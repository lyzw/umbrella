package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.dto.response.ChoreInstanceResp;
import cn.studykid.growthplanet.dto.response.MedalDefinitionResp;
import cn.studykid.growthplanet.dto.response.WalletResp;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;

import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 家务闭环集成测试（F-028~F-032 + F-048）：
 * 1) 完整链路：家长建任务 → 儿童认领 → 提交 → 家长确认 → 余额增加、流水 GRANT/CHORE_REWARD、勋章发放、连续记录更新。
 * 2) 驳回路径：确认改为驳回，状态 REJECTED。
 * 3) 并发：重复确认（相同 expectedVersion）返回冲突。
 */
class ChoreFlowIT extends BaseIT {

    private FamilyContext boundFamily() throws Exception {
        FamilyContext ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        return ctx;
    }

    @Test
    void earnToMedalHappyPath() throws Exception {
        FamilyContext ctx = boundFamily();
        Long childId = childUserId(ctx);

        String balanceBefore = balanceOf(ctx, childId);

        Long createdTaskId = createTask(ctx);
        assertTrue(createdTaskId != null && createdTaskId > 0, "应返回任务 ID");

        // 儿童认领
        MvcResult claim = mockMvc.perform(post("/api/chore/claim")
                        .header("Authorization", "Bearer " + ctx.childToken())
                        .contentType(JSON)
                        .content("{\"taskId\":" + createdTaskId + "}"))
                .andExpect(status().isOk()).andReturn();
        ChoreInstanceResp claimResp = dataOf(claim, ChoreInstanceResp.class);
        assertEquals("CLAIMED", claimResp.status());

        // 儿童提交
        MvcResult submit = mockMvc.perform(post("/api/chore/submit")
                        .header("Authorization", "Bearer " + ctx.childToken())
                        .contentType(JSON)
                        .content("{\"instanceId\":" + claimResp.instanceId() + "}"))
                .andExpect(status().isOk()).andReturn();
        ChoreInstanceResp submitResp = dataOf(submit, ChoreInstanceResp.class);
        assertEquals("SUBMITTED", submitResp.status());
        assertEquals(1, submitResp.version());

        // 家长确认（expectedVersion = 提交后的版本 1）
        MvcResult confirm = mockMvc.perform(post("/api/chore/confirm")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{\"id\":" + submitResp.instanceId() + ",\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andReturn();
        ChoreInstanceResp confirmResp = dataOf(confirm, ChoreInstanceResp.class);
        assertEquals("CONFIRMED", confirmResp.status());
        assertEquals(1, confirmResp.rewardGranted() ? 1 : 0);

        // 余额增加 5.00
        String balanceAfter = balanceOf(ctx, childId);
        assertEquals("5.00", decimalSubtract(balanceAfter, balanceBefore));

        // 勋章：CHORE_FIRST（阈值 1）应已获得
        List<ChildMedalView> awards = awardsOf(ctx, childId);
        Optional<ChildMedalView> first = awards.stream().filter(a -> "CHORE_FIRST".equals(a.code())).findFirst();
        assertTrue(first.isPresent() && first.get().earned(), "初次当家勋章应已发放");

        // 连做三日尚未达到，CHORE_STREAK3 不应发放
        Optional<ChildMedalView> streak = awards.stream().filter(a -> "CHORE_STREAK3".equals(a.code())).findFirst();
        assertTrue(streak.isPresent() && !streak.get().earned(), "连做三日勋章不应在首次完成时发放");
    }

    @Test
    void rejectPathSetsRejected() throws Exception {
        FamilyContext ctx = boundFamily();
        Long createdTaskId = createTask(ctx);

        MvcResult claim = mockMvc.perform(post("/api/chore/claim")
                        .header("Authorization", "Bearer " + ctx.childToken())
                        .contentType(JSON)
                        .content("{\"taskId\":" + createdTaskId + "}"))
                .andExpect(status().isOk()).andReturn();
        ChoreInstanceResp claimResp = dataOf(claim, ChoreInstanceResp.class);

        mockMvc.perform(post("/api/chore/submit")
                        .header("Authorization", "Bearer " + ctx.childToken())
                        .contentType(JSON)
                        .content("{\"instanceId\":" + claimResp.instanceId() + "}"))
                .andExpect(status().isOk());

        MvcResult reject = mockMvc.perform(post("/api/chore/reject")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{\"id\":" + claimResp.instanceId() + ",\"expectedVersion\":1,\"reason\":\"未扫干净\"}"))
                .andExpect(status().isOk()).andReturn();
        ChoreInstanceResp rejectResp = dataOf(reject, ChoreInstanceResp.class);
        assertEquals("REJECTED", rejectResp.status());
        assertEquals("未扫干净", rejectResp.rejectReason());
    }

    @Test
    void doubleConfirmReturnsConflict() throws Exception {
        FamilyContext ctx = boundFamily();
        Long createdTaskId = createTask(ctx);

        MvcResult claim = mockMvc.perform(post("/api/chore/claim")
                        .header("Authorization", "Bearer " + ctx.childToken())
                        .contentType(JSON)
                        .content("{\"taskId\":" + createdTaskId + "}"))
                .andExpect(status().isOk()).andReturn();
        ChoreInstanceResp claimResp = dataOf(claim, ChoreInstanceResp.class);

        mockMvc.perform(post("/api/chore/submit")
                        .header("Authorization", "Bearer " + ctx.childToken())
                        .contentType(JSON)
                        .content("{\"instanceId\":" + claimResp.instanceId() + "}"))
                .andExpect(status().isOk());

        // 第一次确认成功
        mockMvc.perform(post("/api/chore/confirm")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{\"id\":" + claimResp.instanceId() + ",\"expectedVersion\":1}"))
                .andExpect(status().isOk());

        // 第二次确认（仍用旧版本）应冲突
        mockMvc.perform(post("/api/chore/confirm")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{\"id\":" + claimResp.instanceId() + ",\"expectedVersion\":1}"))
                .andExpect(status().isConflict());
    }

    /** 家长创建一个一次性家务任务，返回任务编号。 */
    private Long createTask(FamilyContext ctx) throws Exception {
        mockMvc.perform(post("/api/chore/task")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{\"title\":\"扫地\",\"description\":\"客厅扫地\",\"icon\":\"🧹\","
                                + "\"estimatedMinutes\":15,\"rewardAmount\":\"5.00\",\"cycle\":\"ONCE\",\"sortOrder\":1}"))
                .andExpect(status().isOk());
        return listTaskId(ctx);
    }

    private Long listTaskId(FamilyContext ctx) throws Exception {
        MvcResult list = mockMvc.perform(get("/api/chore/tasks")
                        .header("Authorization", "Bearer " + ctx.parentToken()))
                .andExpect(status().isOk()).andReturn();
        JsonNode root = objectMapper.readTree(list.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertTrue(data.isArray() && data.size() > 0, "任务库不应为空");
        return data.get(0).get("taskId").asLong();
    }

    private String balanceOf(FamilyContext ctx, Long childId) throws Exception {
        MvcResult bal = mockMvc.perform(get("/api/wallet/balance")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .param("childId", String.valueOf(childId)))
                .andExpect(status().isOk()).andReturn();
        WalletResp resp = dataOf(bal, WalletResp.class);
        return resp.balance();
    }

    private List<ChildMedalView> awardsOf(FamilyContext ctx, Long childId) throws Exception {
        MvcResult aw = mockMvc.perform(get("/api/medal/awards")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .param("childId", String.valueOf(childId)))
                .andExpect(status().isOk()).andReturn();
        JsonNode root = objectMapper.readTree(aw.getResponse().getContentAsString());
        return objectMapper.convertValue(root.get("data"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ChildMedalView.class));
    }

    /** 金额差（保留两位小数，与后端金额口径一致；不得 stripTrailingZeros，否则 5.00 会变成 5）。 */
    private String decimalSubtract(String a, String b) {
        java.math.BigDecimal ba = new java.math.BigDecimal(a);
        java.math.BigDecimal bb = new java.math.BigDecimal(b);
        return ba.subtract(bb).setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }

    /** 与 ChildMedalResp 对应的只读视图（仅取断言所需字段）。 */
    private record ChildMedalView(MedalDefinitionResp definition, boolean earned, String awardedAt,
                                 int consecutiveCount, int progress, int threshold) {
        String code() {
            return definition == null ? null : definition.code();
        }
    }
}
