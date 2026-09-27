package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.dto.response.GrowthRecordItem;
import cn.studykid.growthplanet.dto.response.GrowthRecordsResp;
import cn.studykid.growthplanet.dto.response.GrowthSaveResp;
import cn.studykid.growthplanet.dto.response.GrowthStandardResp;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 儿童成长记录集成测试：家长录入 → BMI 计算 → 分位评估 → WHO 标准分位表。
 */
class GrowthRecordIT extends BaseIT {

    private FamilyContext ready() throws Exception {
        FamilyContext ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        mockMvc.perform(post("/api/mini/child/profile")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content(profileJson(ctx)))
                .andExpect(status().isOk())
                .andReturn();
        return ctx;
    }

    private String recordJson(FamilyContext ctx, String date, String height, String weight,
                              String age, String note) {
        return "{\"childId\":\"" + childUserId(ctx) + "\",\"gender\":\"BOY\","
                + "\"recordDate\":\"" + date + "\",\"heightCm\":\"" + height
                + "\",\"weightKg\":\"" + weight + "\",\"ageYears\":" + age
                + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}";
    }

    @Test
    void parentSavesAndListsGrowthRecordsWithEvaluation() throws Exception {
        FamilyContext ctx = ready();

        MvcResult save = mockMvc.perform(post("/api/mini/growth/records")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content(recordJson(ctx, "2026-09-20", "126.00", "25.00", "7", "体检后")))
                .andExpect(status().isOk())
                .andReturn();
        GrowthSaveResp saveResp = dataOf(save, GrowthSaveResp.class);
        assertNotNull(saveResp.getId());

        mockMvc.perform(post("/api/mini/growth/records")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content(recordJson(ctx, "2026-09-01", "127.00", "25.40", "7", null)))
                .andExpect(status().isOk());

        MvcResult list = mockMvc.perform(get("/api/mini/growth/records")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .param("childId", String.valueOf(childUserId(ctx))))
                .andExpect(status().isOk())
                .andReturn();
        GrowthRecordsResp resp = dataOf(list, GrowthRecordsResp.class);
        assertEquals(2, resp.getItems().size());
        // 最新在前
        assertEquals("2026-09-20", resp.getItems().get(0).getRecordDate().toString());
        GrowthRecordItem first = resp.getItems().get(0);
        assertEquals("体检后", first.getNote());
        // BMI = 25.00 / (1.26)^2 ≈ 15.7
        assertEquals("15.7", first.getBmi().toPlainString());
        // 7 岁男孩：身高 126.0 / 体重 25.0 均落在 P50–P85 之间
        assertEquals("P50–P85", first.getHeightBand());
        assertEquals("P50–P85", first.getWeightBand());
        assertEquals("正常", first.getBmiCategory());

        // 最新点接口
        MvcResult latest = mockMvc.perform(get("/api/mini/growth/records/latest")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .param("childId", String.valueOf(childUserId(ctx))))
                .andExpect(status().isOk())
                .andReturn();
        GrowthRecordItem latestResp = dataOf(latest, GrowthRecordItem.class);
        assertEquals("2026-09-20", latestResp.getRecordDate().toString());

        // 儿童本人可读
        MvcResult childList = mockMvc.perform(get("/api/mini/growth/records")
                        .header("Authorization", "Bearer " + ctx.childToken())
                        .param("childId", String.valueOf(childUserId(ctx))))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(2, dataOf(childList, GrowthRecordsResp.class).getItems().size());
    }

    @Test
    void childCannotSaveGrowthRecord() throws Exception {
        FamilyContext ctx = ready();
        mockMvc.perform(post("/api/mini/growth/records")
                        .header("Authorization", "Bearer " + ctx.childToken())
                        .contentType(JSON)
                        .content(recordJson(ctx, "2026-09-20", "128.50", "26.20", "7", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void standardTableServesWhoRows() throws Exception {
        String token = loginAndSelectRole("growth_std_" + System.nanoTime(), RoleEnum.PARENT);
        MvcResult res = mockMvc.perform(get("/api/mini/growth/standard")
                        .header("Authorization", "Bearer " + token)
                        .param("ageBand", "WHO 2007")
                        .param("gender", "BOY")
                        .param("metric", "HEIGHT"))
                .andExpect(status().isOk())
                .andReturn();
        GrowthStandardResp resp = dataOf(res, GrowthStandardResp.class);
        assertEquals("WHO 2007", resp.getAgeBand());
        assertEquals("BOY", resp.getGender());
        assertEquals("cm", resp.getUnit());
        assertEquals(15, resp.getRows().size());
        GrowthStandardResp.GrowthStandardRow seven = resp.getRows().stream()
                .filter(r -> r.getAgeYears() == 7).findFirst().orElseThrow();
        assertEquals(114.0, seven.getP3());
        assertEquals(121.8, seven.getP50());
        assertEquals(131.0, seven.getP97());
        assertTrue(seven.getP15() < seven.getP50() && seven.getP85() > seven.getP50());

        // 0-5 岁 WHO 2006 女孩体重
        MvcResult res2 = mockMvc.perform(get("/api/mini/growth/standard")
                        .header("Authorization", "Bearer " + token)
                        .param("ageBand", "WHO 2006")
                        .param("gender", "GIRL")
                        .param("metric", "WEIGHT"))
                .andExpect(status().isOk())
                .andReturn();
        GrowthStandardResp resp2 = dataOf(res2, GrowthStandardResp.class);
        assertEquals(6, resp2.getRows().size());
    }

    @Test
    void recordWithoutAgeHasNoEvaluation() throws Exception {
        FamilyContext ctx = ready();
        MvcResult save = mockMvc.perform(post("/api/mini/growth/records")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content("{\"childId\":\"" + childUserId(ctx) + "\",\"gender\":\"BOY\","
                                + "\"recordDate\":\"2026-09-20\",\"heightCm\":\"128.50\",\"weightKg\":\"26.20\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertNotNull(dataOf(save, GrowthSaveResp.class).getId());

        MvcResult list = mockMvc.perform(get("/api/mini/growth/records")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .param("childId", String.valueOf(childUserId(ctx))))
                .andExpect(status().isOk())
                .andReturn();
        GrowthRecordItem item = dataOf(list, GrowthRecordsResp.class).getItems().get(0);
        assertEquals(null, item.getHeightBand());
        assertEquals("暂无阈值", item.getBmiCategory());
    }
}
