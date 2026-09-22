package cn.studykid.growthplanet;

import cn.studykid.growthplanet.dto.response.AdminLoginResp;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M4 业务数据集成测试（里程碑 A 批次 3）：
 * 想吃/确认单/超额复核（仅审计）/审批记录/钱包流水/家务健康/勋章补发/CSV 导出 + RBAC（RA 403、OP 放行）。
 */
class AdminBizDataIT extends BaseIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    private long seedFamily() {
        return insertReturningId(
                "INSERT INTO usr_family (family_name, owner_user_id) VALUES ('IT业务家庭', 99901)");
    }

    /** 直插儿童用户（nickname 用于脱敏断言），返回 userId。 */
    private long seedChild(String nickname) {
        return insertReturningId("INSERT INTO usr_user (openid, role, nickname) VALUES ('it-openid-"
                + System.nanoTime() + "', 'CHILD', '" + nickname + "')");
    }

    private long seedWallet(long childId, long familyId) {
        return insertReturningId("INSERT INTO life_wallet (child_id, family_id, balance) VALUES ("
                + childId + ", " + familyId + ", 88.00)");
    }

    private long seedCategory(String name) {
        return insertReturningId(
                "INSERT INTO life_dish_category (name, sort, status) VALUES ('" + name + "', 0, 'ENABLED')");
    }

    private long seedFamilyDish(long familyId, long categoryId, String name) {
        return insertReturningId(
                "INSERT INTO life_family_dish (family_id, category_id, name, virtual_price, spice_level) "
                        + "VALUES (" + familyId + ", " + categoryId + ", '" + name + "', 12.50, 1)");
    }

    private long seedWantEat(long familyId, long childId, String dishType, long dishId) {
        return insertReturningId(
                "INSERT INTO usr_child_want_eat (child_id, family_id, menu_date, meal_type, source_type, "
                        + "dish_type, dish_id) VALUES (" + childId + ", " + familyId + ", '2026-09-22', "
                        + "'LUNCH', 'FAMILY', '" + dishType + "', " + dishId + ")");
    }

    /** 直插确认单（超额可指定），返回 confirmId。 */
    private long seedConfirm(long childId, long familyId, boolean overLimit) {
        String suffix = Long.toHexString(System.nanoTime());
        return insertReturningId(
                "INSERT INTO life_menu_confirm (confirm_no, child_id, family_id, menu_id, menu_date, "
                        + "meal_type, total_amount, status, request_key, request_hash, estimated_balance, "
                        + "is_over_limit, submit_time) VALUES ('ITC" + suffix + "', " + childId + ", "
                        + familyId + ", 1, '2026-09-22', 'LUNCH', 66.00, 'PENDING', 'it-key-" + suffix
                        + "', 'it-hash-" + suffix + "', 34.00, " + (overLimit ? 1 : 0)
                        + ", CURRENT_TIMESTAMP)");
    }

    private long seedConfirmItem(long confirmId, String dishName) {
        return insertReturningId(
                "INSERT INTO life_menu_item (confirm_id, dish_id, dish_name, quantity, unit_price, subtotal) "
                        + "VALUES (" + confirmId + ", 11, '" + dishName + "', 2, 33.00, 66.00)");
    }

    private long seedApproval(long confirmId) {
        return insertReturningId(
                "INSERT INTO life_confirm_approval (confirm_id, parent_id, action, before_status, "
                        + "after_status, is_over_limit, reason) VALUES (" + confirmId
                        + ", 99902, 'REJECT', 'PENDING', 'REJECTED', 1, '超额度了，明天再买')");
    }

    private long seedAllowanceRule(long childId, long familyId) {
        return insertReturningId(
                "INSERT INTO life_allowance_rule (child_id, family_id, daily_period, weekly_period) "
                        + "VALUES (" + childId + ", " + familyId + ", '2026-09-22', '2026-09-22')");
    }

    private long seedAllowanceLog(long walletId, long childId, long familyId) {
        return insertReturningId(
                "INSERT INTO life_allowance_log (wallet_id, child_id, family_id, operator_id, trans_type, "
                        + "scene, amount, balance_before, balance_after, wallet_version, usage_date) "
                        + "VALUES (" + walletId + ", " + childId + ", " + familyId + ", 99902, 'GRANT', "
                        + "'IT_SCENE', 10.00, 78.00, 88.00, 1, '2026-09-22')");
    }

    private long seedChoreInstance(long familyId, long childId, String status) {
        return insertReturningId(
                "INSERT INTO life_chore_instance (task_id, family_id, child_id, status, claim_date) "
                        + "VALUES (1, " + familyId + ", " + childId + ", '" + status + "', '2026-09-22')");
    }

    private long seedCheckItem(long familyId, String name) {
        return insertReturningId(
                "INSERT INTO life_check_item (family_id, name) VALUES (" + familyId + ", '" + name + "')");
    }

    private long seedCheckRecord(long familyId, long childId, long itemId, String itemName) {
        return insertReturningId(
                "INSERT INTO life_check_record (family_id, child_id, item_id, item_name, check_date, "
                        + "check_time) VALUES (" + familyId + ", " + childId + ", " + itemId + ", '"
                        + itemName + "', '2026-09-22', CURRENT_TIMESTAMP)");
    }

    private long seedMedalDefinition(String code) {
        return insertReturningId(
                "INSERT INTO life_medal_definition (code, name, category, condition_type, threshold) "
                        + "VALUES ('" + code + "', 'IT补发勋章', 'CHORE', 'EVENT', 1)");
    }

    private JsonNode bodyOf(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return MAPPER.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    @DisplayName("想吃清单：FAMILY 菜名回显 + 孩子昵称脱敏 + 餐次筛选")
    void wantEatListMaskAndFilter() throws Exception {
        String token = adminLogin();
        long familyId = seedFamily();
        long childId = seedChild("王小明");
        long categoryId = seedCategory("IT想吃分类-" + System.nanoTime());
        long dishId = seedFamilyDish(familyId, categoryId, "妈妈牌红烧肉");
        seedWantEat(familyId, childId, "FAMILY", dishId);
        long otherChild = seedChild("李小花");
        seedWantEat(familyId, otherChild, "FAMILY", dishId);

        var list = mockMvc.perform(get("/api/admin/want-eat")
                        .param("familyId", String.valueOf(familyId))
                        .param("mealType", "LUNCH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        JsonNode items = bodyOf(list).path("items");
        JsonNode target = null;
        for (JsonNode n : items) {
            // 同日期按 id 倒序，两条都可能排首；按菜名定位王小明的行
            if ("妈妈牌红烧肉".equals(n.path("dishName").asText())
                    && n.path("childId").asLong() == childId) {
                target = n;
            }
            assertThat(n.path("childName").asText()).endsWith("*");
        }
        assertThat(target).as("未找到王小明的想吃记录").isNotNull();
        assertThat(target.path("childName").asText()).isEqualTo("王*");

        // 日期范围过滤（空结果域）
        mockMvc.perform(get("/api/admin/want-eat")
                        .param("familyId", String.valueOf(familyId))
                        .param("from", "2026-01-01").param("to", "2026-01-02")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("超额确认单复核：结论落审计、不改状态；非超额 400；非法结论 400")
    void confirmationReviewAuditOnly() throws Exception {
        String token = adminLogin();
        long familyId = seedFamily();
        long childId = seedChild("赵小刚");
        long confirmId = seedConfirm(childId, familyId, true);
        seedConfirmItem(confirmId, "番茄炒蛋");

        var detail = mockMvc.perform(get("/api/admin/confirmations/" + confirmId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = bodyOf(detail);
        assertThat(body.path("isOverLimit").asBoolean()).isTrue();
        assertThat(body.path("items").size()).isEqualTo(1);
        assertThat(body.path("items").get(0).path("dishName").asText()).isEqualTo("番茄炒蛋");

        mockMvc.perform(post("/api/admin/confirmations/" + confirmId + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"decision\":\"FOLLOW_UP\",\"note\":\"额度配置异常，已联系家庭调整\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(66.00));

        // 复核仅落审计：可查到 CONFIRM_ADMIN_REVIEW
        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("action", "CONFIRM_ADMIN_REVIEW")
                        .param("targetType", "CONFIRM")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber());

        // 非超额确认单 → 400
        long normalId = seedConfirm(childId, familyId, false);
        mockMvc.perform(post("/api/admin/confirmations/" + normalId + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"decision\":\"RESOLVED\",\"note\":\"不需要\"}"))
                .andExpect(status().isBadRequest());

        // 非法结论 → 400
        mockMvc.perform(post("/api/admin/confirmations/" + confirmId + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"decision\":\"WHATEVER\",\"note\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("审批记录/钱包/流水：列表回显关联单号与额度规则")
    void approvalsWalletsAndLogs() throws Exception {
        String token = adminLogin();
        long familyId = seedFamily();
        long childId = seedChild("孙小悟");
        long confirmId = seedConfirm(childId, familyId, true);
        long approvalId = seedApproval(confirmId);
        long walletId = seedWallet(childId, familyId);
        seedAllowanceRule(childId, familyId);
        seedAllowanceLog(walletId, childId, familyId);

        var approvals = mockMvc.perform(get("/api/admin/confirm-approvals")
                        .param("confirmId", String.valueOf(confirmId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn();
        JsonNode approval = bodyOf(approvals).path("items").get(0);
        assertThat(approval.path("id").asLong()).isEqualTo(approvalId);
        assertThat(approval.path("confirmNo").asText()).isNotEmpty();

        var walletList = mockMvc.perform(get("/api/admin/wallets")
                        .param("familyId", String.valueOf(familyId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn();
        JsonNode wallet = bodyOf(walletList).path("items").get(0);
        assertThat(wallet.path("balance").doubleValue()).isEqualTo(88.00);
        assertThat(wallet.path("dailyLimit").doubleValue()).isEqualTo(30.00);

        mockMvc.perform(get("/api/admin/allowance-logs")
                        .param("childId", String.valueOf(childId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].balanceAfter").value(88.00));
    }

    @Test
    @DisplayName("家务健康：完成率按全量口径计算；打卡记录含快照项名")
    void choreSummaryAndCheckRecords() throws Exception {
        String token = adminLogin();
        long familyId = seedFamily();
        long childId = seedChild("周小茹");
        seedChoreInstance(familyId, childId, "CLAIMED");
        seedChoreInstance(familyId, childId, "CONFIRMED");

        var chores = mockMvc.perform(get("/api/admin/chore-instances")
                        .param("familyId", String.valueOf(familyId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        JsonNode summary = bodyOf(chores);
        assertThat(summary.path("statusCounts").path("CONFIRMED").asLong()).isEqualTo(1);
        assertThat(summary.path("completionRate").asDouble()).isEqualTo(50.0);

        long itemId = seedCheckItem(familyId, "刷牙");
        seedCheckRecord(familyId, childId, itemId, "刷牙");
        mockMvc.perform(get("/api/admin/check-records")
                        .param("familyId", String.valueOf(familyId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].itemName").value("刷牙"))
                .andExpect(jsonPath("$.data.items[0].childName").value("周*"));
    }

    @Test
    @DisplayName("勋章补发：幂等（同 ref_id 二次补发返回同一行）；缺原因 400")
    void medalReissueIdempotent() throws Exception {
        String token = adminLogin();
        long familyId = seedFamily();
        long childId = seedChild("钱小进");
        seedWallet(childId, familyId);
        String code = "IT_REISSUE_" + System.nanoTime();
        seedMedalDefinition(code);

        var first = mockMvc.perform(post("/api/admin/medal-awards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"childId\":" + childId + ",\"code\":\"" + code
                                + "\",\"reason\":\"活动数据修复补发\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refId").value(0))
                .andReturn();
        long awardId = bodyOf(first).path("id").asLong();

        // 幂等：同一 (definition, child, refId=0) 再次补发返回同一行
        mockMvc.perform(post("/api/admin/medal-awards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"childId\":" + childId + ",\"code\":\"" + code
                                + "\",\"reason\":\"重复请求\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(awardId));

        // 缺原因 → 400（Bean Validation）
        mockMvc.perform(post("/api/admin/medal-awards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"childId\":" + childId + ",\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest());

        // 列表可查（勋章名回显 + 脱敏）
        mockMvc.perform(get("/api/admin/medal-awards")
                        .param("childId", String.valueOf(childId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].medalCode").value(code));
    }

    @Test
    @DisplayName("CSV 导出：want-eat 域返回 BOM CSV；未知域 400")
    void bizExportCsv() throws Exception {
        String token = adminLogin();
        long familyId = seedFamily();
        long childId = seedChild("李小龙");
        long categoryId = seedCategory("IT导出分类-" + System.nanoTime());
        long dishId = seedFamilyDish(familyId, categoryId, ".export 糖醋排骨");
        seedWantEat(familyId, childId, "FAMILY", dishId);

        var resp = mockMvc.perform(post("/api/admin/export/want-eat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"familyId\":" + familyId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        String csv = resp.getResponse().getContentAsString();
        assertThat(csv).contains("孩子").contains("李*").contains("菜单日期");

        mockMvc.perform(post("/api/admin/export/no-such-domain")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("RBAC：OP 可查业务数据并导出；RA 仅审批记录可见、其余 403")
    void roleBasedAccess() throws Exception {
        String saToken = adminLogin();
        String opToken = roleLogin(saToken, "OP");
        String raToken = roleLogin(saToken, "RA");

        mockMvc.perform(get("/api/admin/want-eat")
                        .header("Authorization", "Bearer " + opToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/export/want-eat")
                        .header("Authorization", "Bearer " + opToken)
                        .contentType(JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // RA：审批记录 view（M4 补播）可见
        mockMvc.perform(get("/api/admin/confirm-approvals")
                        .header("Authorization", "Bearer " + raToken))
                .andExpect(status().isOk());
        // RA：想吃与勋章补发均 403
        mockMvc.perform(get("/api/admin/want-eat")
                        .header("Authorization", "Bearer " + raToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/medal-awards")
                        .header("Authorization", "Bearer " + raToken)
                        .contentType(JSON)
                        .content("{\"childId\":1,\"code\":\"X\",\"reason\":\"越权\"}"))
                .andExpect(status().isForbidden());
    }
}
