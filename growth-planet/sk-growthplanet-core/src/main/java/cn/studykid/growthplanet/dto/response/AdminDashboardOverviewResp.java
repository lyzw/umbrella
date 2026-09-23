package cn.studykid.growthplanet.dto.response;

import java.util.List;

/** 运营总览看板。 */
public record AdminDashboardOverviewResp(
        long familiesTotal,
        long familiesNew30d,
        long childrenTotal,
        long childrenNew30d,
        long activeFamilies7d,
        long activeChildren7d,
        long confirmPending,
        long confirmPassed,
        long confirmOverLimit,
        long ugcPending,
        long privacyBacklog,
        List<MiniStat> highlights) {

    /** 关键指标卡。 */
    public record MiniStat(String label, long value, String hint) {
    }
}
