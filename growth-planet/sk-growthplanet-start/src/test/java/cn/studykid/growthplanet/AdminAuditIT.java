package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.dto.response.AdminAccountResp;
import cn.studykid.growthplanet.dto.response.AdminAuditLogResp;
import cn.studykid.growthplanet.dto.response.AdminLoginResp;
import cn.studykid.growthplanet.entity.AuditLog;
import cn.studykid.growthplanet.mapper.AuditLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 操作日志与审计集成测试：
 * 列表筛选/详情、C 端关键操作白名单、CSV 导出落审计、
 * RBAC（M6 查看=全部角色、导出=SA/RA）与 RA 只读语义。
 */
class AdminAuditIT extends BaseIT {

    private static final String BOOTSTRAP_USERNAME = "admin.zhou";
    private static final String BOOTSTRAP_PASSWORD = "admin123";

    @Autowired
    private AuditLogMapper auditLogMapper;

    private String adminLogin() throws Exception {
        return adminLogin(BOOTSTRAP_USERNAME, BOOTSTRAP_PASSWORD);
    }

    private String adminLogin(String username, String password) throws Exception {
        var result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result, AdminLoginResp.class).getToken();
    }

    /** 直插一条审计记录（createTime/updateTime 由 MetaObjectHandler 自动填充）。 */
    private Long seedAudit(String action, String targetType, String result) {
        AuditLog log = new AuditLog();
        log.setActorUserId(99901L);
        log.setFamilyId(1L);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(1001L);
        log.setIp("127.0.0.1");
        log.setDetail("it-seed action=" + action);
        log.setRequestId("it-" + System.nanoTime());
        log.setResult(result);
        auditLogMapper.insert(log);
        return log.getId();
    }

    private String createRoleAccount(String saToken, String roleCode) throws Exception {
        String username = roleCode.toLowerCase() + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String password = roleCode.toLowerCase() + "123456";
        var created = mockMvc.perform(post("/api/admin/accounts")
                        .header("Authorization", "Bearer " + saToken)
                        .contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"name\":\"测试" + roleCode + "\","
                                + "\"password\":\"" + password + "\",\"roleCode\":\"" + roleCode + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        AdminAccountResp account = dataOf(created, AdminAccountResp.class);
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        return adminLogin(username, password);
    }

    @Test
    @DisplayName("操作日志列表：分页结构 + 按 action 筛选命中")
    void listAuditLogsPagedAndFiltered() throws Exception {
        Long seeded = seedAudit("WALLET_GRANT", "WALLET", "SUCCESS");
        String token = adminLogin();

        var result = mockMvc.perform(get("/api/admin/audit-logs")
                        .param("action", "WALLET_GRANT")
                        .param("page", "1").param("pageSize", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.items[0].id").value(seeded))
                .andExpect(jsonPath("$.data.items[0].action").value("WALLET_GRANT"))
                // 操作人展示名解析（99901 非真实账号 → 回退 #id 形态）
                .andExpect(jsonPath("$.data.items[0].actorLabel",
                        org.hamcrest.Matchers.startsWith("#")))
                .andReturn();
    }

    @Test
    @DisplayName("时间筛选：begin 在明天时结果为空")
    void timeFilterExcludesFutureBegin() throws Exception {
        seedAudit("MEDAL_AWARD", "MEDAL", "SUCCESS");
        String token = adminLogin();
        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("action", "MEDAL_AWARD")
                        .param("begin", LocalDate.now().plusDays(1).toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("审计详情返回明细与请求 ID")
    void auditDetailReturnsFields() throws Exception {
        Long id = seedAudit("CONFIRM_SUBMIT", "MENU_CONFIRM", "SUCCESS");
        String token = adminLogin();
        var result = mockMvc.perform(get("/api/admin/audit-logs/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        AdminAuditLogResp detail = dataOf(result, AdminAuditLogResp.class);
        assertThat(detail.getId()).isEqualTo(id);
        assertThat(detail.getDetail()).contains("it-seed");
        assertThat(detail.getRequestId()).startsWith("it-");
    }

    @Test
    @DisplayName("C 端关键操作仅返回白名单动作（家庭/绑定、额度、数据出口）")
    void cAuditLogsWhitelisted() throws Exception {
        Long whitelisted = seedAudit("WALLET_GRANT", "WALLET", "SUCCESS");
        seedAudit("CHORE_SUBMIT", "CHORE", "SUCCESS");
        String token = adminLogin();

        var result = mockMvc.perform(get("/api/admin/c-audit-logs")
                        .param("page", "1").param("pageSize", "50")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andReturn();

        java.util.Set<String> whitelist = java.util.Set.of(
                "CREATE_FAMILY", "JOIN", "BIND", "GRANT", "REVOKE",
                "WALLET_GRANT", "ALLOWANCE_RULE",
                "EXPORT", "PRIVACY_DOWNLOAD", "PRIVACY_QUERY", "PRIVACY_TRANSITION");
        tools.jackson.databind.JsonNode items =
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("data").path("items");
        assertThat(items.size()).isGreaterThanOrEqualTo(1);
        boolean containsSeeded = false;
        for (tools.jackson.databind.JsonNode item : items) {
            assertThat(item.path("action").asText()).isIn(whitelist);
            if (item.path("id").asLong() == whitelisted) {
                containsSeeded = true;
            }
        }
        assertThat(containsSeeded).isTrue();
    }

    @Test
    @DisplayName("SA 导出 CSV：响应头与表头正确，且导出行为本身落 EXPORT 审计")
    void exportCsvAndSelfAudited() throws Exception {
        seedAudit("PRIVACY_DOWNLOAD", "PRIVACY_REQUEST", "SUCCESS");
        String token = adminLogin();

        byte[] body = mockMvc.perform(get("/api/admin/audit-logs/export")
                        .param("action", "PRIVACY_DOWNLOAD")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();

        String csv = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("PRIVACY_DOWNLOAD");
        assertThat(csv).contains("it-seed");

        // 导出动作本身落审计（EXPORT + sys_audit_log）
        var latest = auditLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditLog>()
                        .eq(AuditLog::getAction, "EXPORT")
                        .eq(AuditLog::getTargetType, "sys_audit_log")
                        .orderByDesc(AuditLog::getId)
                        .last("LIMIT 1"));
        assertThat(latest).isNotEmpty();
        assertThat(latest.get(0).getDetail()).contains("audit-log export");
    }

    @Test
    @DisplayName("RBAC：OP 可查操作日志（M6 查看=全部）但导出被拒（导出=SA,RA）")
    void operatorCanViewButNotExport() throws Exception {
        String saToken = adminLogin();
        String opToken = createRoleAccount(saToken, "OP");

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + opToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber());

        mockMvc.perform(get("/api/admin/audit-logs/export")
                        .header("Authorization", "Bearer " + opToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("E-009"));
    }

    @Test
    @DisplayName("RBAC：RA 可查可导出，但无任何写权限入口（账号管理 403）")
    void readOnlyAuditorCanViewAndExport() throws Exception {
        String saToken = adminLogin();
        String raToken = createRoleAccount(saToken, "RA");

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + raToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/audit-logs/export")
                        .header("Authorization", "Bearer " + raToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("text/csv")));

        // RA 只读：账号管理不可见
        mockMvc.perform(get("/api/admin/accounts")
                        .header("Authorization", "Bearer " + raToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("缺少 admin token 访问审计接口返回 401")
    void auditWithoutTokenRejected() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/c-audit-logs"))
                .andExpect(status().isUnauthorized());
    }
}
