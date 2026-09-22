package cn.studykid.growthplanet;

import cn.studykid.growthplanet.dto.response.ScheduleOccurrenceResp;
import cn.studykid.growthplanet.dto.response.ScheduleResp;
import cn.studykid.growthplanet.service.ScheduleReminderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 日程提醒集成测试（F-036~F-038 / T-020~T-022）：
 * 1) 家长建日程 → 儿童列表/按日展开可见。
 * 2) 每周重复仅在选择星期发生。
 * 3) 取消后不再出现在列表与展开结果中。
 * 4) 到点提醒只投递一次（幂等）、儿童与创建家长各收一份、错过的历史时刻不补发。
 * 5) 过去日期被拒绝。
 */
class ScheduleFlowIT extends BaseIT {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private ScheduleReminderService reminderService;

    private FamilyContext boundFamily() throws Exception {
        FamilyContext ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        return ctx;
    }

    @Test
    void parentCreatesScheduleChildSeesIt() throws Exception {
        FamilyContext ctx = boundFamily();
        Long childId = childUserId(ctx);

        mockMvc.perform(post("/api/mini/schedule")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content(createBody(childId, "写语文作业", today(), "08:00", "ONCE", null, null)))
                .andExpect(status().isOk());

        List<ScheduleResp> list = schedulesOf(ctx.childToken(), childId);
        assertEquals(1, list.size());
        assertEquals("写语文作业", list.get(0).title());
        assertEquals("NORMAL", list.get(0).status());
        assertEquals(today(), list.get(0).scheduleDate());

        List<ScheduleOccurrenceResp> occurrences = occurrencesOf(ctx.childToken(), childId, today(), today());
        assertEquals(1, occurrences.size());
        assertEquals(today(), occurrences.get(0).date());
        assertEquals("08:00", occurrences.get(0).time());
        assertFalse(occurrences.get(0).reminded(), "尚未投递前不应标记为已提醒");
    }

    @Test
    void weeklyRepeatExpandsOnlySelectedWeekdays() throws Exception {
        FamilyContext ctx = boundFamily();
        Long childId = childUserId(ctx);
        int weekday = LocalDate.now(ZONE).getDayOfWeek().getValue();

        mockMvc.perform(post("/api/mini/schedule")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content(createBody(childId, "钢琴课", today(), "18:30", "WEEKLY", String.valueOf(weekday), 30)))
                .andExpect(status().isOk());

        // 覆盖今日起 14 天，应恰好命中本周与下周两个同名星期
        List<ScheduleOccurrenceResp> occurrences = occurrencesOf(ctx.childToken(), childId, today(), plusDays(13));
        assertEquals(2, occurrences.size());
        assertEquals(today(), occurrences.get(0).date());
        assertEquals(plusDays(7), occurrences.get(1).date());
    }

    @Test
    void cancelHidesScheduleFromListAndOccurrences() throws Exception {
        FamilyContext ctx = boundFamily();
        Long childId = childUserId(ctx);

        MvcResult created = mockMvc.perform(post("/api/mini/schedule")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content(createBody(childId, "吃药", today(), "20:00", "DAILY", null, 10)))
                .andExpect(status().isOk()).andReturn();
        ScheduleResp schedule = dataOf(created, ScheduleResp.class);

        mockMvc.perform(post("/api/mini/schedule/" + schedule.scheduleId() + "/cancel")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        assertTrue(schedulesOf(ctx.childToken(), childId).isEmpty(), "取消后不应出现在列表");
        assertTrue(occurrencesOf(ctx.childToken(), childId, today(), plusDays(7)).isEmpty(), "取消后不应再展开");
    }

    @Test
    void reminderDeliveredOnceToChildAndParent() throws Exception {
        FamilyContext ctx = boundFamily();
        Long childId = childUserId(ctx);

        MvcResult created = mockMvc.perform(post("/api/mini/schedule")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content(createBody(childId, "晨读", today(), "00:00", "DAILY", null, 0)))
                .andExpect(status().isOk()).andReturn();
        ScheduleResp schedule = dataOf(created, ScheduleResp.class);
        assertEquals(today(), schedule.scheduleDate());

        assertFalse(occurrencesOf(ctx.parentToken(), childId, today(), today()).get(0).reminded(),
                "投递前应未标记");

        // 首次扫描：至少投递本日程
        assertTrue(reminderService.deliverDueReminders() >= 1, "到点日程应被投递");
        assertTrue(occurrencesOf(ctx.parentToken(), childId, today(), today()).get(0).reminded(),
                "投递后应按发生日标记已提醒");

        // 同一发生日重复扫描不再投递
        assertEquals(0, reminderService.deliverDueReminders(), "同一发生日不得重复投递");

        // 儿童与创建家长各收一份站内提醒（通知体不含日程编号，故只断言事件类型存在）
        assertTrue(sawScheduleRemind(ctx.childToken()), "儿童应收到日程提醒");
        assertTrue(sawScheduleRemind(ctx.parentToken()), "创建家长应收到日程提醒");
    }

    @Test
    void pastDateRejected() throws Exception {
        FamilyContext ctx = boundFamily();
        Long childId = childUserId(ctx);

        mockMvc.perform(post("/api/mini/schedule")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content(createBody(childId, "过期日程", minusDays(1), "08:00", "ONCE", null, null)))
                .andExpect(status().isBadRequest());
    }

    // ---------- 辅助 ----------

    private String createBody(Long childId, String title, String date, String time, String repeat,
            String weekdays, Integer remindMinutes) {
        StringBuilder body = new StringBuilder("{\"childId\":").append(childId)
                .append(",\"title\":\"").append(title).append("\"")
                .append(",\"category\":\"HOMEWORK\"")
                .append(",\"scheduleDate\":\"").append(date).append("\"")
                .append(",\"scheduleTime\":\"").append(time).append("\"")
                .append(",\"repeatType\":\"").append(repeat).append("\"");
        if (weekdays != null) {
            body.append(",\"repeatWeekdays\":\"").append(weekdays).append("\"");
        }
        if (remindMinutes != null) {
            body.append(",\"remindMinutes\":").append(remindMinutes);
        }
        return body.append("}").toString();
    }

    private List<ScheduleResp> schedulesOf(String token, Long childId) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/mini/schedule/list")
                        .header("Authorization", "Bearer " + token)
                        .param("childId", String.valueOf(childId)))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        return objectMapper.convertValue(data,
                objectMapper.getTypeFactory().constructCollectionType(List.class, ScheduleResp.class));
    }

    private List<ScheduleOccurrenceResp> occurrencesOf(String token, Long childId, String from, String to)
            throws Exception {
        MvcResult res = mockMvc.perform(get("/api/mini/schedule/occurrences")
                        .header("Authorization", "Bearer " + token)
                        .param("childId", String.valueOf(childId)).param("from", from).param("to", to))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        return objectMapper.convertValue(data,
                objectMapper.getTypeFactory().constructCollectionType(List.class, ScheduleOccurrenceResp.class));
    }

    private boolean sawScheduleRemind(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/mini/notices")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "1").param("pageSize", "100"))
                .andExpect(status().isOk()).andReturn();
        JsonNode items = objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("items");
        for (JsonNode item : items) {
            if ("SCHEDULE_REMIND".equals(item.get("eventType").asText())) {
                return true;
            }
        }
        return false;
    }

    private String today() {
        return LocalDate.now(ZONE).toString();
    }

    private String plusDays(int days) {
        return LocalDate.now(ZONE).plusDays(days).toString();
    }

    private String minusDays(int days) {
        return LocalDate.now(ZONE).minusDays(days).toString();
    }
}
