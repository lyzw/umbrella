package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.dto.response.AdminLoginResp;
import cn.studykid.growthplanet.dto.response.AdminMeResp;
import cn.studykid.growthplanet.dto.response.AdminWorkbenchResp;
import cn.studykid.growthplanet.entity.AuditLog;
import cn.studykid.growthplanet.mapper.AuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台运营端地基纵切片集成测试：
 * 认证隔离（独立账号/密钥/前缀）、RBAC 细粒度鉴权、审计落库、M0 工作台、M2 账号与权限矩阵。
 */
class AdminConsoleIT extends BaseIT {

    private static final String BOOTSTRAP_USERNAME = "admin.zhou";
    private static final String BOOTSTRAP_PASSWORD = "admin123";

    @Autowired
    private AuditLogMapper auditLogMapper;

    private String adminLogin() throws Exception {
        return adminLogin(BOOTSTRAP_USERNAME, BOOTSTRAP_PASSWORD);
    }

    private String adminLogin(String username, String password) throws Exception {
        var result = mockMvc.perform(post("/api/console/auth/login")
                        .contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result, AdminLoginResp.class).getToken();
    }

    @Test
    @DisplayName("首个超级管理员可登录并返回角色与权限点")
    void bootstrapSuperAdminCanLogin() throws Exception {
        var result = mockMvc.perform(post("/api/console/auth/login")
                        .contentType(JSON)
                        .content("{\"username\":\"" + BOOTSTRAP_USERNAME + "\",\"password\":\""
                                + BOOTSTRAP_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.roleCode").value("SA"))
                .andReturn();

        AdminLoginResp resp = dataOf(result, AdminLoginResp.class);
        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getPermissions()).isNotNull();
    }

    @Test
    @DisplayName("密码错误返回 401")
    void wrongPasswordRejected() throws Exception {
        mockMvc.perform(post("/api/console/auth/login")
                        .contentType(JSON)
                        .content("{\"username\":\"" + BOOTSTRAP_USERNAME + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E-001"));
    }

    @Test
    @DisplayName("缺少 admin token 访问后台接口返回 401")
    void consoleWithoutTokenRejected() throws Exception {
        mockMvc.perform(get("/api/console/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E-001"));
    }

    @Test
    @DisplayName("C 端 token 无法访问后台接口（账号/密钥体系隔离）")
    void cEndTokenCannotAccessConsole() throws Exception {
        String cEndToken = loginAndSelectRole("c_admin_" + java.util.UUID.randomUUID(), RoleEnum.PARENT);
        mockMvc.perform(get("/api/console/auth/me")
                        .header("Authorization", "Bearer " + cEndToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/me 回显当前管理员身份")
    void meReturnsIdentity() throws Exception {
        String token = adminLogin();
        var result = mockMvc.perform(get("/api/console/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(BOOTSTRAP_USERNAME))
                .andExpect(jsonPath("$.data.superAdmin").value(true))
                .andReturn();
        AdminMeResp me = dataOf(result, AdminMeResp.class);
        assertThat(me.getRoleCode()).isEqualTo("SA");
    }

    @Test
    @DisplayName("登出后旧 token 立即失效（token_version 递增）")
    void logoutInvalidatesToken() throws Exception {
        String token = adminLogin();
        mockMvc.perform(post("/api/console/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/console/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("工作台返回概览 KPI 与待办入口")
    void workbenchReturnsKpis() throws Exception {
        String token = adminLogin();
        var result = mockMvc.perform(get("/api/console/workbench")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis").exists())
                .andExpect(jsonPath("$.data.kpis.familyTotal").isNumber())
                .andReturn();
        AdminWorkbenchResp resp = dataOf(result, AdminWorkbenchResp.class);
        assertThat(resp.getKpis()).isNotNull();
        assertThat(resp.getTodos()).isNotEmpty();
    }

    @Test
    @DisplayName("账号列表包含首个超级管理员")
    void listAccountsContainsBootstrap() throws Exception {
        String token = adminLogin();
        mockMvc.perform(get("/api/console/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.items[0].username").value(BOOTSTRAP_USERNAME));
    }

    @Test
    @DisplayName("角色列表包含 6 个后台角色")
    void listRolesHasSix() throws Exception {
        String token = adminLogin();
        mockMvc.perform(get("/api/console/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Test
    @DisplayName("权限矩阵：OP 在菜品库具备 view 但不具备角色权限")
    void permissionMatrixForOp() throws Exception {
        String token = adminLogin();
        mockMvc.perform(get("/api/console/roles/permissions").param("roleCode", "OP")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(
                        cn.studykid.growthplanet.common.constant.AdminResource.ALL.size()))
                // 资源顺序固定，索引 7 = 菜品库（OP 有 view），索引 3 = 后台账号（OP 无 view）
                .andExpect(jsonPath("$.data[7].resource").value("菜品库"))
                .andExpect(jsonPath("$.data[7].perms.view").value(true))
                .andExpect(jsonPath("$.data[3].resource").value("后台账号"))
                .andExpect(jsonPath("$.data[3].perms.view").value(false));
    }

    @Test
    @DisplayName("RBAC：SA 新建 OP 账号后，OP 可看工作台但被拒于账号/角色管理")
    void createOperatorAndEnforceRbac() throws Exception {
        String saToken = adminLogin();
        String opUsername = "op_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String opPassword = "op123456";

        mockMvc.perform(post("/api/console/accounts")
                        .header("Authorization", "Bearer " + saToken)
                        .contentType(JSON)
                        .content("{\"username\":\"" + opUsername + "\",\"name\":\"运营演示\","
                                + "\"password\":\"" + opPassword + "\",\"roleCode\":\"OP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("OP"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        String opToken = adminLogin(opUsername, opPassword);

        // OP 有 工作台首页:view
        mockMvc.perform(get("/api/console/workbench")
                        .header("Authorization", "Bearer " + opToken))
                .andExpect(status().isOk());

        // OP 无 后台账号:view → 403
        mockMvc.perform(get("/api/console/accounts")
                        .header("Authorization", "Bearer " + opToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("E-009"));

        // OP 无 角色权限:view → 403
        mockMvc.perform(get("/api/console/roles")
                        .header("Authorization", "Bearer " + opToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("禁用账号后其 token 失效")
    void disableAccountInvalidatesToken() throws Exception {
        String saToken = adminLogin();
        String username = "dc_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String password = "dc123456";

        var created = mockMvc.perform(post("/api/console/accounts")
                        .header("Authorization", "Bearer " + saToken)
                        .contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"name\":\"客服演示\","
                                + "\"password\":\"" + password + "\",\"roleCode\":\"DC\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Long id = dataOf(created, cn.studykid.growthplanet.dto.response.AdminAccountResp.class).getId();

        String dcToken = adminLogin(username, password);
        mockMvc.perform(get("/api/console/auth/me")
                        .header("Authorization", "Bearer " + dcToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/console/accounts/" + id + "/status").param("status", "DISABLED")
                        .header("Authorization", "Bearer " + saToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(get("/api/console/auth/me")
                        .header("Authorization", "Bearer " + dcToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("后台登录写入审计日志")
    void adminLoginWritesAuditLog() throws Exception {
        adminLogin();
        long count = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getAction, "ADMIN_LOGIN"));
        assertThat(count).isGreaterThan(0);
    }
}
