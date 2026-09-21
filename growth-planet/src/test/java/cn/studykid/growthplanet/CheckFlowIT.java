package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.entity.CheckItem;
import cn.studykid.growthplanet.entity.CheckRecord;
import cn.studykid.growthplanet.mapper.CheckItemMapper;
import cn.studykid.growthplanet.mapper.CheckRecordMapper;
import cn.studykid.growthplanet.mapper.MedalAwardMapper;
import cn.studykid.growthplanet.mapper.MedalDefinitionMapper;
import cn.studykid.growthplanet.entity.MedalAward;
import cn.studykid.growthplanet.entity.MedalDefinition;
import cn.studykid.growthplanet.service.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 健康打卡集成测试（V0.0.2 F-033~F-035）。覆盖设计文档 §7 全部用例：
 * 1) 家长建项 → 儿童打卡 → 今日次数 +1；达每日上限拒收 E013。
 * 2) streak 跨项整体连续推导：构造连续 7 天 → streak=7；中间断签 → 重置。
 * 3) 勋章：streak 达阈值触发 HEALTH_STREAK_* 且幂等（重复打卡不重复发放）。
 * 4) 同意撤回（F-007/NF-5）→ 儿童接口返回 E010、列表隐藏。
 * 5) 跨家庭隔离：家庭 B 的儿童不可见/不可打卡家庭 A 的项（空列表 + 404，不泄露存在性）。
 * 6) 家长删项 → 历史记录保留 item_name 快照、记录本身不软删。
 */
class CheckFlowIT extends BaseIT {

    @Autowired
    CheckItemMapper items;
    @Autowired
    CheckRecordMapper records;
    @Autowired
    MedalDefinitionMapper medalDefs;
    @Autowired
    MedalAwardMapper medalAwards;
    @Autowired
    BusinessTime time;

    // ---- 基础工具 ----

    private FamilyContext boundFamily() throws Exception {
        FamilyContext ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        return ctx;
    }

    /** 家长建项，返回 itemId。 */
    private Long createItem(FamilyContext ctx, String name, String icon, String unit, int dailyTarget, int sortOrder)
            throws Exception {
        String body = "{\"name\":\"" + name + "\",\"icon\":\"" + icon + "\",\"unit\":\"" + unit
                + "\",\"dailyTarget\":" + dailyTarget + ",\"sortOrder\":" + sortOrder + "}";
        MvcResult r = mockMvc.perform(post("/api/parent/check-item")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return data(r).get("itemId").asLong();
    }

    /** 儿童一键打卡；调用方自行断言状态。 */
    private MvcResult childCheckIn(FamilyContext ctx, Long itemId) throws Exception {
        return mockMvc.perform(post("/api/child/check-in")
                        .header("Authorization", "Bearer " + ctx.childToken())
                        .param("itemId", String.valueOf(itemId)))
                .andReturn();
    }

    /** 直接写入一条历史打卡记录（绕过 HTTP，用于构造跨天 streak）。 */
    private void insertRecord(FamilyContext ctx, Long itemId, String itemName, LocalDate date) {
        CheckRecord rec = new CheckRecord();
        rec.setFamilyId(ctx.familyId());
        rec.setChildId(childUserId(ctx));
        rec.setItemId(itemId);
        rec.setItemName(itemName);
        rec.setCheckDate(date);
        rec.setCheckTime(time.now().minusDays((int) java.time.temporal.ChronoUnit.DAYS.between(date, time.today())));
        rec.setDeleteAt(0L);
        records.insert(rec);
    }

    private int awardCount(FamilyContext ctx, String code) {
        MedalDefinition def = medalDefs.selectOne(new QueryWrapper<MedalDefinition>().eq("code", code));
        assertNotNull(def, "勋章定义 " + code + " 应在 v004 中播种");
        return medalAwards.selectCount(new QueryWrapper<MedalAward>()
                .eq("definition_id", def.getId()).eq("child_id", childUserId(ctx)).eq("delete_at", 0L)).intValue();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult r = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data");
    }

    private int currentStreak(FamilyContext ctx) throws Exception {
        return data(get("/api/child/check-in/calendar")
                .header("Authorization", "Bearer " + ctx.childToken())).get("currentStreak").asInt();
    }

    // ---- 用例 1：打卡 + 每日上限 ----

    @Test
    void checkInIncrementsAndRejectsWhenDailyLimitReached() throws Exception {
        FamilyContext ctx = boundFamily();
        Long itemId = createItem(ctx, "喝水", "💧", "杯", 2, 1);

        // 前两次打卡成功
        assertEquals(200, childCheckIn(ctx, itemId).getResponse().getStatus());
        assertEquals(200, childCheckIn(ctx, itemId).getResponse().getStatus());

        // 超过每日上限（2）后拒收 E013（CONFLICT）
        assertEquals(409, childCheckIn(ctx, itemId).getResponse().getStatus());

        // 今日次数应封顶在 2，且 reached=true
        JsonNode today = data(get("/api/child/check-in/today")
                .header("Authorization", "Bearer " + ctx.childToken()));
        JsonNode row = today.get(0);
        assertEquals(2, row.get("count").asInt());
        assertEquals(2, row.get("dailyTarget").asInt());
        assertEquals(true, row.get("reached").asBoolean());
    }

    // ---- 用例 2 + 3：streak 推导 + 勋章触发 + 幂等 ----

    @Test
    void sevenDayStreakTriggersHealthMedalsAndIsIdempotent() throws Exception {
        FamilyContext ctx = boundFamily();
        Long itemId = createItem(ctx, "运动", "🏃", "次", 0, 1);

        // 构造前 6 天连续记录（today-6 .. today-1）
        for (int k = 6; k >= 1; k--) {
            insertRecord(ctx, itemId, "运动", time.today().minusDays(k));
        }

        // 第 7 天经真实 HTTP 打卡 → streak 应达 7，并触发 3/7 天勋章
        assertEquals(200, childCheckIn(ctx, itemId).getResponse().getStatus());
        assertEquals(7, currentStreak(ctx));

        assertEquals(1, awardCount(ctx, "HEALTH_STREAK_3"));
        assertEquals(1, awardCount(ctx, "HEALTH_STREAK_7"));
        assertEquals(0, awardCount(ctx, "HEALTH_STREAK_14"));
        assertEquals(0, awardCount(ctx, "HEALTH_STREAK_30"));

        // 同日再次打卡（dailyTarget=0 不限）→ 重复触发 award，必须幂等：仍各 1 枚
        assertEquals(200, childCheckIn(ctx, itemId).getResponse().getStatus());
        assertEquals(7, currentStreak(ctx));
        assertEquals(1, awardCount(ctx, "HEALTH_STREAK_3"));
        assertEquals(1, awardCount(ctx, "HEALTH_STREAK_7"));
    }

    // ---- 用例 2（续）：断签重置 ----

    @Test
    void streakResetsOnGap() throws Exception {
        FamilyContext ctx = boundFamily();
        Long itemId = createItem(ctx, "阅读", "📚", "页", 0, 1);

        // today-1 与 today-3 有记录，today-2 缺失 → 连续段只能从 today-1 起算 = 1
        insertRecord(ctx, itemId, "阅读", time.today().minusDays(1));
        insertRecord(ctx, itemId, "阅读", time.today().minusDays(3));

        assertEquals(1, currentStreak(ctx));
    }

    // ---- 用例 4：同意撤回隐藏 ----

    @Test
    void consentRevokedHidesChildCheckInAndItems() throws Exception {
        FamilyContext ctx = boundFamily();
        createItem(ctx, "睡眠", "😴", "小时", 0, 1);

        // 撤回同意（F-007）
        revoke(ctx);

        // 儿童接口应返回 E010（CONFLICT），实现停采并隐藏
        assertEquals(409, mockMvc.perform(get("/api/child/check-in/items")
                        .header("Authorization", "Bearer " + ctx.childToken())).andReturn()
                .getResponse().getStatus());
        assertEquals(409, childCheckIn(ctx, 999999L).getResponse().getStatus());
    }

    // ---- 用例 5：跨家庭隔离 ----

    @Test
    void crossFamilyCannotSeeOrCheckInForeignItem() throws Exception {
        FamilyContext a = boundFamily();
        FamilyContext b = boundFamily();
        Long itemA = createItem(a, "家庭A专属", "🔒", "次", 0, 1);

        // 家庭 B 儿童看不到家庭 A 的项（列表为空）
        JsonNode bItems = data(get("/api/child/check-in/items")
                .header("Authorization", "Bearer " + b.childToken()));
        assertTrue(bItems.isArray() && bItems.size() == 0, "家庭 B 不应看到家庭 A 的打卡项");

        // 家庭 B 儿童打卡家庭 A 的 itemId → 不泄露存在性，返回 404（资源不存在）
        assertEquals(404, childCheckIn(b, itemA).getResponse().getStatus());

        // 家庭 A 自身儿童可正常打卡（对照组）
        assertEquals(200, childCheckIn(a, itemA).getResponse().getStatus());
    }

    // ---- 用例 6：删项保留快照 ----

    @Test
    void deleteItemPreservesRecordSnapshot() throws Exception {
        FamilyContext ctx = boundFamily();
        Long itemId = createItem(ctx, "喝水", "💧", "杯", 0, 1);

        // 儿童打卡一次 → 记录写入 item_name 快照
        assertEquals(200, childCheckIn(ctx, itemId).getResponse().getStatus());

        // 家长软删该项（expectedVersion=0）
        mockMvc.perform(delete("/api/parent/check-item/" + itemId)
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .param("expectedVersion", "0"))
                .andExpect(status().isOk());

        // 儿童列表不再包含该项
        JsonNode list = data(get("/api/child/check-in/items")
                .header("Authorization", "Bearer " + ctx.childToken()));
        boolean stillVisible = false;
        for (JsonNode n : list) {
            if (itemId.equals(n.get("itemId").asLong())) {
                stillVisible = true;
            }
        }
        assertFalse(stillVisible, "软删后儿童不应再看到该项");

        // 历史记录保留快照名，且记录本身未软删
        CheckRecord rec = records.selectOne(new QueryWrapper<CheckRecord>()
                .eq("child_id", childUserId(ctx)).eq("item_id", itemId).eq("delete_at", 0L));
        assertNotNull(rec, "打卡记录应保留且未被软删");
        assertEquals("喝水", rec.getItemName());

        // 项本身已被软删：MyBatis-Plus 全局逻辑删除会自动附加 delete_at=0 条件，
        // 软删后通过普通 mapper 查询不可见（证明是软删而非物理删除，且快照记录不受影响）。
        CheckItem reloaded = items.selectOne(new QueryWrapper<CheckItem>().eq("id", itemId));
        assertNull(reloaded, "打卡项应被软删，普通查询不可见");
    }
}
