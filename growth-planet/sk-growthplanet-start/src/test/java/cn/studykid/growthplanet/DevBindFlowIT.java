package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.dto.response.FamilyChildResp;
import cn.studykid.growthplanet.dto.response.QuickBindParentResp;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 开发/测试专用端点 quick-bind-parent 集成测试（仅 test profile 运行）。
 */
class DevBindFlowIT extends BaseIT {

    @Test
    void quickBindParentBindsChildAndRefreshesToken() throws Exception {
        String childToken = loginAndSelectRole("dev_child_1", RoleEnum.CHILD);

        MvcResult bind = mockMvc.perform(post("/api/dev/quick-bind-parent")
                        .header("Authorization", "Bearer " + childToken)
                        .contentType(JSON)
                        .content("{\"parentAccount\":\"dev_parent_1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        QuickBindParentResp resp = dataOf(bind, QuickBindParentResp.class);
        assertNotNull(resp.getFamilyId());
        assertEquals("BOUND", resp.getBindStatus());
        assertNotNull(resp.getToken());

        // 返回的刷新 token 应能以儿童身份查询到绑定状态为 BOUND
        MvcResult binding = mockMvc.perform(get("/api/family/binding")
                        .header("Authorization", "Bearer " + resp.getToken()))
                .andExpect(status().isOk())
                .andReturn();
        FamilyChildResp b = dataOf(binding, FamilyChildResp.class);
        assertEquals("BOUND", b.getBindStatus());
        assertEquals(resp.getFamilyId(), b.getFamilyId());
    }

    @Test
    void quickBindParentRequiresChildRole_403() throws Exception {
        String parentToken = loginAndSelectRole("dev_parent_only", RoleEnum.PARENT);
        mockMvc.perform(post("/api/dev/quick-bind-parent")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(JSON)
                        .content("{\"parentAccount\":\"dev_parent_2\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void quickBindParentRejectsBadAccount_400() throws Exception {
        String childToken = loginAndSelectRole("dev_child_bad", RoleEnum.CHILD);
        mockMvc.perform(post("/api/dev/quick-bind-parent")
                        .header("Authorization", "Bearer " + childToken)
                        .contentType(JSON)
                        .content("{\"parentAccount\":\"bad account!\"}"))
                .andExpect(status().isBadRequest());
    }
}
