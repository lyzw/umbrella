package cn.studykid.growthplanet;

import cn.studykid.growthplanet.dto.response.AdminLoginResp;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M5 合规与隐私中心集成测试（里程碑 A 批次 4）：
 * 同意留痕、隐私工单（CP 核验闭环 / RA 隔离 403）、核验记录、合规清单勾检。
 */
class AdminPrivacyIT extends BaseIT {

    @Autowired
    private JdbcTemplate jdbc;

    private String adminLogin() throws Exception {
        var result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(JSON)
                        .content("{\"username\":\"admin.zhou\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result, AdminLoginResp.class).getToken();
    }

    /** 用 SA 创建一个指定角色的新后台账号并登录，返回其 token。 */
    private String roleLogin(String saToken, String roleCode) throws Exception {
        String username = "it-" + roleCode.toLowerCase() + "-" + System.nanoTime();
        mockMvc.perform(post("/api/admin/accounts")
                        .header("Authorization", "Bearer " + saToken)
                        .contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"name\":\"IT" + roleCode
                                + "\",\"password\":\"Passw0rd!\",\"roleCode\":\"" + roleCode + "\"}"))
                .andExpect(status().isOk());
        var login = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(login, AdminLoginResp.class).getToken();
    }

    private long insertReturningId(String sql) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("INSERT 未返回自增主键: " + sql);
        }
        return key.longValue();
    }

    private long seedChild(String nickname) {
        long userId = insertReturningId(
                "INSERT INTO usr_user (openid, role, nickname) VALUES ('it-openid-"
                        + System.nanoTime() + "', 'CHILD', '" + nickname + "')");
        long familyId = insertReturningId(
                "INSERT INTO usr_family (family_name, owner_user_id) VALUES ('IT隐私家庭', 99901)");
        jdbc.update("INSERT INTO usr_child_profile (user_id, family_id, nickname, profile_status) VALUES ("
                + userId + ", " + familyId + ", '" + nickname + "', 'COMPLETE')");
        return userId;
    }

    private long seedConsent(long childId, long familyId) {
        return insertReturningId(
                "INSERT INTO usr_consent_log (user_id, child_id, family_id, consent_type, action, "
                        + "version, guardian_status, signed_at) VALUES (99901, " + childId + ", "
                        + familyId + ", 'ORDER', 'GRANT', '1.0', 'VERIFIED', 1700000000000)");
    }

    /** 直插隐私工单（默认 RECEIVED 待核验）。 */
    private long seedPrivacyRequest(long childId, long familyId, String status) {
        String suffix = Long.toHexString(System.nanoTime());
        return insertReturningId(
                "INSERT INTO sys_privacy_request (requester_id, child_id, family_id, request_type, "
                        + "idempotency_key, request_hash, status) VALUES (99901, " + childId + ", "
                        + familyId + ", 'EXPORT', 'it-key-" + suffix + "', 'it-hash-" + suffix + "', '"
                        + status + "')");
    }

    private JsonNode bodyOf(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    @DisplayName("同意留痕：CP 可查询，回显全名（fullPii）")
    void consentListForCp() throws Exception {
        String saToken = adminLogin();
        String cpToken = roleLogin(saToken, "CP");
        long childId = seedChild("隐私小子");
        seedConsent(childId, 1L);

        mockMvc.perform(get("/api/admin/consents")
                        .param("childId", String.valueOf(childId))
                        .header("Authorization", "Bearer " + cpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].childName").value("隐私小子"))
                .andExpect(jsonPath("$.data.items[0].consentType").value("ORDER"));
    }

    @Test
    @DisplayName("隐私工单隔离：CP/SA/RA 可见，OP/DC/CR 必须 403")
    void privacyRequestIsolation() throws Exception {
        String saToken = adminLogin();
        long childId = seedChild("隔离娃");
        long reqId = seedPrivacyRequest(childId, 1L, "RECEIVED");

        String cpToken = roleLogin(saToken, "CP");
        String raToken = roleLogin(saToken, "RA");
        String opToken = roleLogin(saToken, "OP");
        String dcToken = roleLogin(saToken, "DC");
        String crToken = roleLogin(saToken, "CR");

        // 可见角色
        mockMvc.perform(get("/api/admin/privacy-requests")
                        .param("status", "RECEIVED")
                        .header("Authorization", "Bearer " + cpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/admin/privacy-requests")
                        .param("status", "RECEIVED")
                        .header("Authorization", "Bearer " + raToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        // CP 可见孩子全名；RA 仅持 childId（PII 隔离，childName 为空）
        String cpBody = mockMvc.perform(get("/api/admin/privacy-requests")
                        .param("status", "RECEIVED")
                        .header("Authorization", "Bearer " + cpToken))
                .andReturn().getResponse().getContentAsString();
        assertThat(cpBody).contains("隔离娃");
        String raBody = mockMvc.perform(get("/api/admin/privacy-requests")
                        .param("status", "RECEIVED")
                        .header("Authorization", "Bearer " + raToken))
                .andReturn().getResponse().getContentAsString();
        assertThat(raBody).doesNotContain("隔离娃");

        // 不可见角色 → 403
        for (String token : new String[]{opToken, dcToken, crToken}) {
            mockMvc.perform(get("/api/admin/privacy-requests")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("CP 核验闭环：RECEIVED→PROCESSING + 写入核验哈希；再核验 400；驳回→REJECTED")
    void verifyAndRejectFlow() throws Exception {
        String saToken = adminLogin();
        String cpToken = roleLogin(saToken, "CP");
        long childId = seedChild("核验娃");
        long reqId = seedPrivacyRequest(childId, 1L, "RECEIVED");

        // 核验
        mockMvc.perform(post("/api/admin/privacy-requests/" + reqId + "/verify")
                        .header("Authorization", "Bearer " + cpToken)
                        .contentType(JSON)
                        .content("{\"code\":\"ID-CHECK-8848\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        // 核验记录落库，codeHash 脱敏（前 8 位 + ****）
        var verifs = mockMvc.perform(get("/api/admin/privacy-verifications")
                        .header("Authorization", "Bearer " + cpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn();
        String codeHashMasked = bodyOf(verifs).path("items").get(0).path("codeHashMasked").asText();
        assertThat(codeHashMasked).endsWith("****");
        assertThat(codeHashMasked.length()).isGreaterThan(8);

        // 已非 RECEIVED，再核验 → 400
        mockMvc.perform(post("/api/admin/privacy-requests/" + reqId + "/verify")
                        .header("Authorization", "Bearer " + cpToken)
                        .contentType(JSON)
                        .content("{\"code\":\"ANOTHER\"}"))
                .andExpect(status().isBadRequest());

        // 驳回（PROCESSING → REJECTED）
        mockMvc.perform(post("/api/admin/privacy-requests/" + reqId + "/reject")
                        .header("Authorization", "Bearer " + cpToken)
                        .contentType(JSON)
                        .content("{\"reason\":\"核验材料不足，已联系监护人补充\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.errorCode").value("核验材料不足，已联系监护人补充"));
    }

    @Test
    @DisplayName("合规清单：初始化播种 5 项；CP 勾检生效；RA 仅可见不可勾检(403)")
    void complianceChecklist() throws Exception {
        String saToken = adminLogin();
        String cpToken = roleLogin(saToken, "CP");
        String raToken = roleLogin(saToken, "RA");

        var list = mockMvc.perform(get("/api/admin/compliance-checklist")
                        .header("Authorization", "Bearer " + cpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andReturn();
        assertThat(bodyOf(list).get(0).path("itemKey").asText()).isNotBlank();

        // CP 勾检第一项
        String firstKey = bodyOf(list).get(0).path("itemKey").asText();
        mockMvc.perform(put("/api/admin/compliance-checklist")
                        .header("Authorization", "Bearer " + cpToken)
                        .contentType(JSON)
                        .content("{\"itemKey\":\"" + firstKey + "\",\"checked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checked").value(true))
                .andExpect(jsonPath("$.data.checkedBy").isNumber());

        // 勾检后列表回显
        mockMvc.perform(get("/api/admin/compliance-checklist")
                        .header("Authorization", "Bearer " + cpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].checked").value(true));

        // RA 可见，但勾检 → 403（仅 CP/SA 有 config 权限）
        mockMvc.perform(get("/api/admin/compliance-checklist")
                        .header("Authorization", "Bearer " + raToken))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/compliance-checklist")
                        .header("Authorization", "Bearer " + raToken)
                        .contentType(JSON)
                        .content("{\"itemKey\":\"" + firstKey + "\",\"checked\":false}"))
                .andExpect(status().isForbidden());
    }
}
