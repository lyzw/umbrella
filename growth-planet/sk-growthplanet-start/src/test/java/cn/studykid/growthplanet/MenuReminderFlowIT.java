package cn.studykid.growthplanet;

import cn.studykid.growthplanet.entity.Notice;
import cn.studykid.growthplanet.mapper.NoticeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MenuReminderFlowIT extends BaseIT {
    @Autowired
    NoticeMapper notices;

    @Test
    void boundChildCanCreateOneReminderPerBusinessDay() throws Exception {
        FamilyContext ctx = boundFamily();

        reminder(ctx.childToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"));
        reminder(ctx.childToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ALREADY_EXISTS"));

        assertEquals(1, notices.selectCount(new QueryWrapper<Notice>()
                .eq("event_type", "MENU_REMINDER")
                .eq("receiver_id", parentId(ctx))
                .eq("channel", "IN_APP")));
        assertEquals(1, notices.selectCount(new QueryWrapper<Notice>()
                .eq("event_type", "MENU_REMINDER")
                .eq("receiver_id", parentId(ctx))
                .eq("channel", "SUBSCRIBE")));
        Notice subscribe = notices.selectOne(new QueryWrapper<Notice>()
                .eq("event_type", "MENU_REMINDER")
                .eq("receiver_id", parentId(ctx))
                .eq("channel", "SUBSCRIBE"));
        assertEquals("UNAUTHORIZED", subscribe.getStatus());
    }

    @Test
    void parentCannotUseChildReminderEndpoint() throws Exception {
        FamilyContext ctx = boundFamily();

        reminder(ctx.parentToken()).andExpect(status().isForbidden());
    }

    @Test
    void unboundChildCannotCreateReminder() throws Exception {
        FamilyContext ctx = setupFamily();

        reminder(ctx.childToken()).andExpect(status().isForbidden());
    }

    @Test
    void concurrentRequestsCreateAtMostOneReminder() throws Exception {
        FamilyContext ctx = boundFamily();
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<MvcResult>> requests = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                requests.add(() -> reminder(ctx.childToken()).andReturn());
            }
            var futures = executor.invokeAll(requests);
            List<Integer> statuses = new ArrayList<>();
            for (var future : futures) {
                statuses.add(future.get(10, TimeUnit.SECONDS).getResponse().getStatus());
            }
            assertTrue(statuses.stream().allMatch(value -> value == 200));
        }

        assertEquals(1, notices.selectCount(new QueryWrapper<Notice>()
                .eq("event_type", "MENU_REMINDER")
                .eq("receiver_id", parentId(ctx))
                .eq("channel", "IN_APP")));
    }

    private org.springframework.test.web.servlet.ResultActions reminder(String token) throws Exception {
        return mockMvc.perform(post("/api/mini/child/menu-reminder")
                .header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .content("{}"));
    }

    private FamilyContext boundFamily() throws Exception {
        FamilyContext ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        return ctx;
    }

    private Long parentId(FamilyContext ctx) {
        return jwtUtil.getUserId(jwtUtil.parse(ctx.parentToken()));
    }
}
