package com.growthplanet;

import com.growthplanet.common.enums.RoleEnum;
import com.growthplanet.dto.response.BindApproveResp;
import com.growthplanet.dto.response.CreateFamilyResp;
import com.growthplanet.dto.response.InviteCodeResp;
import com.growthplanet.dto.response.JoinFamilyResp;
import com.growthplanet.util.InviteCodeUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 家庭链路集成测试：create / invite-code / join / bind-approve + 越权 403（E-009）。
 */
class FamilyFlowIT extends BaseIT {

    @Test
    void createFamilyReturnsValidInviteCodeAndToken() throws Exception {
        String parentToken = loginAndSelectRole("fam_create_code", RoleEnum.PARENT);
        MvcResult create = mockMvc.perform(post("/api/family/create")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(JSON)
                        .content("{\"familyName\":\"小明家\"}"))
                .andExpect(status().isOk())
                .andReturn();
        CreateFamilyResp resp = dataOf(create, CreateFamilyResp.class);
        assertTrue(InviteCodeUtil.isValidFormat(resp.getInviteCode()), "邀请码格式应合法");
        assertTrue(resp.getExpireAt() > System.currentTimeMillis(), "过期时间应晚于当前");
        // 返回 token 已写入 family_ids
        assertTrue(resp.getToken() != null && !resp.getToken().isEmpty());
    }

    @Test
    void inviteCodeReuseAndJoinApproveFlow() throws Exception {
        FamilyContext ctx = setupFamily();
        // 查询邀请码
        MvcResult inv = mockMvc.perform(get("/api/family/invite-code")
                        .header("Authorization", "Bearer " + ctx.parentToken()))
                .andExpect(status().isOk())
                .andReturn();
        InviteCodeResp invResp = dataOf(inv, InviteCodeResp.class);
        assertEquals(ctx.inviteCode(), invResp.getInviteCode());

        // 审批通过
        MvcResult approve = mockMvc.perform(post("/api/family/bind-approve")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{\"applyId\":" + ctx.applyId() + ",\"relationLabel\":\"娃\",\"approve\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        BindApproveResp apResp = dataOf(approve, BindApproveResp.class);
        assertEquals("APPROVED", apResp.getBindStatus());
    }

    @Test
    void childCannotCreateFamily_403() throws Exception {
        String childToken = loginAndSelectRole("fam_child_create", RoleEnum.CHILD);
        mockMvc.perform(post("/api/family/create")
                        .header("Authorization", "Bearer " + childToken)
                        .contentType(JSON)
                        .content("{\"familyName\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void parentCannotJoinFamily_403() throws Exception {
        String parentToken = loginAndSelectRole("fam_parent_join", RoleEnum.PARENT);
        mockMvc.perform(post("/api/family/join")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(JSON)
                        .content("{\"inviteCode\":\"ABC123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossFamilyApproveBlocked_403() throws Exception {
        // 家庭 A：家长 A + 儿童 A；家庭 B：家长 B
        FamilyContext ctxA = setupFamily();
        FamilyContext ctxB = setupFamily();

        // 家长 B 尝试审批家庭 A 的儿童申请 -> 跨家庭，应 403
        mockMvc.perform(post("/api/family/bind-approve")
                        .header("Authorization", "Bearer " + ctxB.parentToken())
                        .contentType(JSON)
                        .content("{\"applyId\":" + ctxA.applyId() + ",\"approve\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void joinWithInvalidCode_400() throws Exception {
        String childToken = loginAndSelectRole("fam_join_bad", RoleEnum.CHILD);
        mockMvc.perform(post("/api/family/join")
                        .header("Authorization", "Bearer " + childToken)
                        .contentType(JSON)
                        .content("{\"inviteCode\":\"ZZZZZZ\"}"))
                .andExpect(status().isBadRequest());
    }
}
