package com.growthplanet;

import tools.jackson.databind.JsonNode;
import com.growthplanet.common.enums.RoleEnum;
import com.growthplanet.dto.response.ConsentResp;
import com.growthplanet.dto.response.DataExportResp;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 合规链路集成测试：同意书查询/提交/撤回降级 + 数据导出 + 越权/年龄校验。
 */
class ComplianceFlowIT extends BaseIT {

    private Long childUserId(FamilyContext ctx) {
        return jwtUtil.getUserId(jwtUtil.parse(ctx.childToken()));
    }

    @Test
    void submitConsentAndQuery() throws Exception {
        FamilyContext ctx = setupFamily();
        // 提交（年龄>=18 且同意）
        MvcResult submit = mockMvc.perform(post("/api/compliance/consent")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{\"version\":\"v1\",\"selfReportedAge\":20,\"agreed\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        ConsentResp sr = dataOf(submit, ConsentResp.class);
        assertEquals("APPROVED", sr.getGuardianStatus());

        // 查询当前状态
        MvcResult query = mockMvc.perform(get("/api/compliance/consent")
                        .header("Authorization", "Bearer " + ctx.parentToken()))
                .andExpect(status().isOk())
                .andReturn();
        ConsentResp qr = dataOf(query, ConsentResp.class);
        assertEquals("APPROVED", qr.getCurrentStatus());
    }

    @Test
    void submitConsentUnder18Rejected_400() throws Exception {
        FamilyContext ctx = setupFamily();
        mockMvc.perform(post("/api/compliance/consent")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{\"version\":\"v1\",\"selfReportedAge\":10,\"agreed\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revokeReturns409WithRevokedStatus() throws Exception {
        FamilyContext ctx = setupFamily();
        // 先同意
        mockMvc.perform(post("/api/compliance/consent")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{\"version\":\"v1\",\"selfReportedAge\":25,\"agreed\":true}"))
                .andExpect(status().isOk());

        // 撤回 -> 409 + data.status=REVOKED
        MvcResult revoke = mockMvc.perform(post("/api/compliance/consent/revoke")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{\"consentType\":\"ORDER\",\"childId\":" + childUserId(ctx) + "}"))
                .andExpect(status().isConflict())
                .andReturn();
        JsonNode root = objectMapper.readTree(revoke.getResponse().getContentAsString());
        assertEquals(1010, root.get("code").asInt());
        assertEquals("REVOKED", root.get("data").get("status").asText());
    }

    @Test
    void blacklistBlocksTokenAfterRevoke_401() throws Exception {
        FamilyContext ctx = setupFamily();
        mockMvc.perform(post("/api/compliance/consent")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{\"version\":\"v1\",\"selfReportedAge\":25,\"agreed\":true}"))
                .andExpect(status().isOk());
        // 撤回（将 parentToken 的 jti 拉黑）
        mockMvc.perform(post("/api/compliance/consent/revoke")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{\"consentType\":\"ORDER\",\"childId\":" + childUserId(ctx) + "}"))
                .andExpect(status().isConflict());

        // 同一 token 再次访问受保护接口 -> 命中黑名单 401
        mockMvc.perform(get("/api/compliance/consent")
                        .header("Authorization", "Bearer " + ctx.parentToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dataExportWorksAndCrossFamilyBlocked() throws Exception {
        FamilyContext ctxA = setupFamily();
        FamilyContext ctxB = setupFamily();

        // A 家长导出 A 家庭儿童数据 -> 200 DONE
        MvcResult exp = mockMvc.perform(post("/api/compliance/data-export")
                        .header("Authorization", "Bearer " + ctxA.parentToken())
                        .contentType(JSON)
                        .content("{\"childId\":" + childUserId(ctxA) + "}"))
                .andExpect(status().isOk())
                .andReturn();
        DataExportResp der = dataOf(exp, DataExportResp.class);
        assertEquals("DONE", der.getStatus());

        // B 家长导出 A 家庭儿童 -> 越权 403
        mockMvc.perform(post("/api/compliance/data-export")
                        .header("Authorization", "Bearer " + ctxB.parentToken())
                        .contentType(JSON)
                        .content("{\"childId\":" + childUserId(ctxA) + "}"))
                .andExpect(status().isForbidden());
    }
}
