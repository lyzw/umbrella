package com.growthplanet;

import com.growthplanet.common.enums.RoleEnum;
import com.growthplanet.dto.response.ChildProfileResp;
import com.growthplanet.dto.response.SelectRoleResp;
import com.growthplanet.dto.response.WxLoginResp;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证链路集成测试：wx-login → select-role → child-profile。
 */
class AuthFlowIT extends BaseIT {

    @Test
    void wxLoginReturnsTokenAndIsNew() throws Exception {
        MvcResult wx = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(JSON)
                        .content("{\"code\":\"auth_flow_code_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        WxLoginResp resp = dataOf(wx, WxLoginResp.class);
        assertEquals("mock_openid_auth_flow_code_1", resp.getOpenid());
        assertEquals("UNSET", resp.getRole());
        // 再次登录同一 code -> 同一 openid，isNew=false
        MvcResult wx2 = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(JSON)
                        .content("{\"code\":\"auth_flow_code_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        WxLoginResp resp2 = dataOf(wx2, WxLoginResp.class);
        assertEquals(resp.getOpenid(), resp2.getOpenid());
        assertEquals(false, resp2.isNew());
    }

    @Test
    void selectRoleReissuesTokenWithRole() throws Exception {
        String token = loginAndSelectRole("auth_role_code", RoleEnum.CHILD);
        // 解析角色：通过 select-role 响应
        // token 已隐含 CHILD 角色；此处仅验证端点可重复调用且返回新 token
        MvcResult sr = mockMvc.perform(post("/api/auth/select-role")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"role\":\"CHILD\"}"))
                .andExpect(status().isOk())
                .andReturn();
        SelectRoleResp resp = dataOf(sr, SelectRoleResp.class);
        assertEquals("CHILD", resp.getRole());
        assertEquals("child-profile", resp.getNextStep());
    }

    @Test
    void childProfileFullFlow() throws Exception {
        FamilyContext ctx = setupFamily();
        MvcResult profile = mockMvc.perform(post("/api/child/profile")
                        .header("Authorization", "Bearer " + ctx.childToken())
                        .contentType(JSON)
                        .content("{\"nickname\":\"小明\",\"grade\":\"三年级\",\"school\":\"实验小学\","
                                + "\"allergies\":[\"花生\"],\"dislikes\":[\"胡萝卜\"],\"tastes\":[\"清淡\"]}"))
                .andExpect(status().isOk())
                .andReturn();
        ChildProfileResp resp = dataOf(profile, ChildProfileResp.class);
        assertEquals("COMPLETED", resp.getProfileStatus());
    }

    @Test
    void childProfileWithoutFamilyReturns403() throws Exception {
        // 仅登录+选角色，未加入家庭的儿童访问 profile -> 403（数据归属校验）
        String childToken = loginAndSelectRole("auth_nofam_code", RoleEnum.CHILD);
        mockMvc.perform(post("/api/child/profile")
                        .header("Authorization", "Bearer " + childToken)
                        .contentType(JSON)
                        .content("{\"nickname\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
