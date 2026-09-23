package cn.studykid.growthplanet.dto.response;

import java.util.List;

/** 餐食看板。 */
public record AdminDashboardMealsResp(
        List<WantEatTop> wantEatTop,
        long confirmTotal,
        double overLimitRate,
        List<SourceRatio> sourceRatio) {

    /** 想吃热度 Top 单项。 */
    public record WantEatTop(String dishType, long dishId, String dishName, long count) {
    }

    /** 确认单来源占比单项。 */
    public record SourceRatio(String sourceType, long count, double ratio) {
    }
}
