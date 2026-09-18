package cn.studykid.growthplanet;

import cn.studykid.growthplanet.entity.*;
import cn.studykid.growthplanet.mapper.*;
import cn.studykid.growthplanet.service.AuditService;
import cn.studykid.growthplanet.service.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WalletFlowIT extends BaseIT {
    @Autowired WalletMapper wallets;
    @Autowired AllowanceRuleMapper rules;
    @Autowired AllowanceLogMapper logs;
    @Autowired BusinessTime time;
    @MockitoSpyBean AuditService audit;

    @Test
    void zeroBalanceGrantAndCanonicalRetryDoNotDoubleCredit() throws Exception {
        var ctx = ready();
        assertEquals("0.00", balance(ctx).get("balance").asText());
        String body = grantJson(ctx, "50.00");
        JsonNode first = grantMoney(ctx, body, "g1", 200);
        JsonNode retry = grantMoney(ctx, grantJson(ctx, "50"), "g1", 200);
        assertEquals(first, retry);
        grantMoney(ctx, grantJson(ctx, "51.00"), "g1", 409);
        assertEquals("50.00", balance(ctx).get("balance").asText());
        assertEquals(1L, logs.selectCount(new QueryWrapper<AllowanceLog>().eq("child_id", childUserId(ctx))));
        var history = mockMvc.perform(get("/api/wallet/allowance-log")
                .header("Authorization", "Bearer " + ctx.childToken()).param("childId", childUserId(ctx).toString()))
                .andExpect(status().isOk()).andReturn();
        JsonNode item = objectMapper.readTree(history.getResponse().getContentAsString())
                .path("data").path("items").get(0);
        assertEquals("0.00", item.get("balanceBefore").asText());
        assertEquals("50.00", item.get("balanceAfter").asText());
        assertTrue(item.get("logId").isString());
    }

    @Test
    void grantRejectsInvalidMoneyScopeAndRevokedConsent() throws Exception {
        var ctx = ready();
        for (String amount : new String[]{"0", "-1", "1.001", "10000.00"}) {
            grantMoney(ctx, grantJson(ctx, amount), "bad", 400);
        }
        grantMoney(ctx, grantJson(ctx, "1.00"), null, 400);
        String anotherParent = setupFamily().parentToken();
        mockMvc.perform(post("/api/wallet/grant").header("Authorization", "Bearer " + anotherParent)
                .header("Idempotency-Key", "bad-scope").contentType(JSON).content(grantJson(ctx, "1.00")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/wallet/grant").header("Authorization", "Bearer " + ctx.childToken())
                .header("Idempotency-Key", "bad-role").contentType(JSON).content(grantJson(ctx, "1.00")))
                .andExpect(status().isForbidden());
        revoke(ctx);
        grantMoney(ctx, grantJson(ctx, "1.00"), "revoked", 409);
    }

    @Test
    void ruleDefaultsVersionOrderingAndDateValidation() throws Exception {
        var ctx = ready();
        JsonNode rule = rule(ctx);
        assertEquals("30.00", rule.get("singleLimit").asText());
        assertEquals("30.00", rule.get("dailyLimit").asText());
        assertEquals("150.00", rule.get("weeklyLimit").asText());
        String body = "{\"childId\":\"" + childUserId(ctx)
                + "\",\"singleLimit\":\"10.00\",\"dailyLimit\":\"20.00\",\"weeklyLimit\":\"100.00\",\"expectedVersion\":0}";
        mockMvc.perform(put("/api/wallet/allowance-rule").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(body)).andExpect(status().isOk());
        mockMvc.perform(put("/api/wallet/allowance-rule").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(body)).andExpect(status().isConflict());
        mockMvc.perform(put("/api/wallet/allowance-rule").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(body.replace("\"10.00\"", "\"21.00\"")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/wallet/allowance-log").header("Authorization", "Bearer " + ctx.childToken())
                .param("childId", childUserId(ctx).toString()).param("startDate", "2026-01-01")
                .param("endDate", "2026-02-01")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/wallet/allowance-log").header("Authorization", "Bearer " + ctx.childToken())
                .param("childId", childUserId(ctx).toString()).param("pageSize", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void independentDailyAndWeeklyPeriodsReconcileOnlyDebits() throws Exception {
        var ctx = ready();
        rule(ctx);
        AllowanceRule stored = rules.selectOne(new QueryWrapper<AllowanceRule>().eq("child_id", childUserId(ctx)));
        stored.setDailyPeriod(time.today().minusDays(1));
        stored.setDailyUsed(new BigDecimal("70.00"));
        stored.setWeeklyUsed(new BigDecimal("90.00"));
        rules.updateById(stored);
        JsonNode refreshed = rule(ctx);
        assertEquals("0.00", refreshed.get("dailyUsed").asText());
        assertEquals("90.00", refreshed.get("weeklyUsed").asText());
        stored = rules.selectById(stored.getId());
        stored.setWeeklyPeriod(time.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1));
        rules.updateById(stored);
        grantMoney(ctx, grantJson(ctx, "50.00"), "period-grant", 200);
        assertEquals("0.00", rule(ctx).get("weeklyUsed").asText());
    }

    @Test
    void concurrentSameKeyAndFailedAuditAreAtomic() throws Exception {
        var ctx = ready();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 16; i++) {
                results.add(executor.submit(() -> mockMvc.perform(post("/api/wallet/grant")
                        .header("Authorization", "Bearer " + ctx.parentToken()).header("Idempotency-Key", "race")
                        .contentType(JSON).content(grantJson(ctx, "50.00"))).andReturn().getResponse().getStatus()));
            }
            for (Future<Integer> result : results) {
                assertEquals(200, result.get());
            }
        }
        assertEquals("50.00", balance(ctx).get("balance").asText());
        AuditService target = AopTestUtils.getUltimateTargetObject(audit);
        doThrow(new IllegalStateException("injected audit failure")).when(target).record(eq("WALLET_GRANT"),
                anyLong(), anyLong(), eq("CHILD"), anyLong(), isNull(), anyString());
        try {
            grantMoney(ctx, grantJson(ctx, "10.00"), "rollback", 500);
        } finally {
            reset(target);
        }
        assertEquals("50.00", balance(ctx).get("balance").asText());
        assertEquals(1L, logs.selectCount(new QueryWrapper<AllowanceLog>().eq("child_id", childUserId(ctx))));
    }

    @Test
    void overflowAndFrozenWalletCannotBeCredited() throws Exception {
        var ctx = ready();
        balance(ctx);
        Wallet wallet = wallets.selectOne(new QueryWrapper<Wallet>().eq("child_id", childUserId(ctx)));
        wallet.setBalance(new BigDecimal("99999999.99"));
        wallets.updateById(wallet);
        grantMoney(ctx, grantJson(ctx, "0.01"), "overflow", 400);
        wallet.setStatus("FROZEN");
        wallets.updateById(wallet);
        grantMoney(ctx, grantJson(ctx, "1.00"), "frozen", 403);
    }

    private FamilyContext ready() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        return ctx;
    }

    private JsonNode balance(FamilyContext ctx) throws Exception {
        var response = mockMvc.perform(get("/api/wallet/balance").header("Authorization", "Bearer " + ctx.childToken())
                .param("childId", childUserId(ctx).toString())).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString()).get("data");
    }

    private JsonNode rule(FamilyContext ctx) throws Exception {
        var response = mockMvc.perform(get("/api/wallet/allowance-rule")
                .header("Authorization", "Bearer " + ctx.parentToken()).param("childId", childUserId(ctx).toString()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString()).get("data");
    }

    private String grantJson(FamilyContext ctx, String amount) {
        return "{\"childId\":\"" + childUserId(ctx) + "\",\"amount\":\"" + amount + "\",\"reason\":\"合成测试发放\"}";
    }

    private JsonNode grantMoney(FamilyContext ctx, String body, String key, int statusCode) throws Exception {
        var request = post("/api/wallet/grant").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(body);
        if (key != null) request.header("Idempotency-Key", key);
        var response = mockMvc.perform(request).andExpect(status().is(statusCode)).andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString()).get("data");
    }
}
