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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3 内容管理集成测试（里程碑 A 批次 2）：
 * 菜品/分类/校餐菜单/任务库/勋章/心愿配置/UGC 审核队列 + 细粒度权限（RA 只读 403）。
 */
class AdminContentIT extends BaseIT {

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

    /** 执行 INSERT 并返回自增主键（MySQL 不支持 RETURNING，用 GeneratedKeyHolder 回填）。 */
    private long insertReturningId(String sql) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("INSERT 未返回自增主键: " + sql);
        }
        return key.longValue();
    }

    /** 直插一个家庭（外键需要），返回 familyId。 */
    private long seedFamily() {
        return insertReturningId(
                "INSERT INTO usr_family (family_name, owner_user_id) VALUES ('IT内容家庭', 99901)");
    }

    /** 直插分类，返回 categoryId。 */
    private long seedCategory(String name) {
        return insertReturningId(
                "INSERT INTO life_dish_category (name, sort, status) VALUES ('" + name + "', 0, 'ENABLED')");
    }

    /** 直插家庭私有菜品（visibility=PRIVATE, review_status=NONE, version=0）。 */
    private long seedFamilyDish(long familyId, long categoryId, String name) {
        return insertReturningId(
                "INSERT INTO life_family_dish (family_id, category_id, name, virtual_price, spice_level) "
                        + "VALUES (" + familyId + ", " + categoryId + ", '" + name + "', 12.50, 1)");
    }

    private JsonNode bodyOf(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return MAPPER.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    @DisplayName("菜品全生命周期：创建→上下架流转；上架未声明过敏原被拒")
    void dishLifecycle() throws Exception {
        String token = adminLogin();
        long categoryId = seedCategory("IT菜品分类-" + System.nanoTime());

        // ON_SALE 但未声明过敏原 → 400（R5-c 红线）
        mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"红烧肉\",\"virtualPrice\":15.00,"
                                + "\"spiceLevel\":2,\"status\":\"ON_SALE\",\"allergens\":[],"
                                + "\"allergenStatus\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());

        // 以 OFF_SALE 草稿创建 → 上下架流转
        var created = mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"清蒸鱼\",\"virtualPrice\":18.00,"
                                + "\"spiceLevel\":0,\"status\":\"OFF_SALE\",\"allergens\":[\"MILK\"],"
                                + "\"allergenStatus\":\"DECLARED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long dishId = bodyOf(created).path("dishId").asLong();

        mockMvc.perform(put("/api/admin/dishes/" + dishId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"status\":\"OFF_SALE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"));
        mockMvc.perform(put("/api/admin/dishes/" + dishId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"status\":\"ON_SALE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }

    @Test
    @DisplayName("分类生命周期：创建/更新/删除；被菜品引用时拒绝删除")
    void categoryLifecycleAndUsedGuard() throws Exception {
        String token = adminLogin();
        var created = mockMvc.perform(post("/api/admin/dish-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"name\":\"IT引用分类\",\"sort\":9,\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long categoryId = bodyOf(created).path("categoryId").asLong();

        mockMvc.perform(put("/api/admin/dish-categories/" + categoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"name\":\"IT引用分类-改\",\"sort\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("IT引用分类-改"));

        // 建一个引用该分类的菜品 → 删除分类必须被拒
        mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"引用菜\",\"virtualPrice\":5.00,"
                                + "\"spiceLevel\":0,\"status\":\"OFF_SALE\",\"allergens\":[],"
                                + "\"allergenStatus\":\"UNKNOWN\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/dish-categories/" + categoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-400"));

        // 未被引用的分类可删
        long freeCategoryId = seedCategory("IT空闲分类-" + System.nanoTime());
        mockMvc.perform(delete("/api/admin/dish-categories/" + freeCategoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("校餐菜单：发布学校菜单并在列表中可见；校名不在发布目录被拒")
    void schoolMenuUpsertAndList() throws Exception {
        String token = adminLogin();
        long categoryId = seedCategory("IT校餐分类-" + System.nanoTime());
        var dish = mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"校园餐A\",\"virtualPrice\":10.00,"
                                + "\"spiceLevel\":0,\"status\":\"ON_SALE\",\"allergens\":[],"
                                + "\"allergenStatus\":\"DECLARED\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long dishId = bodyOf(dish).path("dishId").asLong();

        // 不在发布目录的校名 → 400
        mockMvc.perform(post("/api/admin/menus/school")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"school\":\"不存在小学\",\"menuDate\":\"2026-09-23\",\"mealType\":\"LUNCH\","
                                + "\"dishIds\":[{\"type\":\"PRESET\",\"id\":" + dishId + "}],\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/menus/school")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"school\":\"合成学校\",\"menuDate\":\"2026-09-23\",\"mealType\":\"LUNCH\","
                                + "\"dishIds\":[{\"type\":\"PRESET\",\"id\":" + dishId + "}],\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/menus/school")
                        .param("school", "合成学校")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    @DisplayName("UGC 审核队列：通过→PUBLIC+APPROVED；驳回→REJECTED+原因留痕")
    void ugcQueueApproveAndReject() throws Exception {
        String token = adminLogin();
        long familyId = seedFamily();
        long categoryId = seedCategory("ITUGC分类-" + System.nanoTime());
        long approveId = seedFamilyDish(familyId, categoryId, "家庭红烧肉");
        long rejectId = seedFamilyDish(familyId, categoryId, "家庭刺身");

        // 队列可见（默认 PRIVATE，全量家庭视图）
        var queue = mockMvc.perform(get("/api/admin/ugc/queue")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = bodyOf(queue);
        assertThat(data.path("total").asLong()).isGreaterThanOrEqualTo(2);

        // 通过
        mockMvc.perform(post("/api/admin/ugc/" + approveId + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"action\":\"APPROVE\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"));

        // 驳回（无原因 → 400；有原因 → REJECTED）
        mockMvc.perform(post("/api/admin/ugc/" + rejectId + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"action\":\"REJECT\",\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/ugc/" + rejectId + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"action\":\"REJECT\",\"reason\":\"含生食过敏风险\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.reviewStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("含生食过敏风险"));
    }

    @Test
    @DisplayName("UGC 审核版本冲突返回 409/E-007")
    void ugcReviewVersionConflict() throws Exception {
        String token = adminLogin();
        long familyId = seedFamily();
        long categoryId = seedCategory("IT冲突分类-" + System.nanoTime());
        long dishId = seedFamilyDish(familyId, categoryId, "冲突家庭菜");

        mockMvc.perform(post("/api/admin/ugc/" + dishId + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"action\":\"APPROVE\",\"expectedVersion\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E-007"));
    }

    @Test
    @DisplayName("勋章配置：创建/重复 code 拒绝/列表含发放计数")
    void medalCreateDuplicateAndList() throws Exception {
        String token = adminLogin();
        mockMvc.perform(post("/api/admin/medals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"code\":\"IT_MEDAL_" + System.nanoTime() + "\",\"name\":\"IT勋章A\","
                                + "\"category\":\"MEAL\",\"conditionType\":\"COUNT\",\"threshold\":5,\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").isNotEmpty());

        mockMvc.perform(get("/api/admin/medals")
                        .param("status", "NORMAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    @DisplayName("心愿菜单配置：默认值回显→乐观锁更新→旧版本冲突")
    void wishConfigGetUpdateConflict() throws Exception {
        String token = adminLogin();
        long familyId = seedFamily();

        var before = mockMvc.perform(get("/api/admin/wish-menu-config/" + familyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode config = bodyOf(before);
        assertThat(config.path("wishMenuEnabled").asInt()).isEqualTo(1);
        int version = config.path("version").asInt();

        mockMvc.perform(put("/api/admin/wish-menu-config/" + familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"wishMenuEnabled\":1,\"wishMenuMaxDishes\":8,\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wishMenuMaxDishes").value(8))
                .andExpect(jsonPath("$.data.version").value(version + 1));

        // 旧版本再写 → E007
        mockMvc.perform(put("/api/admin/wish-menu-config/" + familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"wishMenuEnabled\":0,\"wishMenuMaxDishes\":5,\"expectedVersion\":" + version + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E-007"));
    }

    @Test
    @DisplayName("任务库：代家庭创建/启停治理/按状态筛选")
    void choreTaskGovernance() throws Exception {
        String token = adminLogin();
        long familyId = seedFamily();

        var created = mockMvc.perform(post("/api/admin/chore-tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"familyId\":" + familyId + ",\"title\":\"IT整理书桌\",\"cycle\":\"DAILY\","
                                + "\"rewardAmount\":2.00,\"estimatedMinutes\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NORMAL"))
                .andReturn();
        long taskId = bodyOf(created).path("id").asLong();

        mockMvc.perform(put("/api/admin/chore-tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/admin/chore-tasks")
                        .param("familyId", String.valueOf(familyId))
                        .param("status", "ARCHIVED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    @DisplayName("RA 只读审计员无菜品权限：查看与写入均 403")
    void raCannotAccessDishEndpoints() throws Exception {
        String saToken = adminLogin();
        String raToken = roleLogin(saToken, "RA");
        long categoryId = seedCategory("ITRA分类-" + System.nanoTime());

        mockMvc.perform(get("/api/admin/dishes")
                        .header("Authorization", "Bearer " + raToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + raToken)
                        .contentType(JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"RA越权菜\",\"virtualPrice\":1.00,"
                                + "\"spiceLevel\":0,\"status\":\"OFF_SALE\",\"allergens\":[],"
                                + "\"allergenStatus\":\"UNKNOWN\"}"))
                .andExpect(status().isForbidden());
    }
}
