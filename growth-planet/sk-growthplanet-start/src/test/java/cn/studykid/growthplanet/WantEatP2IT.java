package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.entity.ChildProfile;
import cn.studykid.growthplanet.entity.ChildWantEat;
import cn.studykid.growthplanet.entity.Dish;
import cn.studykid.growthplanet.entity.MedalAward;
import cn.studykid.growthplanet.entity.MedalDefinition;
import cn.studykid.growthplanet.entity.MenuDaily;
import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.mapper.ChildProfileMapper;
import cn.studykid.growthplanet.mapper.ChildWantEatMapper;
import cn.studykid.growthplanet.mapper.DishMapper;
import cn.studykid.growthplanet.mapper.MedalAwardMapper;
import cn.studykid.growthplanet.mapper.MedalDefinitionMapper;
import cn.studykid.growthplanet.mapper.MenuDailyMapper;
import cn.studykid.growthplanet.mapper.UserMapper;
import cn.studykid.growthplanet.service.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2 集成测试：采纳反馈闭环（想吃被做 → 勋章）、「今天吃什么」推荐、周视图（家长/孩子）、批量发布，
 * 以及新增只读路径的 R6 并发回归（不得等待行锁）。
 */
class WantEatP2IT extends BaseIT {
    @Autowired UserMapper users;
    @Autowired DishMapper dishes;
    @Autowired MenuDailyMapper menus;
    @Autowired ChildProfileMapper profiles;
    @Autowired ChildWantEatMapper wantEats;
    @Autowired MedalDefinitionMapper medalDefs;
    @Autowired MedalAwardMapper medalAwards;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean BusinessTime time;

    // ---------- A. 采纳反馈闭环（接勋章） ----------

    @Test
    void cookedTransitionAwardsMedalOnceAndIsIdempotent() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "勋章菜", List.of(), "DECLARED", 0);
        LocalDate today = today();
        long menuId = familyMenu(ctx, List.of(dishId), today, "LUNCH");
        markFavorite(ctx, dishId, menuId, today, "LUNCH");
        long wantEatId = wantEatId(ctx, dishId);
        long defId = definitionId("MEAL_COOKED_1");
        assertEquals(0, awardCount(defId, childUserId(ctx)));

        // 仅"已采购"不发勋章：激励锚定在"家长真的做给孩子吃了"。
        transition(ctx.parentToken(), wantEatId, "ADOPTED", 0).andExpect(status().isOk());
        assertEquals(0, awardCount(defId, childUserId(ctx)));

        transition(ctx.parentToken(), wantEatId, "COOKED", 1).andExpect(status().isOk());
        assertEquals(1, awardCount(defId, childUserId(ctx)));

        // 幂等：回退再标 COOKED 不重复发（award 以 (definition, child, ref_id) 为幂等键）。
        transition(ctx.parentToken(), wantEatId, "MARKED", 2).andExpect(status().isOk());
        transition(ctx.parentToken(), wantEatId, "COOKED", 3).andExpect(status().isOk());
        assertEquals(1, awardCount(defId, childUserId(ctx)));

        // 勋章墙可见（前端平铺渲染，无需改动勋章页）。
        MvcResult medalResult = mockMvc.perform(get("/api/medal/awards")
                .header("Authorization", bearer(ctx.childToken()))
                .param("childId", childUserId(ctx).toString())).andExpect(status().isOk()).andReturn();
        JsonNode medals = objectMapper.readTree(medalResult.getResponse().getContentAsString()).get("data");
        JsonNode target = null;
        for (JsonNode medal : medals) {
            if ("MEAL_COOKED_1".equals(medal.get("definition").get("code").asString())) {
                target = medal;
            }
        }
        assertNotNull(target, "勋章墙应包含 MEAL_COOKED_1");
        assertTrue(target.get("earned").asBoolean());
    }

    @Test
    void tenCookedWishesUnlockCumulativeMedal() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        List<Long> dishIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            dishIds.add(dish(admin, categoryId, "心愿菜" + i, List.of(), "DECLARED", 0));
        }
        LocalDate today = today();
        long menuId = familyMenu(ctx, dishIds, today, "LUNCH");
        for (Long dishId : dishIds) {
            markFavorite(ctx, dishId, menuId, today, "LUNCH");
        }
        for (Long dishId : dishIds) {
            transition(ctx.parentToken(), wantEatId(ctx, dishId), "COOKED", 0).andExpect(status().isOk());
        }
        // 累计 10 次「已做」：MEAL_COOKED_1 仍只有 1 枚（幂等键含 ref_id=阈值，重复达成不重复发），
        // 达标 MEAL_COOKED_10 后补发 1 枚，阈值 30 未到不提前发放。
        assertEquals(1, awardCount(definitionId("MEAL_COOKED_1"), childUserId(ctx)));
        assertEquals(1, awardCount(definitionId("MEAL_COOKED_10"), childUserId(ctx)));
        assertEquals(0, awardCount(definitionId("MEAL_COOKED_30"), childUserId(ctx)));
    }

    // ---------- B. 「今天吃什么」推荐 ----------

    @Test
    void recommendExcludesAllergensAndDislikesAndRanksFrequent() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        // 档案（profileJson）：过敏 PEANUT、忌口「胡萝卜」、口味「清淡」
        long allergyDish = dish(admin, categoryId, "花生饼干", List.of("PEANUT"), "DECLARED", 0);
        long dislikeDish = dish(admin, categoryId, "胡萝卜炒蛋", List.of(), "DECLARED", 0);
        long frequentDish = dish(admin, categoryId, "常点菜", List.of(), "DECLARED", 0);
        long plainDish = dish(admin, categoryId, "清淡菜", List.of(), "DECLARED", 0);
        long spicyDish = dish(admin, categoryId, "重辣菜", List.of(), "DECLARED", 3);

        LocalDate today = today();
        long menuId = familyMenu(ctx, List.of(allergyDish, dislikeDish, frequentDish, plainDish, spicyDish),
                today, "LUNCH");
        // 常点：前两天各标记一次，形成历史加权。
        for (long daysAgo = 1; daysAgo <= 2; daysAgo++) {
            LocalDate date = today.minusDays(daysAgo);
            long past = familyMenu(ctx, List.of(frequentDish), date, "LUNCH");
            markFavorite(ctx, frequentDish, past, date, "LUNCH");
        }

        JsonNode data = dataJson(mockMvc.perform(get("/api/child/recommend")
                .header("Authorization", bearer(ctx.childToken())).param("menuId", Long.toString(menuId))));
        assertEquals(Long.toString(menuId), data.get("menuId").asString());
        JsonNode list = data.get("dishes");
        assertEquals(3, list.size(), "过敏与忌口菜品必须被剔除");

        List<String> names = new ArrayList<>();
        for (JsonNode item : list) {
            names.add(item.get("name").asString());
        }
        assertFalse(names.contains("花生饼干"), "含过敏原的菜品不得出现在推荐里");
        assertFalse(names.contains("胡萝卜炒蛋"), "忌口菜品不得出现在推荐里");
        // 常吃加权 → 排第一；重辣扣分 → 排最后。
        assertEquals("常点菜", names.get(0));
        assertEquals("重辣菜", names.get(2));
        assertTrue(list.get(0).get("reasons").size() > 0, "推荐应带可解释理由");
        assertTrue(list.get(0).get("score").asInt() > list.get(2).get("score").asInt());
    }

    @Test
    void recommendRejectsBadLimitForeignChildAndForeignMenu() throws Exception {
        var ctx = readyProfile();
        var other = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "边界菜", List.of(), "DECLARED", 0);
        LocalDate today = today();
        long menuId = familyMenu(ctx, List.of(dishId), today, "LUNCH");

        // limit<=0 取默认值 3（与 P1 /child/frequent-dish 的语义一致）；超过上限才 400。
        recommend(ctx.childToken(), menuId, null, 0).andExpect(status().isOk());
        recommend(ctx.childToken(), menuId, null, 6).andExpect(status().isBadRequest());
        recommend(ctx.childToken(), 99999999L, null, 3).andExpect(status().isNotFound());
        // 儿童传他人 childId → 403
        recommend(ctx.childToken(), menuId, childUserId(other), 3).andExpect(status().isForbidden());
        // 别家家长代查别家孩子 → 403
        recommend(other.parentToken(), menuId, childUserId(ctx), 3).andExpect(status().isForbidden());
        // 别家孩子看本家菜单 → 404（FAMILY 归属不符，不暴露存在性）
        recommend(other.childToken(), menuId, null, 3).andExpect(status().isNotFound());
    }

    // ---------- C1. 家长周视图 ----------

    @Test
    void parentWeekReturnsEveryDayAndMealWithoutDroppingSlots() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long first = dish(admin, categoryId, "周菜一", List.of(), "DECLARED", 0);
        long second = dish(admin, categoryId, "周菜二", List.of(), "DECLARED", 0);
        long third = dish(admin, categoryId, "周菜三", List.of(), "DECLARED", 0);

        LocalDate today = today();
        familyMenu(ctx, List.of(first, second), today, "LUNCH");
        familyMenu(ctx, List.of(third), today.plusDays(1), "DINNER");

        JsonNode data = dataJson(week(ctx.parentToken(), today, today.plusDays(6)));
        assertEquals(today.toString(), data.get("from").asString());
        assertEquals(7, data.get("days").size());
        for (JsonNode day : data.get("days")) {
            assertEquals(3, day.get("meals").size(), "每天都必须返回三餐格子（不丢格）");
        }

        JsonNode todayBreakfast = meal(data, 0, "BREAKFAST");
        assertTrue(todayBreakfast.get("menuId").isNull(), "未发布餐次应返回 null 而不是缺格");
        assertTrue(todayBreakfast.get("status").isNull());
        assertEquals(0, todayBreakfast.get("dishCount").asInt());
        assertEquals(0, todayBreakfast.get("wantEatCount").asInt());
        assertFalse(todayBreakfast.get("marked").asBoolean());

        JsonNode todayLunch = meal(data, 0, "LUNCH");
        assertFalse(todayLunch.get("menuId").isNull());
        assertEquals("PUBLISHED", todayLunch.get("status").asString());
        assertEquals("FAMILY", todayLunch.get("sourceType").asString());
        assertEquals(2, todayLunch.get("dishCount").asInt());

        assertEquals(1, meal(data, 1, "DINNER").get("dishCount").asInt());
        assertTrue(meal(data, 5, "LUNCH").get("menuId").isNull());
    }

    @Test
    void parentWeekRejectsBadRangesAndIsolatesOtherFamilies() throws Exception {
        var ctx = readyProfile();
        var other = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "隔离菜", List.of(), "DECLARED", 0);
        LocalDate today = today();
        familyMenu(ctx, List.of(dishId), today, "LUNCH");

        // 儿童 token 无权限
        week(ctx.childToken(), today, today).andExpect(status().isForbidden());
        // from > to
        week(ctx.parentToken(), today.plusDays(1), today).andExpect(status().isBadRequest());
        // 跨度超过 14 天
        week(ctx.parentToken(), today, today.plusDays(14)).andExpect(status().isBadRequest());
        // 缺参数
        mockMvc.perform(get("/api/parent/menu-week").header("Authorization", bearer(ctx.parentToken())))
                .andExpect(status().isBadRequest());
        // 跨家庭隔离：别家家长在同一格看到的是"未发布"，看不到本家菜单
        JsonNode otherWeek = dataJson(week(other.parentToken(), today, today));
        assertTrue(meal(otherWeek, 0, "LUNCH").get("menuId").isNull());
    }

    // ---------- C3. 孩子周视图 ----------

    @Test
    void childWeekReportsWantEatCountPerMeal() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long first = dish(admin, categoryId, "孩子的菜一", List.of(), "DECLARED", 0);
        long second = dish(admin, categoryId, "孩子的菜二", List.of(), "DECLARED", 0);
        LocalDate today = today();
        long menuId = familyMenu(ctx, List.of(first, second), today, "LUNCH");
        markFavorite(ctx, first, menuId, today, "LUNCH");

        JsonNode data = dataJson(mockMvc.perform(get("/api/child/menu-week")
                .header("Authorization", bearer(ctx.childToken()))
                .param("from", today.toString()).param("to", today.plusDays(6).toString())));
        JsonNode lunch = meal(data, 0, "LUNCH");
        assertEquals(Long.toString(menuId), lunch.get("menuId").asString());
        assertEquals(2, lunch.get("dishCount").asInt());
        assertEquals(1, lunch.get("wantEatCount").asInt());
        assertTrue(lunch.get("marked").asBoolean());
        JsonNode dinner = meal(data, 0, "DINNER");
        assertTrue(dinner.get("menuId").isNull());
        assertFalse(dinner.get("marked").asBoolean());

        // 家长可代查（须本家庭）
        data = dataJson(mockMvc.perform(get("/api/child/menu-week")
                .header("Authorization", bearer(ctx.parentToken())).param("childId", childUserId(ctx).toString())
                .param("from", today.toString()).param("to", today.toString())));
        assertEquals(1, meal(data, 0, "LUNCH").get("wantEatCount").asInt());

        // 儿童传他人 childId → 403
        var other = readyProfile();
        mockMvc.perform(get("/api/child/menu-week").header("Authorization", bearer(ctx.childToken()))
                .param("childId", childUserId(other).toString())
                .param("from", today.toString()).param("to", today.toString()))
                .andExpect(status().isForbidden());
    }

    // ---------- C2. 批量发布 ----------

    @Test
    void batchPublishPublishesWholeWeek() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "整周菜", List.of(), "DECLARED", 0);
        LocalDate today = today();

        List<Map<String, Object>> items = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            for (String mealType : List.of("BREAKFAST", "LUNCH", "DINNER")) {
                items.add(batchItem(today.plusDays(day), mealType, dishId));
            }
        }
        JsonNode data = dataJson(mockMvc.perform(post("/api/parent/menu-daily/batch")
                .header("Authorization", bearer(ctx.parentToken())).contentType(JSON)
                .content(json(Map.of("items", items)))));
        assertEquals(21, data.get("okCount").asInt());
        assertEquals(0, data.get("failCount").asInt());
        for (JsonNode result : data.get("results")) {
            assertTrue(result.get("ok").asBoolean());
        }
        assertEquals(21L, menus.selectCount(new QueryWrapper<MenuDaily>().eq("source_type", "FAMILY")
                .eq("owner_key", ctx.familyId().toString()).between("menu_date", today, today.plusDays(6))));

        // 周视图应看到整周都已发布
        JsonNode week = dataJson(week(ctx.parentToken(), today, today.plusDays(6)));
        for (JsonNode day : week.get("days")) {
            for (JsonNode meal : day.get("meals")) {
                assertFalse(meal.get("menuId").isNull());
                assertEquals(1, meal.get("dishCount").asInt());
            }
        }
    }

    @Test
    void batchPublishAllowsPartialSuccessAndRejectsOversizeBatch() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "部分成功菜", List.of(), "DECLARED", 0);
        LocalDate today = today();

        Map<String, Object> bad = batchItem(today.plusDays(1), "LUNCH", dishId);
        bad.put("dishIds", List.of(Map.of("type", "PRESET", "id", "99999999")));
        JsonNode data = dataJson(mockMvc.perform(post("/api/parent/menu-daily/batch")
                .header("Authorization", bearer(ctx.parentToken())).contentType(JSON)
                .content(json(Map.of("items", List.of(batchItem(today, "LUNCH", dishId), bad))))));
        assertEquals(1, data.get("okCount").asInt());
        assertEquals(1, data.get("failCount").asInt());
        assertTrue(data.get("results").get(0).get("ok").asBoolean());
        assertFalse(data.get("results").get(1).get("ok").asBoolean());
        assertEquals("E-404", data.get("results").get(1).get("code").asString());
        // 部分成功语义：失败那条不影响已成功的那条落库
        assertEquals(1L, menus.selectCount(new QueryWrapper<MenuDaily>().eq("source_type", "FAMILY")
                .eq("owner_key", ctx.familyId().toString()).between("menu_date", today, today.plusDays(1))));

        List<Map<String, Object>> oversize = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            oversize.add(batchItem(today, "LUNCH", dishId));
        }
        mockMvc.perform(post("/api/parent/menu-daily/batch").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(json(Map.of("items", oversize))))
                .andExpect(status().isBadRequest());
        // 儿童 token 无权限
        mockMvc.perform(post("/api/parent/menu-daily/batch").header("Authorization", bearer(ctx.childToken()))
                .contentType(JSON).content(json(Map.of("items", List.of(batchItem(today, "DINNER", dishId))))))
                .andExpect(status().isForbidden());
    }

    // ---------- R6 回归：新增只读路径不得等待行锁 ----------

    @Test
    @Timeout(60)
    void weekAndRecommendReadPathsDoNotWaitOnRowLocks() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "并读菜", List.of(), "DECLARED", 0);
        LocalDate today = today();
        long menuId = familyMenu(ctx, List.of(dishId), today, "LUNCH");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                menus.selectOne(new QueryWrapper<MenuDaily>().eq("id", menuId).last("FOR UPDATE"));
                dishes.selectOne(new QueryWrapper<Dish>().eq("id", dishId).last("FOR UPDATE"));
                profiles.selectOne(new QueryWrapper<ChildProfile>()
                        .eq("user_id", childUserId(ctx)).last("FOR UPDATE"));
                locked.countDown();
                await(release);
                return null;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS), "并发事务未能取得行锁");

            assertReadable(pool, "家长周视图", () -> mockMvc.perform(get("/api/parent/menu-week")
                    .header("Authorization", bearer(ctx.parentToken()))
                    .param("from", today.toString()).param("to", today.plusDays(6).toString()))
                    .andReturn().getResponse().getStatus());
            assertReadable(pool, "孩子周视图", () -> mockMvc.perform(get("/api/child/menu-week")
                    .header("Authorization", bearer(ctx.childToken()))
                    .param("from", today.toString()).param("to", today.plusDays(6).toString()))
                    .andReturn().getResponse().getStatus());
            assertReadable(pool, "推荐", () -> mockMvc.perform(get("/api/child/recommend")
                    .header("Authorization", bearer(ctx.childToken()))
                    .param("menuId", Long.toString(menuId)))
                    .andReturn().getResponse().getStatus());

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // ---------- helpers ----------

    private FamilyContext readyProfile() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        mockMvc.perform(post("/api/child/profile").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(profileJson(ctx))).andExpect(status().isOk());
        return ctx;
    }

    private String adminToken() {
        User admin = new User();
        admin.setOpenid("p2-admin-" + UUID.randomUUID());
        admin.setRole("ADMIN");
        admin.setStatus("NORMAL");
        users.insert(admin);
        return tokenWithJti(admin.getId(), RoleEnum.ADMIN, List.of(), UUID.randomUUID().toString());
    }

    private long category(String admin) throws Exception {
        return id(mockMvc.perform(post("/api/admin/dish-category").header("Authorization", bearer(admin))
                .contentType(JSON).content("{\"name\":\"P2 category\",\"sort\":0,\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk()).andReturn(), "categoryId");
    }

    private long dish(String admin, long categoryId, String name, List<String> allergens, String allergenStatus,
            int spiceLevel) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", Long.toString(categoryId));
        body.put("name", name);
        body.put("virtualPrice", "18.00");
        body.put("allergens", allergens);
        body.put("allergenStatus", allergenStatus);
        body.put("spiceLevel", spiceLevel);
        body.put("status", "ON_SALE");
        return id(mockMvc.perform(post("/api/admin/dish").header("Authorization", bearer(admin))
                .contentType(JSON).content(json(body))).andExpect(status().isOk()).andReturn(), "dishId");
    }

    private long familyMenu(FamilyContext ctx, List<Long> dishIds, LocalDate date, String mealType) throws Exception {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Long dishId : dishIds) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("type", "PRESET");
            ref.put("id", dishId);
            refs.add(ref);
        }
        return menuId(mockMvc.perform(post("/api/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(json(Map.of("menuDate", date.toString(), "mealType", mealType,
                        "dishIds", refs))))
                .andExpect(status().isOk()).andReturn());
    }

    private Map<String, Object> batchItem(LocalDate date, String mealType, long dishId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("menuDate", date.toString());
        item.put("mealType", mealType);
        item.put("dishIds", List.of(Map.of("type", "PRESET", "id", Long.toString(dishId))));
        item.put("status", "PUBLISHED");
        return item;
    }

    private void markFavorite(FamilyContext ctx, long dishId, long menuId, LocalDate date, String mealType)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dishId", Long.toString(dishId));
        body.put("favorite", true);
        body.put("menuId", Long.toString(menuId));
        body.put("menuDate", date.toString());
        body.put("mealType", mealType);
        body.put("dishType", "PRESET");
        mockMvc.perform(post("/api/menu/mark-favorite").header("Authorization", bearer(ctx.childToken()))
                .contentType(JSON).content(json(body))).andExpect(status().isOk());
    }

    private ResultActions transition(String token, long wantEatId, String status, int expectedVersion)
            throws Exception {
        return mockMvc.perform(post("/api/parent/want-eat/{id}/status", wantEatId)
                .header("Authorization", bearer(token)).contentType(JSON)
                .content(json(Map.of("status", status, "expectedVersion", expectedVersion))));
    }

    private ResultActions recommend(String token, long menuId, Long childId, int limit) throws Exception {
        var request = get("/api/child/recommend").header("Authorization", bearer(token))
                .param("menuId", Long.toString(menuId)).param("limit", Integer.toString(limit));
        if (childId != null) {
            request = request.param("childId", childId.toString());
        }
        return mockMvc.perform(request);
    }

    private ResultActions week(String token, LocalDate from, LocalDate to) throws Exception {
        return mockMvc.perform(get("/api/parent/menu-week").header("Authorization", bearer(token))
                .param("from", from.toString()).param("to", to.toString()));
    }

    /** 取周视图第 dayIndex 天的指定餐次。 */
    private JsonNode meal(JsonNode week, int dayIndex, String mealType) {
        for (JsonNode meal : week.get("days").get(dayIndex).get("meals")) {
            if (mealType.equals(meal.get("mealType").asString())) {
                return meal;
            }
        }
        throw new AssertionError("周视图缺少餐次：" + mealType);
    }

    private long wantEatId(FamilyContext ctx, long dishId) {
        ChildWantEat row = wantEats.selectOne(new QueryWrapper<ChildWantEat>()
                .eq("child_id", childUserId(ctx)).eq("dish_id", dishId).eq("dish_type", "PRESET"));
        assertNotNull(row, "想吃标记未落库");
        return row.getId();
    }

    private long definitionId(String code) {
        MedalDefinition def = medalDefs.selectOne(new QueryWrapper<MedalDefinition>().eq("code", code));
        assertNotNull(def, "勋章定义缺失：" + code + "（检查 v006 是否登记进 application-test.yml）");
        return def.getId();
    }

    private long awardCount(long definitionId, Long childId) {
        return medalAwards.selectCount(new QueryWrapper<MedalAward>()
                .eq("definition_id", definitionId).eq("child_id", childId));
    }

    private void assertReadable(ExecutorService pool, String label, java.util.concurrent.Callable<Integer> call) {
        Future<Integer> reader = pool.submit(call);
        try {
            assertEquals(200, reader.get(10, TimeUnit.SECONDS),
                    label + " 被行锁阻塞：纯读路径不应加 FOR UPDATE");
        } catch (TimeoutException ex) {
            fail(label + " 被行锁阻塞：纯读路径不应加 FOR UPDATE");
        } catch (Exception ex) {
            throw new AssertionError(label + " 读取失败", ex);
        }
    }

    private JsonNode dataJson(ResultActions actions) throws Exception {
        MvcResult result = actions.andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private long menuId(MvcResult result) throws Exception {
        JsonNode value = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("menuId");
        assertTrue(value.isString(), "Business identifiers must be JSON strings");
        return Long.parseLong(value.asString());
    }

    private long id(MvcResult result, String field) throws Exception {
        JsonNode value = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get(field);
        assertTrue(value.isString(), "Business identifiers must be JSON strings");
        return Long.parseLong(value.asString());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private LocalDate today() {
        return time.today();
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
