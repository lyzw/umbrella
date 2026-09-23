package cn.studykid.growthplanet.dto.response;

import java.util.List;

/** 勋章看板。 */
public record AdminDashboardMedalsResp(
        long awardTotal,
        double obtainRate,
        List<MedalTop> topMedals) {

    /** 热门勋章单项。 */
    public record MedalTop(long definitionId, String name, long count) {
    }
}
