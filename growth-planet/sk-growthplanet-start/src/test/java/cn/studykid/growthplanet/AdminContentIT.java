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
    @DisplayName("菜品参考字典下发过敏原发布目录；E-400 透出具体原因")
    void dishReferencesAndErrorDetail() throws Exception {
        String token = adminLogin();
        long categoryId = seedCategory("IT字典分类-" + System.nanoTime());

        // 字典来自 compliance.allergens（test.yml = PEANUT/MILK/EGG），前端不得硬编码
        mockMvc.perform(get("/api/admin/dish-references")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.catalogReady").value(true))
                .andExpect(jsonPath("$.data.allergens.length()").value(3))
                .andExpect(jsonPath("$.data.allergens[0]").value("PEANUT"));

        // R5-c 被拒时 message 必须带具体原因，而不是笼统的「参数不合法」
        mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"未声明菜\",\"virtualPrice\":9.00,"
                                + "\"spiceLevel\":1,\"status\":\"ON_SALE\",\"allergens\":[],"
                                + "\"allergenStatus\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("参数不合法：上架菜品必须先声明过敏原"));

        // 目录外的过敏原取值同样给出可定位提示
        mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"越界过敏原菜\",\"virtualPrice\":9.00,"
                                + "\"spiceLevel\":1,\"status\":\"OFF_SALE\",\"allergens\":[\"SHRIMP\"],"
                                + "\"allergenStatus\":\"DECLARED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-400"))
                .andExpect(jsonPath("$.message").value("参数不合法：过敏原取值不在发布目录内，或存在空值 / 重复值 / 超长值"));

        // 分类不存在同样可定位
        mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"categoryId\":99999999,\"name\":\"无分类菜\",\"virtualPrice\":9.00,"
                                + "\"spiceLevel\":1,\"status\":\"OFF_SALE\",\"allergens\":[],"
                                + "\"allergenStatus\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("参数不合法：菜品分类不存在或已停用"));
    }

    @Test
    @DisplayName("菜品配方：六字段往返一致、顺序保持；列表只带摘要且 recipe=null")
    void dishRecipeRoundTrip() throws Exception {
        String token = adminLogin();
        long categoryId = seedCategory("IT配方分类-" + System.nanoTime());
        String name = "IT辣椒炒肉-" + System.nanoTime();

        var created = mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"" + name + "\","
                                + "\"virtualPrice\":12.00,\"spiceLevel\":3,\"status\":\"OFF_SALE\","
                                + "\"allergens\":[],\"allergenStatus\":\"UNKNOWN\","
                                + "\"ingredients\":[{\"name\":\"猪肉\",\"amount\":\"200g\"},"
                                + "{\"name\":\"青椒\",\"amount\":\"3 个\"},{\"name\":\"蒜\"}],"
                                + "\"cookSteps\":[\"猪肉切薄片腌制 10 分钟\",\"热锅冷油爆香蒜片\"],"
                                + "\"cookTips\":\"给孩子吃可以少放辣椒。\","
                                + "\"cookMinutes\":20,\"servings\":2,\"difficulty\":\"EASY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ingredientCount").value(3))
                .andExpect(jsonPath("$.data.stepCount").value(2))
                .andExpect(jsonPath("$.data.cookMinutes").value(20))
                .andExpect(jsonPath("$.data.servings").value(2))
                .andExpect(jsonPath("$.data.difficulty").value("EASY"))
                // 顺序必须与请求数组下标一致（sort 0,1,2）
                .andExpect(jsonPath("$.data.recipe.ingredients[0].name").value("猪肉"))
                .andExpect(jsonPath("$.data.recipe.ingredients[1].name").value("青椒"))
                .andExpect(jsonPath("$.data.recipe.ingredients[2].name").value("蒜"))
                .andExpect(jsonPath("$.data.recipe.ingredients[2].amount").doesNotExist())
                .andExpect(jsonPath("$.data.recipe.cookSteps[0]").value("猪肉切薄片腌制 10 分钟"))
                .andExpect(jsonPath("$.data.recipe.cookSteps[1]").value("热锅冷油爆香蒜片"))
                .andExpect(jsonPath("$.data.recipe.cookTips").value("给孩子吃可以少放辣椒。"))
                .andReturn();
        long dishId = bodyOf(created).path("dishId").asLong();

        // 详情：配方完整（编辑抽屉的数据来源）
        mockMvc.perform(get("/api/admin/dishes/" + dishId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipe.ingredients.length()").value(3))
                .andExpect(jsonPath("$.data.recipe.cookSteps.length()").value(2));

        // 列表：recipe 不携带（null），但摘要计数与标量要平铺出来
        mockMvc.perform(get("/api/admin/dishes").param("keyword", name)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].recipe").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].ingredientCount").value(3))
                .andExpect(jsonPath("$.data.items[0].stepCount").value(2))
                .andExpect(jsonPath("$.data.items[0].difficulty").value("EASY"));
    }

    @Test
    @DisplayName("菜品配方清空：更新为空 → 食材行软删、做法清空、标量置 null")
    void dishRecipeClear() throws Exception {
        String token = adminLogin();
        long categoryId = seedCategory("IT清空分类-" + System.nanoTime());
        String name = "IT清空配方菜-" + System.nanoTime();

        var created = mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"" + name + "\","
                                + "\"virtualPrice\":12.00,\"spiceLevel\":1,\"status\":\"OFF_SALE\","
                                + "\"allergens\":[],\"allergenStatus\":\"UNKNOWN\","
                                + "\"ingredients\":[{\"name\":\"土豆\",\"amount\":\"2 个\"}],"
                                + "\"cookSteps\":[\"削皮切丝\"],\"cookTips\":\"少油\","
                                + "\"cookMinutes\":15,\"servings\":2,\"difficulty\":\"EASY\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long dishId = bodyOf(created).path("dishId").asLong();

        // 传空数组 / null 即清空：这是 updateStrategy=ALWAYS 的验收点（漏配则旧值会残留）
        mockMvc.perform(put("/api/admin/dishes/" + dishId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"" + name + "\","
                                + "\"virtualPrice\":12.00,\"spiceLevel\":1,\"status\":\"OFF_SALE\","
                                + "\"allergens\":[],\"allergenStatus\":\"UNKNOWN\","
                                + "\"ingredients\":[],\"cookSteps\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ingredientCount").value(0))
                .andExpect(jsonPath("$.data.stepCount").value(0))
                .andExpect(jsonPath("$.data.cookMinutes").doesNotExist())
                .andExpect(jsonPath("$.data.servings").doesNotExist())
                .andExpect(jsonPath("$.data.recipe.ingredients.length()").value(0))
                .andExpect(jsonPath("$.data.recipe.cookSteps.length()").value(0))
                .andExpect(jsonPath("$.data.recipe.cookTips").doesNotExist());

        // 详情二次确认：清空真的落库，而不只是响应层为空
        mockMvc.perform(get("/api/admin/dishes/" + dishId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipe.ingredients.length()").value(0))
                .andExpect(jsonPath("$.data.recipe.cookTips").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM life_dish_ingredient "
                + "WHERE owner_type='PRESET' AND dish_id=? AND delete_at=0", Integer.class, dishId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM life_dish_ingredient "
                + "WHERE owner_type='PRESET' AND dish_id=? AND delete_at>0", Integer.class, dishId)).isEqualTo(1);
    }

    @Test
    @DisplayName("菜品配方校验：条数/重复/空步/时长越界/难度非法 → 各自 400 且原因可定位")
    void dishRecipeValidation() throws Exception {
        String token = adminLogin();
        long categoryId = seedCategory("IT配方校验分类-" + System.nanoTime());
        String base = "{\"categoryId\":" + categoryId + ",\"name\":\"IT配方校验菜-" + System.nanoTime()
                + "\",\"virtualPrice\":9.00,\"spiceLevel\":1,\"status\":\"OFF_SALE\","
                + "\"allergens\":[],\"allergenStatus\":\"UNKNOWN\"";

        StringBuilder tooMany = new StringBuilder();
        for (int i = 0; i < 31; i++) {
            tooMany.append(i == 0 ? "" : ",").append("{\"name\":\"食材").append(i).append("\"}");
        }
        assertRecipeRejected(token, base, "\"ingredients\":[" + tooMany + "]",
                "参数不合法：食材最多 30 条");
        // 去空白后同名（含大小写差异）即重复
        assertRecipeRejected(token, base, "\"ingredients\":[{\"name\":\"猪肉\"},{\"name\":\" 猪肉 \"}]",
                "参数不合法：食材名称重复：猪肉");
        assertRecipeRejected(token, base, "\"cookSteps\":[\"切菜\",\"  \"]",
                "参数不合法：第 2 步做法不能为空");
        assertRecipeRejected(token, base, "\"cookMinutes\":1441",
                "参数不合法：烹饪时长需在 1 至 1440 分钟之间");
        assertRecipeRejected(token, base, "\"difficulty\":\"EXPERT\"",
                "参数不合法：难度取值不合法");
    }

    @Test
    @DisplayName("家庭菜品配方归属：只落 FAMILY 行；跨家庭读配方 404；菜品软删级联软删食材")
    void familyDishRecipeOwnership() throws Exception {
        String token = adminLogin();
        long categoryId = seedCategory("IT家庭配方分类-" + System.nanoTime());
        var owner = setupFamily();
        var other = setupFamily();

        var created = mockMvc.perform(post("/api/mini/parent/family-dish")
                        .header("Authorization", "Bearer " + owner.parentToken())
                        .contentType(JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"IT家庭番茄蛋\","
                                + "\"virtualPrice\":8.00,\"spiceLevel\":0,\"allergens\":[],"
                                + "\"allergenStatus\":\"UNKNOWN\","
                                + "\"ingredients\":[{\"name\":\"番茄\",\"amount\":\"2 个\"},"
                                + "{\"name\":\"鸡蛋\",\"amount\":\"3 个\"}],"
                                + "\"cookSteps\":[\"番茄切块\",\"鸡蛋打散炒熟\",\"合炒调味\"],"
                                + "\"cookTips\":\"不放味精。\","
                                + "\"cookMinutes\":10,\"servings\":2,\"difficulty\":\"EASY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ingredientCount").value(2))
                .andExpect(jsonPath("$.data.stepCount").value(3))
                .andReturn();
        long dishId = bodyOf(created).path("dishId").asLong();

        // 食材只落 FAMILY 行：owner_type 由代码路径固定，绝不接受请求参数。
        // 注意 (owner_type, dish_id) 才是完整键 —— life_dish 与 life_family_dish 自增序列独立，
        // 单看 dish_id 会跨表撞号（既有 DishRef 教训），故按本次写入的食材名定位再断言 owner_type。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM life_dish_ingredient "
                + "WHERE owner_type='FAMILY' AND dish_id=? AND delete_at=0", Integer.class, dishId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM life_dish_ingredient "
                + "WHERE dish_id=? AND name IN ('番茄','鸡蛋') AND owner_type='FAMILY'", Integer.class, dishId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM life_dish_ingredient "
                + "WHERE dish_id=? AND name IN ('番茄','鸡蛋') AND owner_type<>'FAMILY'", Integer.class, dishId))
                .isZero();

        // 本家庭家长可读
        mockMvc.perform(get("/api/mini/dishes/FAMILY/" + dishId + "/recipe")
                        .header("Authorization", "Bearer " + owner.parentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ingredients[0].name").value("番茄"))
                .andExpect(jsonPath("$.data.ingredients[1].amount").value("3 个"))
                .andExpect(jsonPath("$.data.cookSteps.length()").value(3))
                .andExpect(jsonPath("$.data.difficulty").value("EASY"));

        // 孩子也能读本家庭菜品配方（「孩子想吃」页看做法）
        grant(owner);
        approve(owner);
        mockMvc.perform(get("/api/mini/dishes/FAMILY/" + dishId + "/recipe")
                        .header("Authorization", "Bearer " + owner.childToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ingredients.length()").value(2));

        // 跨家庭读：按「不存在」处理，不泄露「这盘菜存在但不属于你」
        mockMvc.perform(get("/api/mini/dishes/FAMILY/" + dishId + "/recipe")
                        .header("Authorization", "Bearer " + other.parentToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("E-404"));

        // 下架的预置菜品 → 404（沿用既有可见性规则，不额外放宽）
        var preset = mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"IT预置配方菜-" + System.nanoTime()
                                + "\",\"virtualPrice\":9.00,\"spiceLevel\":0,\"status\":\"OFF_SALE\","
                                + "\"allergens\":[],\"allergenStatus\":\"UNKNOWN\","
                                + "\"cookSteps\":[\"一步搞定\"],\"cookMinutes\":5}"))
                .andExpect(status().isOk())
                .andReturn();
        long presetId = bodyOf(preset).path("dishId").asLong();
        mockMvc.perform(get("/api/mini/dishes/PRESET/" + presetId + "/recipe")
                        .header("Authorization", "Bearer " + owner.parentToken()))
                .andExpect(status().isNotFound());

        // type 只接受 PRESET / FAMILY
        mockMvc.perform(get("/api/mini/dishes/SCHOOL/" + presetId + "/recipe")
                        .header("Authorization", "Bearer " + owner.parentToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-400"));

        // 家庭菜品软删后食材行同步软删（避免悬挂数据）
        mockMvc.perform(delete("/api/mini/parent/family-dish/" + dishId)
                        .param("expectedVersion", "0")
                        .header("Authorization", "Bearer " + owner.parentToken()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM life_dish_ingredient "
                + "WHERE owner_type='FAMILY' AND dish_id=? AND delete_at=0", Integer.class, dishId)).isZero();
    }

    /** 断言某条配方字段被拒时的 E-400 与可定位原因（避免笼统的「参数不合法」）。 */
    private void assertRecipeRejected(String token, String base, String recipeField, String expectedMessage)
            throws Exception {
        mockMvc.perform(post("/api/admin/dishes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(base + "," + recipeField + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-400"))
                .andExpect(jsonPath("$.message").value(expectedMessage));
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

        mockMvc.perform(get("/api/admin/dish-references")
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
