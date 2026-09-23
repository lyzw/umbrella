package cn.studykid.growthplanet;

import cn.studykid.growthplanet.dto.response.AdminLoginResp;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M1 运营看板与报表集成测试（里程碑 A 批次 5）：
 * 5 张看板接口、报表导出（OP 成功 / CR 越权 403）。
 */
class AdminDashboardIT extends BaseIT {

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

    /** 播种家庭/孩子/家庭菜/想吃记录，使聚合 SQL（含想吃 Top 名称补齐）真正执行。 */
    private void seedBizData() {
        long familyId = insertReturningId(
                "INSERT INTO usr_family (family_name, owner_user_id) VALUES ('IT看板家庭', 99901)");
        long userId = insertReturningId(
                "INSERT INTO usr_user (openid, role, nickname) VALUES ('it-openid-"
                        + System.nanoTime() + "', 'CHILD', '看板娃')");
        long childId = insertReturningId("INSERT INTO usr_child_profile (user_id, family_id, nickname, profile_status) VALUES ("
                + userId + ", " + familyId + ", '看板娃', 'COMPLETE')");
        jdbc.update("INSERT IGNORE INTO life_dish_category (id, name) VALUES (99001, '测试分类')");
        long dishId = insertReturningId(
                "INSERT INTO life_family_dish (family_id, category_id, name, spice_level, visibility, status, review_status) VALUES ("
                        + familyId + ", 99001, '测试红烧肉', 0, 'PUBLIC', 'ON_SALE', 'APPROVED')");
        // 每日菜单（FAMILY 来源）：确认单通过 menu_id 关联，用于验证来源占比聚合 SQL。
        long dailyId = insertReturningId(
                "INSERT INTO life_menu_daily (source_type, owner_key, family_id, menu_date, meal_type, dish_ids, status) VALUES ("
                        + "'FAMILY', '" + familyId + "', " + familyId + ", '2026-09-20', 'LUNCH', '[1]', 'PUBLISHED')");
        jdbc.update("INSERT INTO usr_child_want_eat (child_id, family_id, menu_date, meal_type, "
                + "source_type, dish_type, dish_id, status) VALUES (" + childId + ", " + familyId
                + ", '2026-09-20', 'LUNCH', 'FAMILY', 'FAMILY', " + dishId + ", 'ACTIVE')");
        String cfSuffix = Long.toHexString(System.nanoTime());
        jdbc.update("INSERT INTO life_menu_confirm (child_id, family_id, confirm_no, menu_id, menu_date, meal_type, "
                + "total_amount, request_key, request_hash, estimated_balance, submit_time, status) VALUES ("
                + childId + ", " + familyId + ", 'CF-IT-" + cfSuffix + "', " + dailyId + ", '2026-09-20', 'LUNCH', 1000, 'rk-it-"
                + cfSuffix + "', 'hash-it-" + cfSuffix + "', 0, '2026-09-20 12:00:00', 'PENDING')");
        jdbc.update("INSERT INTO life_chore_instance (task_id, family_id, child_id, claim_date, status) VALUES ("
                + "1, " + familyId + ", " + childId + ", '2026-09-20', 'CONFIRMED')");
        jdbc.update("INSERT IGNORE INTO life_check_item (id, family_id, name) VALUES (99001, " + familyId + ", '刷牙')");
        jdbc.update("INSERT INTO life_check_record (family_id, child_id, item_id, item_name, check_date, check_time) VALUES ("
                + familyId + ", " + childId + ", 99001, '刷牙', '2026-09-20', '2026-09-20 08:00:00')");
    }

    @Test
    @DisplayName("运营看板：OP 可访问全部 5 张看板，想吃 Top 带家庭菜名称")
    void dashboardsForOp() throws Exception {
        String saToken = adminLogin();
        String opToken = roleLogin(saToken, "OP");
        seedBizData();

        mockMvc.perform(get("/api/admin/dashboard/overview").header("Authorization", "Bearer " + opToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.familiesTotal").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        var meals = mockMvc.perform(get("/api/admin/dashboard/meals").header("Authorization", "Bearer " + opToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wantEatTop[0].dishName").value("测试红烧肉"))
                .andExpect(jsonPath("$.data.sourceRatio").isArray())
                .andReturn();
        assertThat(meals.getResponse().getContentAsString()).contains("测试红烧肉");

        mockMvc.perform(get("/api/admin/dashboard/allowance").header("Authorization", "Bearer " + opToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/dashboard/chores").header("Authorization", "Bearer " + opToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.streakDistribution").isArray());
        mockMvc.perform(get("/api/admin/dashboard/medals").header("Authorization", "Bearer " + opToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topMedals").isArray());
    }

    @Test
    @DisplayName("报表导出：OP 有权导出（text/csv + BOM），CR 无权限 403")
    void reportExportPerm() throws Exception {
        String saToken = adminLogin();
        String opToken = roleLogin(saToken, "OP");
        String crToken = roleLogin(saToken, "CR");
        seedBizData();

        mockMvc.perform(post("/api/admin/reports/export?domain=overview")
                        .header("Authorization", "Bearer " + opToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("﻿")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("家庭总数")));

        mockMvc.perform(post("/api/admin/reports/export?domain=overview")
                        .header("Authorization", "Bearer " + crToken))
                .andExpect(status().isForbidden());
    }
}
