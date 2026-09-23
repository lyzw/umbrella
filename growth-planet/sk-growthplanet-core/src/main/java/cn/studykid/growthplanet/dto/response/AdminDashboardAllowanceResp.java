package cn.studykid.growthplanet.dto.response;

/** 零花钱看板（金额单位：分）。 */
public record AdminDashboardAllowanceResp(
        long grantedTotal,
        long consumedTotal,
        long balanceTotal,
        double grantConsumeRatio) {
}
