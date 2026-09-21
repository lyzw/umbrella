package cn.studykid.growthplanet;

import cn.studykid.growthplanet.entity.AllowanceLog;
import cn.studykid.growthplanet.entity.Wallet;
import cn.studykid.growthplanet.mapper.AllowanceLogMapper;
import cn.studykid.growthplanet.mapper.WalletMapper;
import cn.studykid.growthplanet.service.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 零花钱看板（V0.0.2 F-024~F-026）集成测试。
 * <p>
 * 消费流水（DEDUCT）需要完整点餐链路才产生，本测试按 WalletFlowIT 既有范式直接经 mapper 构造流水，
 * 并同步钱包余额，使“余额与流水一致”的聚合口径（看板的核心正确性）可被独立验证。
 */
class WalletBoardIT extends BaseIT {
    @Autowired
    WalletMapper wallets;
    @Autowired
    AllowanceLogMapper logs;
    @Autowired
    BusinessTime time;

    @Test
    void overviewBoardAndStatsReflectWalletActivity() throws Exception {
        var ctx = ready();
        grantMoney(ctx, "100.00", "board-grant");
        insertSpend(ctx, "12.00", "MENU_CONFIRM", 900001L);

        JsonNode overview = data(get("/api/wallet/overview").header("Authorization", "Bearer " + ctx.childToken())
                .param("childId", childUserId(ctx).toString()));
        assertEquals("88.00", overview.get("balance").asText());
        assertEquals("12.00", overview.get("weekUsed").asText());
        assertEquals("138.00", overview.get("weekRemaining").asText());
        assertEquals("12.00", overview.get("monthUsed").asText());
        assertEquals("150.00", overview.get("weeklyLimit").asText());
        assertEquals(8, overview.get("weekProgress").asInt());
        assertEquals("NORMAL", overview.get("weekStatus").asText());

        JsonNode board = data(get("/api/wallet/board").header("Authorization", "Bearer " + ctx.parentToken()));
        assertEquals(ctx.familyId().toString(), board.get("familyId").asText());
        assertEquals("88.00", board.get("totalBalance").asText());
        assertEquals("12.00", board.get("weekSpend").asText());
        assertEquals("100.00", board.get("weekGrant").asText());
        assertEquals(1, board.get("children").size());
        JsonNode child = board.get("children").get(0);
        assertEquals(childUserId(ctx).toString(), child.get("childId").asText());
        assertEquals("88.00", child.get("balance").asText());
        assertEquals("12.00", child.get("weekUsed").asText());

        JsonNode stats = data(get("/api/wallet/stats").header("Authorization", "Bearer " + ctx.childToken())
                .param("childId", childUserId(ctx).toString()).param("range", "WEEK"));
        assertEquals("WEEK", stats.get("range").asText());
        assertEquals(7, stats.get("spendTrend").size());
        assertEquals("12.00", stats.get("totalSpend").asText());
        assertEquals("100.00", stats.get("totalGrant").asText());
        assertEquals("MENU_CONFIRM", stats.get("spendCategories").get(0).get("scene").asText());
        assertEquals(100, stats.get("spendCategories").get(0).get("percent").asInt());
        assertEquals("MANUAL", stats.get("grantCategories").get(0).get("scene").asText());
        assertEquals(100, stats.get("grantCategories").get(0).get("percent").asInt());

        // 本月按周分桶：4~5 个点，且支出合计与周口径一致
        JsonNode month = data(get("/api/wallet/stats").header("Authorization", "Bearer " + ctx.childToken())
                .param("childId", childUserId(ctx).toString()).param("range", "MONTH"));
        assertEquals("MONTH", month.get("range").asText());
        assertTrue(month.get("spendTrend").size() >= 4 && month.get("spendTrend").size() <= 6);
        assertEquals("12.00", month.get("totalSpend").asText());
        mockMvc.perform(get("/api/wallet/stats").header("Authorization", "Bearer " + ctx.childToken())
                .param("childId", childUserId(ctx).toString()).param("range", "YEAR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logFiltersByDirectionAndScene() throws Exception {
        var ctx = ready();
        grantMoney(ctx, "100.00", "filter-grant");
        insertSpend(ctx, "12.00", "MENU_CONFIRM", 900101L);
        insertSpend(ctx, "8.00", "SHOPPING", 900102L);

        assertEquals(2, logPage(ctx, "direction", "EXPENSE").get("total").asInt());
        assertEquals(1, logPage(ctx, "direction", "INCOME").get("total").asInt());
        JsonNode menu = logPage(ctx, "scene", "MENU_CONFIRM");
        assertEquals(1, menu.get("total").asInt());
        assertEquals("MENU_CONFIRM", menu.get("items").get(0).get("scene").asText());
        assertEquals("12.00", menu.get("items").get(0).get("amount").asText());
        // 无筛选时返回全部 3 条，证明扩展参数向后兼容
        assertEquals(3, logPage(ctx, null, null).get("total").asInt());
        mockMvc.perform(get("/api/wallet/allowance-log").header("Authorization", "Bearer " + ctx.childToken())
                .param("childId", childUserId(ctx).toString()).param("direction", "SIDEWAYS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void boardIsIsolatedPerFamilyAndRejectsForeignChild() throws Exception {
        var ctx = ready();
        var other = ready();
        grantMoney(ctx, "20.00", "iso-grant");
        grantMoney(other, "7.00", "iso-grant");

        JsonNode board = data(get("/api/wallet/board").header("Authorization", "Bearer " + ctx.parentToken()));
        assertEquals("20.00", board.get("totalBalance").asText());
        assertEquals(1, board.get("children").size());
        assertEquals(childUserId(ctx).toString(), board.get("children").get(0).get("childId").asText());

        // 跨家庭：家长查他人儿童、儿童查他人自述，均以 E-009 拒绝且不暴露数据存在性
        mockMvc.perform(get("/api/wallet/overview").header("Authorization", "Bearer " + other.parentToken())
                .param("childId", childUserId(ctx).toString())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/wallet/overview").header("Authorization", "Bearer " + ctx.childToken())
                .param("childId", childUserId(other).toString())).andExpect(status().isForbidden());
        // 儿童不可访问家长看板
        mockMvc.perform(get("/api/wallet/board").header("Authorization", "Bearer " + ctx.childToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyFamilyRendersZeroBoardAndEmptyCategories() throws Exception {
        var ctx = ready();

        JsonNode board = data(get("/api/wallet/board").header("Authorization", "Bearer " + ctx.parentToken()));
        assertEquals("0.00", board.get("totalBalance").asText());
        assertEquals("0.00", board.get("weekSpend").asText());
        assertEquals("0.00", board.get("weekGrant").asText());
        assertEquals(1, board.get("children").size());
        assertEquals("0.00", board.get("children").get(0).get("balance").asText());

        // 无流水时分类为空（前端展示空态），趋势仍返回完整 7 个 0 值点以便图表占位
        JsonNode stats = data(get("/api/wallet/stats").header("Authorization", "Bearer " + ctx.childToken())
                .param("childId", childUserId(ctx).toString()));
        assertEquals("WEEK", stats.get("range").asText());
        assertEquals(0, stats.get("spendCategories").size());
        assertEquals(0, stats.get("grantCategories").size());
        assertEquals("0.00", stats.get("totalSpend").asText());
        assertEquals(7, stats.get("spendTrend").size());
        assertEquals("0.00", stats.get("spendTrend").get(0).get("amount").asText());
        assertEquals("周一", stats.get("spendTrend").get(0).get("label").asText());
    }

    private FamilyContext ready() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        return ctx;
    }

    private void grantMoney(FamilyContext ctx, String amount, String key) throws Exception {
        mockMvc.perform(post("/api/wallet/grant").header("Authorization", "Bearer " + ctx.parentToken())
                        .header("Idempotency-Key", key).contentType(JSON)
                        .content("{\"childId\":\"" + childUserId(ctx) + "\",\"amount\":\"" + amount
                                + "\",\"reason\":\"看板测试发放\"}"))
                .andExpect(status().isOk());
    }

    /** 直接构造一条消费流水并同步钱包余额，保持“余额 = 收入 - 支出”的可核对关系。 */
    private void insertSpend(FamilyContext ctx, String amount, String scene, Long refId) {
        Wallet wallet = wallets.selectOne(new QueryWrapper<Wallet>().eq("child_id", childUserId(ctx)));
        BigDecimal value = new BigDecimal(amount);
        AllowanceLog log = new AllowanceLog();
        log.setWalletId(wallet.getId());
        log.setChildId(childUserId(ctx));
        log.setFamilyId(ctx.familyId());
        log.setOperatorId(parentUserId(ctx));
        log.setTransType("DEDUCT");
        log.setScene(scene);
        log.setRefId(refId);
        log.setAmount(value);
        log.setBalanceBefore(wallet.getBalance());
        log.setBalanceAfter(wallet.getBalance().subtract(value));
        log.setWalletVersion(wallet.getVersion());
        log.setUsageDate(time.today());
        log.setCreateTime(LocalDateTime.now());
        logs.insert(log);
        wallet.setBalance(wallet.getBalance().subtract(value));
        wallet.setVersion(wallet.getVersion() + 1);
        wallets.updateById(wallet);
    }

    private JsonNode logPage(FamilyContext ctx, String field, String value) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/wallet/allowance-log")
                .header("Authorization", "Bearer " + ctx.childToken())
                .param("childId", childUserId(ctx).toString());
        if (field != null) {
            request = request.param(field, value);
        }
        return data(request);
    }

    private JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        var response = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString()).get("data");
    }

    private Long parentUserId(FamilyContext ctx) {
        return jwtUtil.getUserId(jwtUtil.parse(ctx.parentToken()));
    }
}
