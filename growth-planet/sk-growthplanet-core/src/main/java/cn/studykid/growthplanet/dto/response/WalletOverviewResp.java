package cn.studykid.growthplanet.dto.response;

/**
 * 零花钱主页/余额看板（F-024 儿童端）：余额、今日/本周已用与上限、本周剩余额度、本月已用统计。
 * monthUsed 仅作统计展示，现有额度模型无月上限（沿用单笔/每日/每周三档）。
 * weekProgress 为本周额度使用百分比（0~100，超额时按 100 截断）；weekStatus 取值 NORMAL/WARN/DANGER。
 */
public record WalletOverviewResp(String childId, String balance, String todayUsed, String dailyLimit,
        String weekUsed, String weeklyLimit, String weekRemaining, String monthUsed, int weekProgress,
        String weekStatus) {
}
