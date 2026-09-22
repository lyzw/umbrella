package cn.studykid.growthplanet;

import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.config.NoticeProperties;
import cn.studykid.growthplanet.entity.Notice;
import cn.studykid.growthplanet.entity.NoticeSubscription;
import cn.studykid.growthplanet.mapper.NoticeMapper;
import cn.studykid.growthplanet.mapper.NoticeSubscriptionMapper;
import cn.studykid.growthplanet.service.NoticeDeliveryService;
import cn.studykid.growthplanet.service.NoticeService;
import cn.studykid.growthplanet.service.NoticeTransport;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NoticeCenterFlowIT extends BaseIT {
    @Autowired NoticeMapper notices;
    @Autowired NoticeSubscriptionMapper subscriptions;
    @Autowired NoticeService events;
    @Autowired NoticeDeliveryService delivery;
    @Autowired NoticeProperties settings;
    @Autowired ComplianceProperties compliance;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean NoticeTransport transport;

    @AfterEach
    void resetSettings() {
        settings.setDeliveryEnabled(false);
        settings.setPlatformApprovalReference("");
        settings.setTemplates(Map.of());
    }

    @Test
    void ownInboxPaginationAndReadAreIsolatedAndIdempotent() throws Exception {
        var ctx = boundFamily();
        var other = boundFamily();
        Notice event = event(ctx, "TEST_INBOX");
        Notice inbox = notices.selectOne(new QueryWrapper<Notice>().eq("event_key", event.getEventKey())
                .eq("channel", "IN_APP"));
        mockMvc.perform(get("/api/notices").header("Authorization", bearer(ctx.parentToken()))
                .param("page", "1").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(inbox.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].subscriptionNoticeId").value(event.getId().toString()));
        mockMvc.perform(get("/api/notices").header("Authorization", bearer(ctx.parentToken()))
                .param("pageSize", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/notices/" + inbox.getId() + "/read")
                .header("Authorization", bearer(other.parentToken()))).andExpect(status().isNotFound());
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/notices/" + inbox.getId() + "/read")
                    .header("Authorization", bearer(ctx.parentToken()))).andExpect(status().isOk());
        }
        assertNotNull(notices.selectById(inbox.getId()).getReadAt());
        long unread = notices.selectCount(new QueryWrapper<Notice>().eq("receiver_id", parentId(ctx))
                .eq("channel", "IN_APP").isNull("read_at"));
        mockMvc.perform(get("/api/notices/unread-count").header("Authorization", bearer(ctx.parentToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(unread));
        mockMvc.perform(post("/api/notices/subscription").header("Authorization", bearer(ctx.childToken()))
                .contentType(JSON).content(subscriptionJson(event, true))).andExpect(status().isNotFound());
    }

    @Test
    void eventsRequireTransactionAndBothChannelsRollbackTogether() throws Exception {
        var ctx = boundFamily();
        String key = "atomic:" + UUID.randomUUID();
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> events.recordEvent(key, "TEST_ATOMIC", ctx.familyId(), childUserId(ctx), parentId(ctx)));
        var tx = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            events.recordEvent(key, "TEST_ATOMIC", ctx.familyId(), childUserId(ctx), parentId(ctx));
            throw new IllegalStateException("synthetic rollback");
        }));
        assertEquals(0, notices.selectCount(new QueryWrapper<Notice>().eq("event_key", key)));
        tx.executeWithoutResult(status -> {
            events.recordEvent(key, "TEST_ATOMIC", ctx.familyId(), childUserId(ctx), parentId(ctx));
            events.recordEvent(key, "TEST_ATOMIC", ctx.familyId(), childUserId(ctx), parentId(ctx));
        });
        assertEquals(2, notices.selectCount(new QueryWrapper<Notice>().eq("event_key", key)));
        assertThrows(cn.studykid.growthplanet.common.exception.BizException.class, () ->
                tx.executeWithoutResult(status -> events.recordEvent(key, "CHANGED", ctx.familyId(),
                        childUserId(ctx), parentId(ctx))));
    }

    @Test
    void transportRetriesAreBoundedSanitizedAndScheduledOneFiveThirtyMinutes() throws Exception {
        var ctx = boundFamily();
        Notice event = event(ctx, "TEST_RETRY");
        authorize(ctx, event);
        enable("TEST_RETRY");
        when(transport.send(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("openid=secret external response"));
        long[] delays = {60_000, 300_000, 1_800_000};
        for (int attempt = 1; attempt <= 4; attempt++) {
            long before = System.currentTimeMillis();
            assertEquals(1, delivery.deliverBatch());
            Notice persisted = notices.selectById(event.getId());
            assertEquals(attempt, persisted.getAttemptCount());
            assertEquals("TRANSPORT_FAILURE", persisted.getLastError());
            if (attempt <= 3) {
                assertEquals("PENDING", persisted.getStatus());
                assertTrue(persisted.getNextRetryAt() >= before + delays[attempt - 1]);
                assertTrue(persisted.getNextRetryAt() <= System.currentTimeMillis() + delays[attempt - 1]);
                assertEquals(0, delivery.deliverBatch());
                dueNow(event);
            } else {
                assertEquals("FAILED", persisted.getStatus());
                assertNull(persisted.getNextRetryAt());
            }
        }
        assertEquals(0, delivery.deliverBatch());
        verify(transport, times(4)).send(anyString(), anyString(), anyString(), anyString());
        mockMvc.perform(post("/api/notices/subscription").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(subscriptionJson(event, true))).andExpect(status().isConflict());
    }

    @Test
    void revokedConsentAndClosedCollectionGateCancelWithoutTransport() throws Exception {
        var ctx = boundFamily();
        Notice first = event(ctx, "TEST_REVOKE");
        authorize(ctx, first);
        enable("TEST_REVOKE");
        revoke(ctx);
        assertEquals("UNAUTHORIZED", notices.selectById(first.getId()).getStatus());
        assertEquals(0, delivery.deliverBatch());
        grant(ctx);
        Notice second = event(ctx, "TEST_REVOKE");
        authorize(ctx, second);
        compliance.setCollectionEnabled(false);
        try {
            assertEquals(1, delivery.deliverBatch());
            assertEquals("UNAUTHORIZED", notices.selectById(second.getId()).getStatus());
            assertEquals(0, notices.selectById(second.getId()).getAttemptCount());
        } finally {
            compliance.setCollectionEnabled(true);
        }
        verify(transport, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void disabledOrUnconfiguredTransportCannotPretendSent() throws Exception {
        var ctx = boundFamily();
        Notice event = event(ctx, "TEST_GATE");
        authorize(ctx, event);
        assertEquals(0, delivery.deliverBatch());
        enable("TEST_GATE");
        when(transport.isConfigured()).thenReturn(false);
        assertEquals(0, delivery.deliverBatch());
        assertEquals("PENDING", notices.selectById(event.getId()).getStatus());
        assertEquals(0, notices.selectById(event.getId()).getAttemptCount());
        verify(transport, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void simultaneousWorkersSendOneCommittedEventOnce() throws Exception {
        var ctx = boundFamily();
        Notice event = event(ctx, "TEST_CONCURRENT");
        authorize(ctx, event);
        enable("TEST_CONCURRENT");
        when(transport.send(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(NoticeTransport.Outcome.ACCEPTED);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(delivery::deliverBatch);
            var second = executor.submit(delivery::deliverBatch);
            assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
        }
        assertEquals("SENT", notices.selectById(event.getId()).getStatus());
        verify(transport, times(1)).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void reauthorizationCannotResetRetryBudgetOrAdvanceBackoff() throws Exception {
        var ctx = boundFamily();
        Notice event = event(ctx, "TEST_REAUTH");
        authorize(ctx, event);
        enable("TEST_REAUTH");
        when(transport.send(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(NoticeTransport.Outcome.RETRYABLE_FAILURE);
        delivery.deliverBatch();
        Long originalRetry = notices.selectById(event.getId()).getNextRetryAt();
        mockMvc.perform(post("/api/notices/subscription").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(subscriptionJson(event, false))).andExpect(status().isOk());
        authorize(ctx, event);
        Notice after = notices.selectById(event.getId());
        assertEquals(1, after.getAttemptCount());
        assertEquals(originalRetry, after.getNextRetryAt());
        assertEquals(0, delivery.deliverBatch());
        verify(transport, times(1)).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void expiredAuthorizationCancelsAndExplicitPlatformRejectionIsTerminal() throws Exception {
        var ctx = boundFamily();
        Notice expired = event(ctx, "TEST_EXPIRY");
        authorize(ctx, expired);
        enable("TEST_EXPIRY");
        subscriptions.update(null, new UpdateWrapper<NoticeSubscription>().eq("notice_id", expired.getId())
                .set("expires_at", 1L));
        assertEquals(1, delivery.deliverBatch());
        assertEquals("UNAUTHORIZED", notices.selectById(expired.getId()).getStatus());
        verify(transport, never()).send(anyString(), anyString(), anyString(), anyString());
        Notice rejected = event(ctx, "TEST_EXPIRY");
        authorize(ctx, rejected);
        when(transport.send(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(NoticeTransport.Outcome.REJECTED);
        assertEquals(1, delivery.deliverBatch());
        Notice failure = notices.selectById(rejected.getId());
        assertEquals("FAILED", failure.getStatus());
        assertEquals("PLATFORM_REJECTED", failure.getLastError());
        assertEquals(1, failure.getAttemptCount());
        assertNull(failure.getNextRetryAt());
    }

    @Test
    void sendHoldingScopeLockCompletesBeforeRacingRevokeAndNoLaterSendOccurs() throws Exception {
        var ctx = boundFamily();
        Notice event = event(ctx, "TEST_REVOKE_RACE");
        authorize(ctx, event);
        enable("TEST_REVOKE_RACE");
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch revoking = new CountDownLatch(1);
        when(transport.send(anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            sending.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("synthetic timeout");
            return NoticeTransport.Outcome.ACCEPTED;
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var send = executor.submit(delivery::deliverBatch);
            try {
                assertTrue(sending.await(5, TimeUnit.SECONDS));
                var revoke = executor.submit(() -> {
                    revoking.countDown();
                    revoke(ctx);
                    return true;
                });
                assertTrue(revoking.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> revoke.get(150, TimeUnit.MILLISECONDS));
                release.countDown();
                assertEquals(1, send.get(5, TimeUnit.SECONDS));
                assertTrue(revoke.get(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
        assertEquals("SENT", notices.selectById(event.getId()).getStatus());
        assertEquals(0, delivery.deliverBatch());
        verify(transport, times(1)).send(anyString(), anyString(), anyString(), anyString());
    }

    private FamilyContext boundFamily() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        return ctx;
    }

    private Notice event(FamilyContext ctx, String type) {
        String key = "test:" + UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                events.recordEvent(key, type, ctx.familyId(), childUserId(ctx), parentId(ctx)));
        return notices.selectOne(new QueryWrapper<Notice>().eq("event_key", key).eq("channel", "SUBSCRIBE"));
    }

    private void authorize(FamilyContext ctx, Notice event) throws Exception {
        mockMvc.perform(post("/api/notices/subscription").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(subscriptionJson(event, true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    private void enable(String type) {
        settings.setDeliveryEnabled(true);
        settings.setPlatformApprovalReference("synthetic-test");
        settings.setTemplates(Map.of(type, "synthetic-template"));
        when(transport.isConfigured()).thenReturn(true);
    }

    private void dueNow(Notice notice) {
        notices.update(null, new UpdateWrapper<Notice>().eq("id", notice.getId()).set("next_retry_at", 1L));
    }

    private Long parentId(FamilyContext ctx) {
        return jwtUtil.getUserId(jwtUtil.parse(ctx.parentToken()));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String subscriptionJson(Notice notice, boolean accepted) {
        return "{\"noticeId\":\"" + notice.getId() + "\",\"accepted\":" + accepted + "}";
    }
}
