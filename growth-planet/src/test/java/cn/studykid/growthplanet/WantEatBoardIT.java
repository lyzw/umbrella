package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.entity.ChildProfile;
import cn.studykid.growthplanet.entity.ChildWantEat;
import cn.studykid.growthplanet.entity.Dish;
import cn.studykid.growthplanet.entity.MenuDaily;
import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.mapper.ChildProfileMapper;
import cn.studykid.growthplanet.mapper.ChildWantEatMapper;
import cn.studykid.growthplanet.mapper.DishMapper;
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
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P1 集成测试：家长「按日想吃清单」（R3）、状态流转、派生「爱吃」视图，
 * 以及 R6（展示路径去行锁）的并发回归。
 */
class WantEatBoardIT extends BaseIT {
    @Autowired UserMapper users;
    @Autowired DishMapper dishes;
    @Autowired MenuDailyMapper menus;
    @Autowired ChildProfileMapper profiles;
    @Autowired ChildWantEatMapper wantEats;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean BusinessTime time;

    @Test
    void boardGroupsByDateAndMealAndSummarisesPurchases() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "番茄炒蛋", List.of(), "DECLARED");

        LocalDate today = today();
        LocalDate tomorrow = today.plusDays(1);
        long lunch = familyMenu(ctx, List.of(dishId), today, "LUNCH");
        long dinner = familyMenu(ctx, List.of(dishId), tomorrow, "DINNER");
        markFavorite(ctx, dishId, lunch, today, "LUNCH");
        markFavorite(ctx, dishId, dinner, tomorrow, "DINNER");

        JsonNode data = dataJson(board(ctx.parentToken(), childUserId(ctx), today, today.plusDays(6)));

        assertEquals(childUserId(ctx).toString(), data.get("childId").asString());
        assertEquals(today.toString(), data.get("today").asString());
        assertEquals(0, data.get("expiredCount").asInt());

        assertEquals(2, data.get("days").size());
        JsonNode firstDay = data.get("days").get(0);
        assertEquals(today.toString(), firstDay.get("menuDate").asString());
        assertEquals(1, firstDay.get("meals").size());
        JsonNode firstMeal = firstDay.get("meals").get(0);
        assertEquals("LUNCH", firstMeal.get("mealType").asString());
        assertEquals("FAMILY", firstMeal.get("sourceType").asString());
        assertEquals(Long.toString(lunch), firstMeal.get("menuId").asString());
        JsonNode item = firstMeal.get("items").get(0);
        assertEquals("番茄炒蛋", item.get("name").asString());
        assertEquals("Synthetic category", item.get("categoryName").asString());
        assertEquals("PRESET", item.get("type").asString());
        assertEquals("MARKED", item.get("status").asString());
        assertFalse(item.get("expired").asBoolean());
        assertFalse(item.get("missing").asBoolean());

        JsonNode secondDay = data.get("days").get(1);
        assertEquals(tomorrow.toString(), secondDay.get("menuDate").asString());
        assertEquals("DINNER", secondDay.get("meals").get(0).get("mealType").asString());

        // 采购汇总：同一道菜跨两天出现 → 去重为 1 项、count=2、dates 两条
        JsonNode summary = data.get("summary");
        assertEquals(1, summary.get("totalItems").asInt());
        assertEquals(1, summary.get("dishes").size());
        assertEquals("番茄炒蛋", summary.get("dishes").get(0).get("name").asString());
        assertEquals(2, summary.get("dishes").get(0).get("count").asInt());
        assertEquals(2, summary.get("dishes").get(0).get("dates").size());
    }

    @Test
    void boardRejectsBadRangesAndForeignCallers() throws Exception {
        var ctx = readyProfile();
        var other = readyProfile();
        LocalDate today = today();

        // 儿童 token 无权限
        board(ctx.childToken(), childUserId(ctx), today, today).andExpect(status().isForbidden());
        // 缺 childId
        mockMvc.perform(get("/api/parent/want-eat").header("Authorization", bearer(ctx.parentToken()))
                .param("from", today.toString()).param("to", today.toString()))
                .andExpect(status().isBadRequest());
        // from > to
        board(ctx.parentToken(), childUserId(ctx), today.plusDays(1), today)
                .andExpect(status().isBadRequest());
        // 跨度超过 31 天
        board(ctx.parentToken(), childUserId(ctx), today, today.plusDays(31))
                .andExpect(status().isBadRequest());
        // 别家家长看本家孩子
        board(other.parentToken(), childUserId(ctx), today, today).andExpect(status().isForbidden());
        // 不存在的孩子
        board(ctx.parentToken(), 99999999L, today, today).andExpect(status().isForbidden());
    }

    @Test
    void boardKeepsExpiredRowsAndCountsThem() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "红烧肉", List.of(), "DECLARED");
        LocalDate today = today();
        long menuId = familyMenu(ctx, List.of(dishId), today, "LUNCH");
        markFavorite(ctx, dishId, menuId, today, "LUNCH");

        LocalDate tomorrow = today.plusDays(1);
        try {
            doReturn(tomorrow).when(time).today();
            JsonNode data = dataJson(board(ctx.parentToken(), childUserId(ctx), today, today.plusDays(6)));
            assertEquals(tomorrow.toString(), data.get("today").asString());
            assertEquals(1, data.get("expiredCount").asInt());
            assertTrue(data.get("days").get(0).get("meals").get(0).get("items").get(0).get("expired").asBoolean());
            // 过期行仍然被返回（不丢数据）
            assertEquals(1, data.get("days").size());
        } finally {
            reset(time);
        }

        // 默认区间（from=今天）下该行不再过期
        JsonNode fresh = dataJson(board(ctx.parentToken(), childUserId(ctx), today, today.plusDays(6)));
        assertEquals(0, fresh.get("expiredCount").asInt());
        assertFalse(fresh.get("days").get(0).get("meals").get(0).get("items").get(0).get("expired").asBoolean());
    }

    @Test
    void boardKeepsRowsWhoseDishDisappeared() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "待删除菜", List.of(), "DECLARED");
        LocalDate today = today();
        long menuId = familyMenu(ctx, List.of(dishId), today, "LUNCH");
        markFavorite(ctx, dishId, menuId, today, "LUNCH");
        mockMvc.perform(delete("/api/admin/dish/{id}", dishId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        JsonNode data = dataJson(board(ctx.parentToken(), childUserId(ctx), today, today));
        JsonNode item = data.get("days").get(0).get("meals").get(0).get("items").get(0);
        assertTrue(item.get("missing").asBoolean());
        assertTrue(item.get("name").isNull());
        assertEquals(1, data.get("summary").get("totalItems").asInt());
        assertTrue(data.get("summary").get("dishes").get(0).get("name").isNull());
    }

    @Test
    void statusTransitionFollowsOptimisticLockAndOwnership() throws Exception {
        var ctx = readyProfile();
        var other = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "青菜", List.of(), "DECLARED");
        LocalDate today = today();
        long menuId = familyMenu(ctx, List.of(dishId), today, "LUNCH");
        markFavorite(ctx, dishId, menuId, today, "LUNCH");

        long wantEatId = wantEats.selectOne(new QueryWrapper<ChildWantEat>()
                .eq("child_id", childUserId(ctx)).eq("dish_id", dishId)).getId();
        int version = wantEats.selectById(wantEatId).getVersion();

        // 版本不符 → 409（手写乐观锁）
        transition(ctx.parentToken(), wantEatId, "ADOPTED", version + 5).andExpect(status().isConflict());
        // 别家家长 / 儿童 token → 403
        transition(other.parentToken(), wantEatId, "ADOPTED", version).andExpect(status().isForbidden());
        transition(ctx.childToken(), wantEatId, "ADOPTED", version).andExpect(status().isForbidden());
        // 不存在 → 404；非法状态 → 400
        transition(ctx.parentToken(), 99999999L, "ADOPTED", 0).andExpect(status().isNotFound());
        transition(ctx.parentToken(), wantEatId, "UNKNOWN", version).andExpect(status().isBadRequest());

        // 正常流转 ADOPTED → COOKED → MARKED
        transition(ctx.parentToken(), wantEatId, "ADOPTED", version).andExpect(status().isOk());
        JsonNode adopted = dataJson(board(ctx.parentToken(), childUserId(ctx), today, today));
        assertEquals("ADOPTED",
                adopted.get("days").get(0).get("meals").get(0).get("items").get(0).get("status").asString());

        transition(ctx.parentToken(), wantEatId, "COOKED", version + 1).andExpect(status().isOk());
        transition(ctx.parentToken(), wantEatId, "MARKED", version + 2).andExpect(status().isOk());
        JsonNode reverted = dataJson(board(ctx.parentToken(), childUserId(ctx), today, today));
        assertEquals("MARKED",
                reverted.get("days").get(0).get("meals").get(0).get("items").get(0).get("status").asString());
    }

    @Test
    void frequentDishesRankRecentAndSkipUnavailable() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long recent = dish(admin, categoryId, "常吃新菜", List.of(), "DECLARED");
        long older = dish(admin, categoryId, "常吃旧菜", List.of(), "DECLARED");
        long gone = dish(admin, categoryId, "已删除菜", List.of(), "DECLARED");
        LocalDate today = today();

        long menuRecent = familyMenu(ctx, List.of(recent), today, "LUNCH");
        markFavorite(ctx, recent, menuRecent, today, "LUNCH");
        long menuOlder = familyMenu(ctx, List.of(older), today.minusDays(10), "LUNCH");
        markFavorite(ctx, older, menuOlder, today.minusDays(10), "LUNCH");
        long menuGone = familyMenu(ctx, List.of(gone), today, "DINNER");
        markFavorite(ctx, gone, menuGone, today, "DINNER");
        mockMvc.perform(delete("/api/admin/dish/{id}", gone).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        JsonNode data = dataJson(mockMvc.perform(get("/api/child/frequent-dish")
                .header("Authorization", bearer(ctx.childToken()))));
        JsonNode items = data.get("dishes");
        // 已删除菜品被跳过；近期加权使今天的菜排在 10 天前的菜之前
        assertEquals(2, items.size());
        assertEquals("常吃新菜", items.get(0).get("name").asString());
        assertEquals(1, items.get(0).get("count").asInt());
        assertEquals("常吃旧菜", items.get(1).get("name").asString());

        // 家长可代查但须本家庭；越权 403
        dataJson(mockMvc.perform(get("/api/child/frequent-dish")
                .header("Authorization", bearer(ctx.parentToken()))
                .param("childId", childUserId(ctx).toString()))).get("dishes");
        var other = readyProfile();
        mockMvc.perform(get("/api/child/frequent-dish").header("Authorization", bearer(other.parentToken()))
                .param("childId", childUserId(ctx).toString())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/child/frequent-dish").header("Authorization", bearer(ctx.childToken()))
                .param("childId", childUserId(other).toString())).andExpect(status().isForbidden());
    }

    /**
     * R6 回归：展示路径不得持有行锁。另一事务锁住菜单/菜品/档案行期间，
     * 孩子的 daily() 必须照常返回，而不是排队等待。
     */
    @Test
    @Timeout(60)
    void dailyDisplayPathDoesNotWaitOnRowLocks() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "并发展示菜", List.of(), "DECLARED");
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

            Future<Integer> reader = pool.submit(() -> mockMvc.perform(get("/api/menu/daily")
                            .header("Authorization", bearer(ctx.childToken())).param("sourceType", "FAMILY")
                            .param("menuDate", today.toString()).param("mealType", "LUNCH"))
                    .andReturn().getResponse().getStatus());
            try {
                assertEquals(200, reader.get(10, TimeUnit.SECONDS),
                        "展示路径被行锁阻塞：纯读路径不应加 FOR UPDATE");
            } catch (TimeoutException ex) {
                fail("展示路径被行锁阻塞：纯读路径不应加 FOR UPDATE");
            }
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
        admin.setOpenid("want-eat-admin-" + UUID.randomUUID());
        admin.setRole("ADMIN");
        admin.setStatus("NORMAL");
        users.insert(admin);
        return tokenWithJti(admin.getId(), RoleEnum.ADMIN, List.of(), UUID.randomUUID().toString());
    }

    private long category(String admin) throws Exception {
        return id(mockMvc.perform(post("/api/admin/dish-category").header("Authorization", bearer(admin))
                .contentType(JSON).content("{\"name\":\"Synthetic category\",\"sort\":0,\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk()).andReturn(), "categoryId");
    }

    private long dish(String admin, long categoryId, String name, List<String> allergens, String allergenStatus)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", Long.toString(categoryId));
        body.put("name", name);
        body.put("virtualPrice", "18.00");
        body.put("allergens", allergens);
        body.put("allergenStatus", allergenStatus);
        body.put("spiceLevel", 0);
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("menuDate", date.toString());
        body.put("mealType", mealType);
        body.put("dishIds", refs);
        return id(mockMvc.perform(post("/api/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(json(body))).andExpect(status().isOk()).andReturn(), "menuId");
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

    private ResultActions board(String token, Long childId, LocalDate from, LocalDate to) throws Exception {
        return mockMvc.perform(get("/api/parent/want-eat").header("Authorization", bearer(token))
                .param("childId", childId.toString()).param("from", from.toString()).param("to", to.toString()));
    }

    private ResultActions transition(String token, long wantEatId, String status, int expectedVersion)
            throws Exception {
        return mockMvc.perform(post("/api/parent/want-eat/{id}/status", wantEatId)
                .header("Authorization", bearer(token)).contentType(JSON)
                .content(json(Map.of("status", status, "expectedVersion", expectedVersion))));
    }

    private JsonNode dataJson(ResultActions actions) throws Exception {
        MvcResult result = actions.andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
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
