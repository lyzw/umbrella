package cn.studykid.growthplanet.dto.response;

import java.util.List;

/** 家务与健康看板。 */
public record AdminDashboardChoresResp(
        double taskCompletionRate,
        double checkCoverageRate,
        List<StreakBucket> streakDistribution) {

    /** 连续打卡天数分布单项。 */
    public record StreakBucket(String bucket, long count) {
    }
}
