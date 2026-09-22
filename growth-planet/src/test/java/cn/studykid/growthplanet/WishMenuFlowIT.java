package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.entity.ChildProfile;
import cn.studykid.growthplanet.entity.ChildWantEat;
import cn.studykid.growthplanet.entity.Dish;
import cn.studykid.growthplanet.entity.Notice;
import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.mapper.ChildProfileMapper;
import cn.studykid.growthplanet.mapper.ChildWantEatMapper;
import cn.studykid.growthplanet.mapper.DishMapper;
import cn.studykid.growthplanet.mapper.NoticeMapper;
import cn.studykid.growthplanet.mapper.UserMapper;
import cn.studykid.growthplanet.service.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P3 集成测试：儿童心愿菜单。
 * 覆盖家长配置上限（含乐观锁）、儿童菜谱目录（跨家庭隔离 / 安全置灰）、候选池标记、
 * 提交（上限校验 + 收编明细 + 通知家长）、提交后锁定与撤回重提交、家长查看、同意撤回，
 * 以及 R6（展示路径不加行锁）的并发回归。
 */
class WishMenuFlowIT extends BaseIT {
    @Autowired UserMapper users;
    @Autowired ChildProfileMapper profiles;
    @Autowired ChildWantEatMapper wantEats;
    @Autowired DishMapper dishes;
    @Autowired NoticeMapper notices;
    @Autowired BusinessTime time;
    @Autowired PlatformTransactionManager transactions;

    // ---------- 家长设置 ----------

    @Test
    void settingUsesDefaultsAndOptimisticLock() throws Exception {
        var ctx = readyProfile();

        JsonNode initial = dataJson(mockMvc.perform(get("/api/parent/wish-setting")
                .header("Authorization", bearer(ctx.parentToken()))));
        assertEquals(5, initial.get("maxDishes").asInt(), "无设置行时应返回默认上限 5");
        assertTrue(initial.get("enabled").asBoolean());
        assertEquals(0, initial.get("version").asInt());

        JsonNode updated = dataJson(putSetting(ctx.parentToken(), 2, true, 0));
        assertEquals(2, updated.get("maxDishes").asInt());
        assertEquals(1, updated.get("version").asInt());

        // 手写乐观锁：版本陈旧 → 409
        putSetting(ctx.parentToken(), 3, true, 0).andExpect(status().isConflict());
        // 越界 → 400（bean validation）
        putSetting(ctx.parentToken(), 11, true, 1).andExpect(status().isBadRequest());
        putSetting(ctx.parentToken(), 0, true, 1).andExpect(status().isBadRequest());
        // 儿童无权读写家长设置
        mockMvc.perform(get("/api/parent/wish-setting").header("Authorization", bearer(ctx.childToken())))
                .andExpect(status().isForbidden());
        // 关闭开关
        JsonNode off = dataJson(putSetting(ctx.parentToken(), 2, false, 1));
        assertFalse(off.get("enabled").asBoolean());
        assertEquals(2, off.get("version").asInt());
    }

    // ---------- 儿童菜谱目录 ----------

    @Test
    void catalogScopesFamilyDishesAndFlagsUnsafeOnes() throws Exception {
        var ctx = readyProfile();
        var other = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        dish(admin, categoryId, "预置番茄蛋", List.of(), "DECLARED");
        legacyUnknownDish(categoryId, "待确认信息菜");
        dish(admin, categoryId, "花生小炒", List.of("PEANUT"), "DECLARED");
        familyDish(ctx, categoryId, "自家私房菜");
        familyDish(other, categoryId, "别家私房菜");

        JsonNode family = dataJson(catalog(ctx.childToken(), today(), "FAMILY"));
        assertEquals(1L, family.get("total").asLong(), "家庭菜谱只应看到本家庭在售私有菜");
        assertEquals("自家私房菜", family.get("items").get(0).get("name").asString());
        assertEquals("FAMILY", family.get("items").get(0).get("type").asString());

        JsonNode presetTab = dataJson(catalog(ctx.childToken(), today(), "PRESET"));
        assertTrue(presetTab.get("total").asLong() >= 3);
        assertTrue(presetTab.get("items").findValues("name").stream()
                .noneMatch(node -> "别家私房菜".equals(node.asString())), "预置目录不应混入任何家庭私有菜");

        Map<String, JsonNode> unsafe = catalogs(presetTab, List.of("待确认信息菜", "花生小炒"));
        assertEquals("UNKNOWN", unsafe.get("待确认信息菜").get("allergenStatus").asString());
        assertFalse(unsafe.get("待确认信息菜").get("selectable").asBoolean());
        assertTrue(unsafe.get("花生小炒").get("allergyConflict").asBoolean(), "孩子对 PEANUT 过敏");
        assertFalse(unsafe.get("花生小炒").get("selectable").asBoolean());
        assertFalse(unsafe.get("花生小炒").get("disliked").asBoolean());
        // status / safetyStatus 是前端统一安全提示 safetyLabel() 的输入（区分"找管理员"与"找家长"），必须回传
        assertEquals("ON_SALE", unsafe.get("花生小炒").get("status").asString());
        assertEquals("ALLERGY_CONFLICT", unsafe.get("花生小炒").get("safetyStatus").asString());
        assertEquals("UNKNOWN", unsafe.get("待确认信息菜").get("safetyStatus").asString());

        // SCHOOL 不在可选范围；超出可编辑窗口的未来日期被拒
        catalog(ctx.childToken(), today(), "SCHOOL").andExpect(status().isBadRequest());
        catalog(ctx.childToken(), today().plusDays(7), "FAMILY").andExpect(status().isBadRequest());
        catalog(ctx.childToken(), today(), "OTHER").andExpect(status().isBadRequest());
    }

    // ---------- 候选池标记 ----------

    @Test
    void markRejectsUnsafeForeignAndOutOfWindowDishes() throws Exception {
        var ctx = readyProfile();
        var other = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long preset = dish(admin, categoryId, "可标记菜", List.of(), "DECLARED");
        long unknown = legacyUnknownDish(categoryId, "信息缺失菜");
        long peanut = dish(admin, categoryId, "过敏花生菜", List.of("PEANUT"), "DECLARED");
        long foreign = familyDish(other, categoryId, "别家菜");

        JsonNode added = dataJson(mark(ctx.childToken(), today(), "PRESET", preset, true));
        assertEquals(1, added.get("dishCount").asInt());
        assertEquals(0, added.get("submittedCount").asInt());
        assertTrue(added.get("canSubmit").asBoolean());
        assertEquals("NONE", added.get("status").asString());
        assertEquals("ALL", added.get("items").get(0).get("mealTypes").get(0).asString(),
                "心愿目录标记使用占位餐次 ALL（心愿菜单不分餐次）");
        assertEquals("ON_SALE", added.get("items").get(0).get("status").asString());
        assertEquals("DECLARED", added.get("items").get(0).get("safetyStatus").asString());

        mark(ctx.childToken(), today(), "PRESET", unknown, true).andExpect(status().isBadRequest());
        mark(ctx.childToken(), today(), "PRESET", peanut, true).andExpect(status().isBadRequest());
        mark(ctx.childToken(), today(), "FAMILY", foreign, true).andExpect(status().isBadRequest());
        mark(ctx.childToken(), today().plusDays(7), "PRESET", preset, true).andExpect(status().isBadRequest());
        mark(ctx.childToken(), today().minusDays(1), "PRESET", preset, true).andExpect(status().isBadRequest());
        mark(ctx.childToken(), today(), "SCHOOL", preset, true).andExpect(status().isBadRequest());
        mark(other.childToken(), today(), "PRESET", preset, true).andExpect(status().isOk());

        JsonNode removed = dataJson(mark(ctx.childToken(), today(), "PRESET", preset, false));
        assertEquals(0, removed.get("dishCount").asInt());
        assertFalse(removed.get("canSubmit").asBoolean(), "候选池为空时不可提交");

        // 目录里的 marked 随候选池变化
        JsonNode catalogAfterRemove = dataJson(catalog(ctx.childToken(), today(), "PRESET"));
        assertFalse(itemByName(catalogAfterRemove, "可标记菜").get("marked").asBoolean());
        dataJson(mark(ctx.childToken(), today(), "PRESET", preset, true));
        JsonNode catalogAfterMark = dataJson(catalog(ctx.childToken(), today(), "PRESET"));
        assertTrue(itemByName(catalogAfterMark, "可标记菜").get("marked").asBoolean());
    }

    // ---------- 提交与通知 ----------

    @Test
    void submitEnforcesParentLimitCollectsItemsAndNotifiesParent() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long first = dish(admin, categoryId, "心愿菜一", List.of(), "DECLARED");
        long second = dish(admin, categoryId, "心愿菜二", List.of(), "DECLARED");
        long third = dish(admin, categoryId, "心愿菜三", List.of(), "DECLARED");
        long unmarked = dish(admin, categoryId, "没被标记的菜", List.of(), "DECLARED");
        putSetting(ctx.parentToken(), 2, true, 0).andExpect(status().isOk());
        for (long dishId : List.of(first, second, third)) {
            mark(ctx.childToken(), today(), "PRESET", dishId, true).andExpect(status().isOk());
        }

        // 超上限 → 400 + 独立错误码 E-014
        // （GlobalExceptionHandler 只回错误码默认文案、不透出 BizException 的 detail，故必须靠 code 区分）
        submit(ctx.childToken(), today(), List.of(first, second, third), 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-014"));
        // 未标记的菜不能提交
        submit(ctx.childToken(), today(), List.of(first, unmarked), 0).andExpect(status().isBadRequest());
        // 空清单 → 400（bean validation）
        submit(ctx.childToken(), today(), List.of(), 0).andExpect(status().isBadRequest());

        JsonNode submitted = dataJson(submit(ctx.childToken(), today(), List.of(first, second), 0));
        assertEquals("SUBMITTED", submitted.get("status").asString());
        assertTrue(submitted.get("locked").asBoolean());
        assertFalse(submitted.get("canEdit").asBoolean());
        assertEquals(2, submitted.get("submittedCount").asInt());
        assertEquals(3, submitted.get("dishCount").asInt(), "候选池仍保留第 3 道未提交的菜");
        assertNotNull(submitted.get("submitTime"));
        long menuId = Long.parseLong(submitted.get("menuId").asString());

        // 收编：命中的两行 wish_id 指向提交单，未提交的那行保持 0
        assertEquals(2, wantEats.selectCount(new QueryWrapper<ChildWantEat>()
                .eq("child_id", childUserId(ctx)).eq("menu_date", today()).eq("wish_id", menuId)));
        assertEquals(1, wantEats.selectCount(new QueryWrapper<ChildWantEat>()
                .eq("child_id", childUserId(ctx)).eq("menu_date", today()).eq("wish_id", 0L)));

        // 通知家长：站内 + 订阅各一行，接收人为家庭创建家长，事件键含提交版本
        List<Notice> sent = notices.selectList(new QueryWrapper<Notice>()
                .eq("event_type", "WISH_SUBMIT").eq("family_id", ctx.familyId()).orderByAsc("id"));
        assertEquals(2, sent.size());
        assertEquals("wish:" + menuId + ":1", sent.get(0).getEventKey());
        assertEquals("IN_APP", sent.get(0).getChannel());
        assertEquals("SUBSCRIBE", sent.get(1).getChannel());
        assertEquals(parentUserId(ctx), sent.get(0).getReceiverId());
        assertEquals(childUserId(ctx), sent.get(0).getChildId());

        // 锁定态下既有 mark-favorite 通路同样被拒（发布一份含该菜的当日家庭菜单）
        markFavoriteMenu(ctx, List.of(first), today());
        markFavorite(ctx, first, today()).andExpect(status().isBadRequest());
    }

    // ---------- 提交时的二次安全校验 ----------

    @Test
    void submitRejectsDishesThatBecameUnsafeAfterMarking() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long unsafe = dish(admin, categoryId, "标记后转待确认菜", List.of(), "DECLARED");
        long safe = dish(admin, categoryId, "标记后仍有声明菜", List.of(), "DECLARED");
        mark(ctx.childToken(), today(), "PRESET", unsafe, true).andExpect(status().isOk());
        mark(ctx.childToken(), today(), "PRESET", safe, true).andExpect(status().isOk());

        // 标记 → 提交之间菜品被改成"过敏信息待确认"（R5-c 只挡上架时刻，历史/异常数据仍可能出现）
        dishes.update(null, new UpdateWrapper<Dish>().eq("id", unsafe).set("allergen_status", "UNKNOWN"));

        submit(ctx.childToken(), today(), List.of(unsafe), 0).andExpect(status().isBadRequest());
        // 只提交仍安全的菜可以成功；把被拒的那道移除后整单也能提交
        assertTrue(dataJson(submit(ctx.childToken(), today(), List.of(safe), 0)).get("locked").asBoolean());
    }

    // ---------- 锁定 / 撤回 / 重新提交 ----------

    @Test
    void submissionLocksMarksUntilWithdrawnThenResubmitNotifiesAgain() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishOne = dish(admin, categoryId, "锁定菜一", List.of(), "DECLARED");
        long dishTwo = dish(admin, categoryId, "锁定菜二", List.of(), "DECLARED");
        markFavoriteMenu(ctx, List.of(dishOne), today());
        mark(ctx.childToken(), today(), "PRESET", dishOne, true).andExpect(status().isOk());

        JsonNode submitted = dataJson(submit(ctx.childToken(), today(), List.of(dishOne), 0));
        assertEquals(1, submitted.get("version").asInt(), "版本语义：version = 写入次数，首次提交后为 1（0 专指当天没有心愿单）");
        long menuId = Long.parseLong(submitted.get("menuId").asString());

        // 已提交：标记增删、重复提交（版本一致）、既有 mark-favorite 全部被拒
        mark(ctx.childToken(), today(), "PRESET", dishTwo, true).andExpect(status().isBadRequest());
        mark(ctx.childToken(), today(), "PRESET", dishOne, false).andExpect(status().isBadRequest());
        submit(ctx.childToken(), today(), List.of(dishOne), 1).andExpect(status().isBadRequest());
        markFavorite(ctx, dishOne, today()).andExpect(status().isBadRequest());

        // 撤回 → 解除锁定（明细保留 wish_id 以便回显）
        JsonNode withdrawn = dataJson(withdraw(ctx.childToken(), today(), 1));
        assertEquals("WITHDRAWN", withdrawn.get("status").asString());
        assertFalse(withdrawn.get("locked").asBoolean());
        assertTrue(withdrawn.get("canEdit").asBoolean());
        assertEquals(1, withdrawn.get("submittedCount").asInt());

        mark(ctx.childToken(), today(), "PRESET", dishTwo, true).andExpect(status().isOk());
        markFavorite(ctx, dishOne, today()).andExpect(status().isOk());

        // 重新提交 → 提交版本 +1 → 第二条通知（事件键不同）
        JsonNode resubmitted = dataJson(submit(ctx.childToken(), today(), List.of(dishOne, dishTwo), 2));
        assertEquals("SUBMITTED", resubmitted.get("status").asString());
        assertEquals(3, resubmitted.get("version").asInt(), "提交(1) → 撤回(2) → 重新提交(3)");
        List<Notice> sent = notices.selectList(new QueryWrapper<Notice>()
                .eq("event_type", "WISH_SUBMIT").eq("family_id", ctx.familyId()));
        assertEquals(4, sent.size());
        assertTrue(sent.stream().anyMatch(row -> ("wish:" + menuId + ":2").equals(row.getEventKey())));

        // 陈旧版本提交 / 撤回 → 409
        submit(ctx.childToken(), today(), List.of(dishOne), 1).andExpect(status().isConflict());
        withdraw(ctx.childToken(), today(), 1).andExpect(status().isConflict());
    }

    // ---------- 家长查看 ----------

    @Test
    void parentViewsChildMenuAndForeignCallersAreForbidden() throws Exception {
        var ctx = readyProfile();
        var other = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "家长查看菜", List.of(), "DECLARED");
        mark(ctx.childToken(), today(), "PRESET", dishId, true).andExpect(status().isOk());
        dataJson(submit(ctx.childToken(), today(), List.of(dishId), 0));

        JsonNode view = dataJson(mockMvc.perform(get("/api/parent/wish-menu")
                .header("Authorization", bearer(ctx.parentToken()))
                .param("childId", childUserId(ctx).toString()).param("menuDate", today().toString())));
        assertEquals(childUserId(ctx).toString(), view.get("childId").asString());
        assertEquals("SUBMITTED", view.get("status").asString());
        assertEquals(1, view.get("submittedCount").asInt());
        assertEquals("家长查看菜", view.get("items").get(0).get("name").asString());

        JsonNode list = dataJson(mockMvc.perform(get("/api/parent/wish-menu/list")
                .header("Authorization", bearer(ctx.parentToken()))
                .param("childId", childUserId(ctx).toString())
                .param("from", today().toString()).param("to", today().plusDays(6).toString())));
        assertEquals(1, list.get("items").size());
        assertEquals(1, list.get("items").get(0).get("items").size());
        assertFalse(list.get("items").get(0).get("expired").asBoolean());

        // 未创建的一天：状态 NONE（可选功能，不产生占位记录）
        JsonNode empty = dataJson(mockMvc.perform(get("/api/parent/wish-menu")
                .header("Authorization", bearer(ctx.parentToken()))
                .param("childId", childUserId(ctx).toString())
                .param("menuDate", today().plusDays(1).toString())));
        assertEquals("NONE", empty.get("status").asString());
        assertTrue(empty.get("items").isEmpty());

        // 越权：别家家长 / 儿童 token / 区间超限 / 不存在的孩子
        mockMvc.perform(get("/api/parent/wish-menu").header("Authorization", bearer(other.parentToken()))
                .param("childId", childUserId(ctx).toString()).param("menuDate", today().toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/parent/wish-menu").header("Authorization", bearer(ctx.childToken()))
                .param("childId", childUserId(ctx).toString()).param("menuDate", today().toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/parent/wish-menu/list").header("Authorization", bearer(ctx.parentToken()))
                .param("childId", "99999999").param("from", today().toString())
                .param("to", today().toString())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/parent/wish-menu/list").header("Authorization", bearer(ctx.parentToken()))
                .param("childId", childUserId(ctx).toString())
                .param("from", today().toString()).param("to", today().plusDays(31).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revokedConsentBlocksWishMenu() throws Exception {
        var ctx = readyProfile();
        revoke(ctx);
        mockMvc.perform(get("/api/child/wish-menu").header("Authorization", bearer(ctx.childToken()))
                .param("menuDate", today().toString())).andExpect(status().isConflict());
        mockMvc.perform(get("/api/child/wish-catalog").header("Authorization", bearer(ctx.childToken()))
                .param("menuDate", today().toString()).param("sourceType", "PRESET"))
                .andExpect(status().isConflict());
        mark(ctx.childToken(), today(), "PRESET", 1L, true).andExpect(status().isConflict());
    }

    // ---------- R6：展示路径不加行锁 ----------

    @Test
    @Timeout(60)
    void displayPathsDoNotWaitOnRowLocks() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "并发展示菜", List.of(), "DECLARED");
        mark(ctx.childToken(), today(), "PRESET", dishId, true).andExpect(status().isOk());
        dataJson(submit(ctx.childToken(), today(), List.of(dishId), 0));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                profiles.selectOne(new QueryWrapper<ChildProfile>()
                        .eq("user_id", childUserId(ctx)).last("FOR UPDATE"));
                wantEats.selectOne(new QueryWrapper<ChildWantEat>()
                        .eq("child_id", childUserId(ctx)).last("FOR UPDATE"));
                locked.countDown();
                await(release);
                return null;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS), "并发事务未能取得行锁");

            Future<Integer> childRead = pool.submit(() -> mockMvc.perform(get("/api/child/wish-menu")
                            .header("Authorization", bearer(ctx.childToken()))
                            .param("menuDate", today().toString()))
                    .andReturn().getResponse().getStatus());
            Future<Integer> parentRead = pool.submit(() -> mockMvc.perform(get("/api/parent/wish-menu/list")
                            .header("Authorization", bearer(ctx.parentToken()))
                            .param("childId", childUserId(ctx).toString())
                            .param("from", today().toString()).param("to", today().toString()))
                    .andReturn().getResponse().getStatus());
            try {
                assertEquals(200, childRead.get(10, TimeUnit.SECONDS), "纯读路径不应加 FOR UPDATE");
                assertEquals(200, parentRead.get(10, TimeUnit.SECONDS), "纯读路径不应加 FOR UPDATE");
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
        admin.setOpenid("wish-menu-admin-" + UUID.randomUUID());
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

    private long familyDish(FamilyContext ctx, long categoryId, String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", Long.toString(categoryId));
        body.put("name", name);
        body.put("virtualPrice", "12.00");
        body.put("allergens", List.of());
        body.put("allergenStatus", "DECLARED");
        body.put("spiceLevel", 1);
        return id(mockMvc.perform(post("/api/parent/family-dish").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(json(body))).andExpect(status().isOk()).andReturn(), "dishId");
    }

    /**
     * 直接落一行 ON_SALE + UNKNOWN 的预置菜：模拟 R5-c（上架必须先声明过敏原）之前的历史数据。
     * 该状态已无法通过管理端接口造出，但目录/提交路径仍必须对它 fail closed。
     */
    private long legacyUnknownDish(long categoryId, String name) {
        Dish row = new Dish();
        row.setCategoryId(categoryId);
        row.setName(name);
        row.setVirtualPrice(new java.math.BigDecimal("18.00"));
        row.setAllergens(List.of());
        row.setAllergenStatus("UNKNOWN");
        row.setSpiceLevel(0);
        row.setStatus("ON_SALE");
        dishes.insert(row);
        return row.getId();
    }

    /** 发布一份含指定 PRESET 菜品的当日家庭菜单（供既有 mark-favorite 通路使用）。 */
    private long markFavoriteMenu(FamilyContext ctx, List<Long> dishIds, LocalDate date) throws Exception {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Long dishId : dishIds) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("type", "PRESET");
            ref.put("id", dishId);
            refs.add(ref);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("menuDate", date.toString());
        body.put("mealType", "LUNCH");
        body.put("dishIds", refs);
        return id(mockMvc.perform(post("/api/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(json(body))).andExpect(status().isOk()).andReturn(), "menuId");
    }

    private ResultActions markFavorite(FamilyContext ctx, long dishId, LocalDate date) throws Exception {
        long menuId = id(mockMvc.perform(get("/api/parent/menu-daily")
                        .header("Authorization", bearer(ctx.parentToken()))
                        .param("menuDate", date.toString()).param("mealType", "LUNCH"))
                .andExpect(status().isOk()).andReturn(), "menuId");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dishId", Long.toString(dishId));
        body.put("favorite", true);
        body.put("dishType", "PRESET");
        body.put("menuDate", date.toString());
        body.put("mealType", "LUNCH");
        body.put("menuId", Long.toString(menuId));
        return mockMvc.perform(post("/api/menu/mark-favorite").header("Authorization", bearer(ctx.childToken()))
                .contentType(JSON).content(json(body)));
    }

    private ResultActions putSetting(String token, int maxDishes, boolean enabled, int expectedVersion)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("maxDishes", maxDishes);
        body.put("enabled", enabled);
        body.put("expectedVersion", expectedVersion);
        return mockMvc.perform(put("/api/parent/wish-setting").header("Authorization", bearer(token))
                .contentType(JSON).content(json(body)));
    }

    private ResultActions catalog(String token, LocalDate menuDate, String sourceType) throws Exception {
        return mockMvc.perform(get("/api/child/wish-catalog").header("Authorization", bearer(token))
                .param("menuDate", menuDate.toString()).param("sourceType", sourceType).param("pageSize", "100"));
    }

    private ResultActions mark(String token, LocalDate menuDate, String type, long id, boolean selected)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("menuDate", menuDate.toString());
        body.put("type", type);
        body.put("id", Long.toString(id));
        body.put("selected", selected);
        return mockMvc.perform(post("/api/child/wish-mark").header("Authorization", bearer(token))
                .contentType(JSON).content(json(body)));
    }

    private ResultActions submit(String token, LocalDate menuDate, List<Long> dishIds, int expectedVersion)
            throws Exception {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Long dishId : dishIds) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("type", "PRESET");
            ref.put("id", Long.toString(dishId));
            refs.add(ref);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("menuDate", menuDate.toString());
        body.put("refs", refs);
        body.put("expectedVersion", expectedVersion);
        return mockMvc.perform(post("/api/child/wish-menu/submit").header("Authorization", bearer(token))
                .contentType(JSON).content(json(body)));
    }

    private ResultActions withdraw(String token, LocalDate menuDate, int expectedVersion) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("menuDate", menuDate.toString());
        body.put("expectedVersion", expectedVersion);
        return mockMvc.perform(post("/api/child/wish-menu/withdraw").header("Authorization", bearer(token))
                .contentType(JSON).content(json(body)));
    }

    private JsonNode dataJson(ResultActions actions) throws Exception {
        MvcResult result = actions.andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    /** 目录响应按菜品名建索引，便于逐项断言。 */
    private Map<String, JsonNode> catalogs(JsonNode catalogData, List<String> names) {
        Map<String, JsonNode> indexed = new LinkedHashMap<>();
        for (JsonNode item : catalogData.get("items")) {
            if (names.contains(item.get("name").asString())) {
                indexed.put(item.get("name").asString(), item);
            }
        }
        assertEquals(names.size(), indexed.size(), "目录应返回全部预期菜品");
        return indexed;
    }

    private JsonNode itemByName(JsonNode catalogData, String name) {
        for (JsonNode item : catalogData.get("items")) {
            if (name.equals(item.get("name").asString())) {
                return item;
            }
        }
        return fail("目录中缺少菜品：" + name);
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

    private Long parentUserId(FamilyContext ctx) {
        return jwtUtil.getUserId(jwtUtil.parse(ctx.parentToken()));
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
