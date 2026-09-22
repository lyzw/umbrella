package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.dto.request.ConfirmApproveReq;
import cn.studykid.growthplanet.dto.request.RevokeConsentReq;
import cn.studykid.growthplanet.entity.*;
import cn.studykid.growthplanet.mapper.*;
import cn.studykid.growthplanet.service.BusinessTime;
import cn.studykid.growthplanet.service.ComplianceService;
import cn.studykid.growthplanet.service.ConfirmService;
import cn.studykid.growthplanet.service.NoticeService;
import cn.studykid.growthplanet.service.SessionService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConfirmationFlowIT extends BaseIT {
    @Autowired DishCategoryMapper categories;
    @Autowired DishMapper dishes;
    @Autowired MenuDailyMapper menus;
    @Autowired WalletMapper wallets;
    @Autowired AllowanceRuleMapper rules;
    @Autowired MenuConfirmMapper confirms;
    @MockitoSpyBean AllowanceLogMapper logs;
    @MockitoSpyBean ConfirmApprovalMapper approvals;
    @Autowired NoticeMapper noticeRows;
    @Autowired ConfirmService confirmService;
    @Autowired ComplianceService compliance;
    @Autowired SessionService sessions;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SqlSessionTemplate sqlSession;
    @MockitoSpyBean BusinessTime time;
    @MockitoSpyBean NoticeService notices;

    @Test
    void submitSnapshotsPriceAndApproveDebitsExactlyOnce() throws Exception {
        Fixture fixture = ready("18.00");
        JsonNode submitted = submit(fixture, "submit", 200);
        long id = submitted.path("confirmId").asLong();
        assertEquals("PENDING", submitted.path("status").asText());
        assertEquals("18.00", submitted.path("totalAmount").asText());
        JsonNode status = request("GET", "/api/menu/confirm/status?confirmId=" + id,
                fixture.ctx().childToken(), null, null, 200);
        assertEquals(3, status.size());
        assertFalse(status.has("items"));
        assertBalance(fixture, "50.00");
        assertEquals(submitted, submit(fixture, "submit", 200));
        Dish dish = dishes.selectById(fixture.dishId());
        dish.setVirtualPrice(new BigDecimal("22.00"));
        dishes.updateById(dish);
        JsonNode completed = approveConfirm(fixture, id, Map.of("expectedVersion", 0), 200);
        assertEquals("COMPLETED", completed.path("status").asText());
        assertEquals("32.00", completed.path("balance").asText());
        assertEquals("18.00", completed.path("items").get(0).path("unitPrice").asText());
        assertBalance(fixture, "32.00");
        assertEquals(completed, approveConfirm(fixture, id, Map.of("expectedVersion", 0), 200));
        approveConfirm(fixture, id, Map.of("expectedVersion", 1), 409);
        assertEquals(1, debitCount(id));
        assertEquals(1L, approvals.selectCount(new QueryWrapper<ConfirmApproval>().eq("confirm_id", id)));
    }

    @Test
    void oneHundredConcurrentApprovalsReturnSameOutcomeAndSingleDebit() throws Exception {
        Fixture fixture = ready("18.00");
        long id = submit(fixture, "race", 200).path("confirmId").asLong();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var results = new ArrayList<Future<JsonNode>>();
            for (int index = 0; index < 100; index++) {
                results.add(pool.submit(() -> approveConfirm(fixture, id, Map.of("expectedVersion", 0), 200)));
            }
            JsonNode first = results.get(0).get(60, TimeUnit.SECONDS);
            for (Future<JsonNode> result : results) {
                assertEquals(first, result.get(60, TimeUnit.SECONDS));
            }
        }
        assertBalance(fixture, "32.00");
        assertEquals(1, debitCount(id));
        assertEquals(1L, approvals.selectCount(new QueryWrapper<ConfirmApproval>().eq("confirm_id", id)));
        assertEquals(2L, noticeRows.selectCount(new QueryWrapper<Notice>()
                .eq("event_key", "confirm:" + id + ":1")));
    }

    @Test
    void overLimitRequiresFreshPreviewAndCurrentWalletRuleVersions() throws Exception {
        Fixture fixture = ready("18.00");
        approveConfirm(fixture, submit(fixture, "first", 200).path("confirmId").asLong(),
                Map.of("expectedVersion", 0), 200);
        Dish dish = dishes.selectById(fixture.dishId());
        dish.setVirtualPrice(new BigDecimal("20.00"));
        dishes.updateById(dish);
        long second = submit(fixture, "second", 200).path("confirmId").asLong();
        JsonNode preview = approveConfirm(fixture, second, Map.of("expectedVersion", 0), 409);
        assertEquals("18.00", preview.path("dailyUsed").asText());
        assertBalance(fixture, "32.00");
        grantMoney(fixture.ctx(), "1.00", "changed-wallet");
        Map<String, Object> stale = explicit(preview);
        JsonNode refreshed = approveConfirm(fixture, second, stale, 409);
        assertNotEquals(preview.path("walletVersion"), refreshed.path("walletVersion"));
        JsonNode rule = request("GET", "/api/wallet/allowance-rule?childId=" + childUserId(fixture.ctx()),
                fixture.ctx().parentToken(), null, null, 200);
        request("PUT", "/api/wallet/allowance-rule", fixture.ctx().parentToken(),
                Map.of("childId", childUserId(fixture.ctx()).toString(), "singleLimit", "10.00",
                        "dailyLimit", "10.00", "weeklyLimit", "50.00",
                        "expectedVersion", rule.path("version").asInt()), null, 200);
        JsonNode changedRule = approveConfirm(fixture, second, explicit(refreshed), 409);
        assertNotEquals(refreshed.path("ruleVersion"), changedRule.path("ruleVersion"));
        approveConfirm(fixture, second, explicit(changedRule), 200);
        assertBalance(fixture, "13.00");
        assertEquals(1, debitCount(second));
    }

    @Test
    void modifyPreservesOriginalLinesAndResubmissionGetsNewIdentity() throws Exception {
        Fixture fixture = ready("18.00");
        long first = submit(fixture, "original", 200).path("confirmId").asLong();
        JsonNode modified = request("POST", "/api/parent/approve/" + first + "/modify",
                fixture.ctx().parentToken(), Map.of("expectedVersion", 0, "reason", "Synthetic suggestion",
                        "items", List.of(Map.of("dishRef", refBody(fixture.dishId()), "quantity", 2))), null, 200);
        assertEquals("REJECTED", modified.path("status").asText());
        assertEquals(1, modified.path("items").get(0).path("quantity").asInt());
        assertEquals(2, modified.path("suggestedItems").get(0).path("quantity").asInt());
        assertEquals("Synthetic suggestion", modified.path("childVisibleNote").asText());
        JsonNode detail = request("GET", "/api/menu/confirm/" + first, fixture.ctx().childToken(),
                null, null, 200);
        assertEquals("Synthetic suggestion", detail.path("childVisibleNote").asText());
        Map<String, Object> body = submitBody(fixture);
        body.put("previousConfirmId", Long.toString(first));
        long second = request("POST", "/api/menu/confirm", fixture.ctx().childToken(), body, "resubmit", 200)
                .path("confirmId").asLong();
        assertNotEquals(first, second);
        JsonNode withdrawn = request("POST", "/api/menu/confirm/" + second + "/withdraw",
                fixture.ctx().childToken(), Map.of("expectedVersion", 0), null, 200);
        assertEquals("CANCELLED", withdrawn.path("status").asText());
        approveConfirm(fixture, second, Map.of("expectedVersion", 0), 409);
        assertBalance(fixture, "50.00");
        assertEquals(0, debitCount(second));
    }

    @Test
    void rejectionExplanationCanBeReadByBothFamilyMembers() throws Exception {
        Fixture fixture = ready("18.00");
        long id = submit(fixture, "reject-reason", 200).path("confirmId").asLong();
        String reason = "Could we choose something else today?";
        request("POST", "/api/parent/approve/" + id + "/reject", fixture.ctx().parentToken(),
                Map.of("expectedVersion", 0, "reason", reason), null, 200);
        for (String token : List.of(fixture.ctx().parentToken(), fixture.ctx().childToken())) {
            JsonNode detail = request("GET", "/api/menu/confirm/" + id, token, null, null, 200);
            assertEquals(reason, detail.path("childVisibleNote").asText());
            assertEquals("REJECTED", detail.path("status").asText());
        }
        assertBalance(fixture, "50.00");
        assertEquals(0, debitCount(id));
    }

    @Test
    void approvalCrossingMidnightOrWeekBoundaryRollsBackWithoutMovingUsage() throws Exception {
        for (LocalDate date : List.of(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 20))) {
            doReturn(date).when(time).today();
            try {
                Fixture fixture = ready("18.00");
                long id = submit(fixture, "midnight", 200).path("confirmId").asLong();
                doReturn(date, date.plusDays(1)).when(time).today();
                approveConfirm(fixture, id, Map.of("expectedVersion", 0), 409);
                assertBalance(fixture, "50.00");
                assertEquals("PENDING", confirms.selectById(id).getStatus());
                assertEquals(0, debitCount(id));
                AllowanceRule rule = rules.selectOne(new QueryWrapper<AllowanceRule>()
                        .eq("child_id", childUserId(fixture.ctx())));
                assertEquals(date, rule.getDailyPeriod());
                assertEquals(0, BigDecimal.ZERO.compareTo(rule.getDailyUsed()));
                assertEquals(0, BigDecimal.ZERO.compareTo(rule.getWeeklyUsed()));
                approveConfirm(fixture, id, Map.of("expectedVersion", 0), 409);
                doReturn(date).when(time).today();
                approveConfirm(fixture, id, Map.of("expectedVersion", 0), 200);
                AllowanceLog debit = logs.selectOne(new QueryWrapper<AllowanceLog>()
                        .eq("ref_id", id).eq("trans_type", "DEDUCT"));
                assertEquals(date, debit.getUsageDate());
            } finally {
                reset(time);
            }
        }
    }

    @Test
    void invalidRequestsCrossFamilyAndRevokeFailWithoutDebit() throws Exception {
        Fixture fixture = ready("18.00");
        long id = submit(fixture, "scope", 200).path("confirmId").asLong();
        var other = setupFamily();
        request("GET", "/api/menu/confirm/" + id, other.parentToken(), null, null, 404);
        request("POST", "/api/parent/approve/" + id + "/approve", fixture.ctx().childToken(),
                Map.of("expectedVersion", 0), null, 403);
        Map<String, Object> body = submitBody(fixture);
        body.put("remark", "changed");
        request("POST", "/api/menu/confirm", fixture.ctx().childToken(), body, "scope", 409);
        for (int quantity : List.of(0, 10)) {
            body.put("items", List.of(Map.of("dishRef", refBody(fixture.dishId()), "quantity", quantity)));
            request("POST", "/api/menu/confirm", fixture.ctx().childToken(), body, "invalid", 400);
        }
        body = submitBody(fixture);
        body.put("totalAmount", "0.01");
        request("POST", "/api/menu/confirm", fixture.ctx().childToken(), body, "injection", 400);
        revoke(fixture.ctx());
        approveConfirm(fixture, id, Map.of("expectedVersion", 0), 409);
        request("GET", "/api/menu/confirm/" + id, fixture.ctx().childToken(), null, null, 409);
        assertBalance(fixture, "50.00");
        assertEquals("PENDING", confirms.selectById(id).getStatus());
    }

    @Test
    void unsafeOrExpiredMenuAndInsufficientBalanceCannotComplete() throws Exception {
        Fixture fixture = ready("18.00");
        long id = submit(fixture, "stale", 200).path("confirmId").asLong();
        Dish dish = dishes.selectById(fixture.dishId());
        dish.setAllergens(List.of("PEANUT"));
        dishes.updateById(dish);
        approveConfirm(fixture, id, Map.of("expectedVersion", 0), 409);
        dish.setAllergens(List.of());
        dish.setStatus("OFF_SALE");
        dishes.updateById(dish);
        approveConfirm(fixture, id, Map.of("expectedVersion", 0), 409);
        dish.setStatus("ON_SALE");
        dishes.updateById(dish);
        var tomorrow = time.today().plusDays(1);
        doReturn(tomorrow).when(time).today();
        try {
            approveConfirm(fixture, id, Map.of("expectedVersion", 0), 409);
        } finally {
            reset(time);
        }
        Wallet wallet = wallet(fixture);
        wallet.setBalance(new BigDecimal("1.00"));
        wallets.updateById(wallet);
        approveConfirm(fixture, id, Map.of("expectedVersion", 0), 409);
        assertBalance(fixture, "1.00");
        assertEquals("PENDING", confirms.selectById(id).getStatus());
        assertEquals(0, debitCount(id));
    }

    @Test
    void noticeFailureRollsBackDebitLedgerStatusApprovalAndUsage() throws Exception {
        Fixture fixture = ready("18.00");
        long id = submit(fixture, "rollback", 200).path("confirmId").asLong();
        NoticeService target = AopTestUtils.getUltimateTargetObject(notices);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Injected post-notice failure");
        }).when(target).recordEvent(eq("confirm:" + id + ":1"), eq("CONFIRM_COMPLETED"),
                anyLong(), anyLong(), anyLong());
        try {
            approveConfirm(fixture, id, Map.of("expectedVersion", 0), 500);
        } finally {
            reset(target);
        }
        assertBalance(fixture, "50.00");
        assertEquals("PENDING", confirms.selectById(id).getStatus());
        assertEquals(0, confirms.selectById(id).getVersion());
        assertEquals(0, debitCount(id));
        assertEquals(0L, approvals.selectCount(new QueryWrapper<ConfirmApproval>().eq("confirm_id", id)));
        assertEquals(0L, noticeRows.selectCount(new QueryWrapper<Notice>().eq("event_key", "confirm:" + id + ":1")));
        AllowanceRule rule = rules.selectOne(new QueryWrapper<AllowanceRule>().eq("child_id", childUserId(fixture.ctx())));
        assertEquals(0, BigDecimal.ZERO.compareTo(rule.getDailyUsed()));
        approveConfirm(fixture, id, Map.of("expectedVersion", 0), 200);
        assertBalance(fixture, "32.00");
    }

    @Test
    void ledgerAndApprovalInsertFailuresRollBackEarlierChanges() throws Exception {
        for (boolean failLedger : List.of(true, false)) {
            Fixture fixture = ready("18.00");
            long id = submit(fixture, "insert-rollback", 200).path("confirmId").asLong();
            AtomicBoolean inserted = new AtomicBoolean();
            if (failLedger) {
                doAnswer(invocation -> {
                    AllowanceLog log = invocation.getArgument(0);
                    assertEquals(1, sqlSession.insert(AllowanceLogMapper.class.getName() + ".insert", log));
                    assertEquals(1, debitCount(id));
                    inserted.set(true);
                    throw new IllegalStateException("Injected post-ledger failure");
                }).when(logs).insert(any(AllowanceLog.class));
            } else {
                doAnswer(invocation -> {
                    ConfirmApproval approval = invocation.getArgument(0);
                    assertEquals(1, sqlSession.insert(ConfirmApprovalMapper.class.getName() + ".insert", approval));
                    assertEquals(1L, approvals.selectCount(new QueryWrapper<ConfirmApproval>().eq("confirm_id", id)));
                    inserted.set(true);
                    throw new IllegalStateException("Injected post-approval failure");
                }).when(approvals).insert(any(ConfirmApproval.class));
            }
            try {
                approveConfirm(fixture, id, Map.of("expectedVersion", 0), 500);
            } finally {
                reset(logs, approvals);
            }
            assertTrue(inserted.get(), "Failure must occur after the actual SQL insert");
            assertBalance(fixture, "50.00");
            assertEquals("PENDING", confirms.selectById(id).getStatus());
            assertEquals(0, confirms.selectById(id).getVersion());
            assertEquals(0, debitCount(id));
            assertEquals(0L, approvals.selectCount(new QueryWrapper<ConfirmApproval>().eq("confirm_id", id)));
            approveConfirm(fixture, id, Map.of("expectedVersion", 0), 200);
            assertBalance(fixture, "32.00");
        }
    }

    @Test
    void approveAndWithdrawRaceProducesOnlyOneTerminalState() throws Exception {
        Fixture fixture = ready("18.00");
        long id = submit(fixture, "withdraw-race", 200).path("confirmId").asLong();
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> approval = pool.submit(() -> mockMvc.perform(post("/api/parent/approve/" + id + "/approve")
                    .header("Authorization", "Bearer " + fixture.ctx().parentToken())
                    .contentType(JSON).content("{\"expectedVersion\":0}")).andReturn().getResponse().getStatus());
            Future<Integer> withdrawal = pool.submit(() -> mockMvc.perform(post("/api/menu/confirm/" + id + "/withdraw")
                    .header("Authorization", "Bearer " + fixture.ctx().childToken())
                    .contentType(JSON).content("{\"expectedVersion\":0}")).andReturn().getResponse().getStatus());
            List<Integer> outcomes = List.of(approval.get(30, TimeUnit.SECONDS), withdrawal.get(30, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(code -> code == 200).count());
            assertEquals(1, outcomes.stream().filter(code -> code == 409).count());
        }
        boolean completed = "COMPLETED".equals(confirms.selectById(id).getStatus());
        assertBalance(fixture, completed ? "32.00" : "50.00");
        assertEquals(completed ? 1 : 0, debitCount(id));
    }

    @Test
    void revokeCommittedBeforeApprovalBlocksDebit() throws Exception {
        assertRevokeOrdering(true);
    }

    @Test
    void approvalCommittedBeforeRevokePreservesSingleDebit() throws Exception {
        assertRevokeOrdering(false);
    }

    private void assertRevokeOrdering(boolean revokeFirst) throws Exception {
        Fixture fixture = ready("18.00");
        long id = submit(fixture, "revoke-race", 200).path("confirmId").asLong();
        CountDownLatch changed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch waiting = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> {
                UserContext.set(sessions.authenticate(fixture.ctx().parentToken()));
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                        if (revokeFirst) {
                            RevokeConsentReq req = new RevokeConsentReq();
                            req.setChildId(childUserId(fixture.ctx()));
                            req.setConsentType("PROFILE");
                            req.setVersion("v1");
                            compliance.revokeConsent(req);
                        } else {
                            ConfirmApproveReq req = new ConfirmApproveReq();
                            req.setExpectedVersion(0);
                            confirmService.approve(id, req);
                        }
                        changed.countDown();
                        await(release);
                    });
                } finally {
                    UserContext.clear();
                }
            });
            await(changed);
            Future<?> second = pool.submit(() -> {
                waiting.countDown();
                if (revokeFirst) {
                    approveConfirm(fixture, id, Map.of("expectedVersion", 0), 409);
                } else {
                    revoke(fixture.ctx());
                }
                return null;
            });
            try {
                await(waiting);
                assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertBalance(fixture, revokeFirst ? "50.00" : "32.00");
        assertEquals(revokeFirst ? 0 : 1, debitCount(id));
        assertEquals(revokeFirst ? "PENDING" : "COMPLETED", confirms.selectById(id).getStatus());
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out waiting for transaction");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private Map<String, Object> refBody(long id) {
        return Map.of("type", "PRESET", "id", id);
    }

    private DishRef dishRef(Long id) {
        DishRef ref = new DishRef();
        ref.setType("PRESET");
        ref.setId(id);
        return ref;
    }

    private Fixture ready(String price) throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        request("POST", "/api/child/profile", ctx.parentToken(),
                objectMapper.readTree(profileJson(ctx)), null, 200);
        grantMoney(ctx, "50.00", "initial");
        DishCategory category = new DishCategory();
        category.setName("Synthetic");
        categories.insert(category);
        Dish dish = new Dish();
        dish.setCategoryId(category.getId());
        dish.setName("Synthetic rice");
        dish.setVirtualPrice(new BigDecimal(price));
        dish.setAllergens(List.of());
        dish.setAllergenStatus("DECLARED");
        dish.setSpiceLevel(0);
        dishes.insert(dish);
        MenuDaily menu = new MenuDaily();
        menu.setFamilyId(ctx.familyId());
        menu.setSourceType("FAMILY");
        menu.setOwnerKey(ctx.familyId().toString());
        menu.setMenuDate(time.today());
        menu.setMealType("LUNCH");
        menu.setDishIds(List.of(dishRef(dish.getId())));
        menus.insert(menu);
        return new Fixture(ctx, menu.getId(), dish.getId());
    }

    private void grantMoney(FamilyContext ctx, String amount, String key) throws Exception {
        request("POST", "/api/wallet/grant", ctx.parentToken(),
                Map.of("childId", childUserId(ctx).toString(), "amount", amount, "reason", "Synthetic grant"), key, 200);
    }

    private Map<String, Object> submitBody(Fixture fixture) {
        return new LinkedHashMap<>(Map.of("menuId", fixture.menuId().toString(),
                "items", List.of(Map.of("dishRef", refBody(fixture.dishId()), "quantity", 1))));
    }

    private Map<String, Object> explicit(JsonNode preview) {
        return Map.of("expectedVersion", preview.path("confirmVersion").asInt(), "explicitConfirm", true,
                "confirmVersion", preview.path("confirmVersion").asInt(),
                "walletVersion", preview.path("walletVersion").asInt(),
                "ruleVersion", preview.path("ruleVersion").asInt(), "usageDate", preview.path("usageDate").asText());
    }

    private JsonNode submit(Fixture fixture, String key, int code) throws Exception {
        return request("POST", "/api/menu/confirm", fixture.ctx().childToken(), submitBody(fixture), key, code);
    }

    private JsonNode approveConfirm(Fixture fixture, long id, Object body, int code) throws Exception {
        return request("POST", "/api/parent/approve/" + id + "/approve",
                fixture.ctx().parentToken(), body, null, code);
    }

    private JsonNode request(String method, String path, String token, Object body, String key, int code)
            throws Exception {
        var request = switch (method) {
            case "GET" -> get(path);
            case "PUT" -> put(path);
            default -> post(path);
        };
        request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        if (body != null) request.contentType(JSON).content(objectMapper.writeValueAsString(body));
        var result = mockMvc.perform(request).andExpect(status().is(code)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private Wallet wallet(Fixture fixture) {
        return wallets.selectOne(new QueryWrapper<Wallet>().eq("child_id", childUserId(fixture.ctx())));
    }

    private void assertBalance(Fixture fixture, String balance) {
        assertEquals(new BigDecimal(balance), wallet(fixture).getBalance());
    }

    private long debitCount(long id) {
        return logs.selectCount(new QueryWrapper<AllowanceLog>().eq("ref_id", id).eq("trans_type", "DEDUCT"));
    }

    private record Fixture(FamilyContext ctx, Long menuId, Long dishId) {
    }
}
