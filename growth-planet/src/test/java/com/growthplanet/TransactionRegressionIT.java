package com.growthplanet;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.growthplanet.common.context.UserContext;
import com.growthplanet.dto.request.ChildProfileReq;
import com.growthplanet.dto.request.RevokeConsentReq;
import com.growthplanet.entity.*;
import com.growthplanet.mapper.*;
import com.growthplanet.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionRegressionIT extends BaseIT {
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SessionService sessions;
    @Autowired AuthService auth;
    @Autowired ComplianceService compliance;
    @Autowired FamilyMemberMapper members;
    @Autowired ChildProfileMapper profiles;
    @Autowired NoticeMapper noticeMapper;
    @Autowired AuditLogMapper audits;
    @MockitoSpyBean NoticeService notices;

    @Test
    void noticeFailureRollsBackBindingEventsAndSuccessAudit() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        NoticeService noticeTarget = AopTestUtils.getUltimateTargetObject(notices);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("injected notice failure");
        }).when(noticeTarget).recordBinding(any(FamilyMember.class), anyLong());
        try {
            mockMvc.perform(post("/api/family/bind-approve")
                    .header("Authorization", "Bearer " + ctx.parentToken()).contentType(JSON)
                    .content("{\"applyId\":\"" + ctx.applyId() + "\",\"approve\":true}"))
                    .andExpect(status().isInternalServerError());
        } finally {
            reset(noticeTarget);
        }
        assertEquals("PENDING", members.selectById(ctx.applyId()).getBindStatus());
        assertEquals(0, noticeMapper.selectCount(new QueryWrapper<Notice>()
                .eq("child_id", childUserId(ctx)).eq("event_type", "BIND_BOUND")));
        assertEquals(0, audits.selectCount(new QueryWrapper<AuditLog>()
                .eq("family_id", ctx.familyId()).eq("action", "BIND")));
        approve(ctx);
    }

    @Test
    void concurrentApprovalCreatesOneBusinessEvent() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return mockMvc.perform(post("/api/family/bind-approve")
                            .header("Authorization", "Bearer " + ctx.parentToken()).contentType(JSON)
                            .content("{\"applyId\":\"" + ctx.applyId() + "\",\"approve\":true}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> future : futures) assertEquals(200, future.get(20, TimeUnit.SECONDS));
        }
        assertEquals(2, noticeMapper.selectCount(new QueryWrapper<Notice>()
                .eq("child_id", childUserId(ctx)).eq("event_type", "BIND_BOUND")));
        assertEquals(1, audits.selectCount(new QueryWrapper<AuditLog>()
                .eq("family_id", ctx.familyId()).eq("action", "BIND")));
    }

    @Test
    void revokeCommitBeforeWaitingWriterBlocksWrite() throws Exception {
        assertLockOrdering(true);
    }

    @Test
    void writerCommitBeforeRevokePreservesHistoryButBlocksNextWrite() throws Exception {
        assertLockOrdering(false);
    }

    private void assertLockOrdering(boolean revokeFirst) throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        var firstHasLock = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> {
                UserContext.set(sessions.authenticate(ctx.parentToken()));
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                        if (revokeFirst) {
                            compliance.revokeConsent(revokeRequest(ctx));
                        } else {
                            auth.saveChildProfile(objectMapper.readValue(profileJson(ctx), ChildProfileReq.class));
                        }
                        firstHasLock.countDown();
                        try {
                            if (!releaseFirst.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test lock timeout");
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("interrupted", ex);
                        }
                    });
                } finally {
                    UserContext.clear();
                }
            });
            try {
                assertTrue(firstHasLock.await(10, TimeUnit.SECONDS));
                Future<Integer> second = pool.submit(() -> {
                    secondStarted.countDown();
                    String path = revokeFirst ? "/api/child/profile" : "/api/compliance/consent/revoke";
                    String body = revokeFirst ? profileJson(ctx) : objectMapper.writeValueAsString(revokeRequest(ctx));
                    return mockMvc.perform(post(path).header("Authorization", "Bearer " + ctx.parentToken())
                            .contentType(JSON).content(body)).andReturn().getResponse().getStatus();
                });
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
                releaseFirst.countDown();
                first.get(15, TimeUnit.SECONDS);
                assertEquals(revokeFirst ? 409 : 200, second.get(15, TimeUnit.SECONDS));
            } finally {
                releaseFirst.countDown();
            }
        }
        assertEquals(revokeFirst ? 0 : 1, profiles.selectCount(
                new QueryWrapper<ChildProfile>().eq("user_id", childUserId(ctx))));
        mockMvc.perform(post("/api/child/profile").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(profileJson(ctx))).andExpect(status().isConflict());
    }

    private RevokeConsentReq revokeRequest(FamilyContext ctx) {
        var request = new RevokeConsentReq();
        request.setChildId(childUserId(ctx));
        request.setConsentType("PROFILE");
        request.setVersion("v1");
        return request;
    }
}
