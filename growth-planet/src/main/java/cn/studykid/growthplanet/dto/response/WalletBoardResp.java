package cn.studykid.growthplanet.dto.response;

import java.util.List;

/**
 * 家长端零花钱看板（F-024 家长汇总）：家庭虚拟总额、本周支出/发放，以及各子女余额与本周已用。
 * 汇总口径均基于本周（周一起算），与额度周期保持一致；“已用”只统计支出（DEDUCT），奖励与发放不计入。
 */
public record WalletBoardResp(String familyId, String totalBalance, String weekSpend, String weekGrant,
        List<ChildSummary> children) {

    /** 单个子女概览。 */
    public record ChildSummary(String childId, String balance, String weekUsed) {
    }
}
