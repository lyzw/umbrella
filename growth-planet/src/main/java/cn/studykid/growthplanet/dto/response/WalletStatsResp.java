package cn.studykid.growthplanet.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 消费预算可视化（F-025）：按 range 输出趋势序列，以及支出/收入两张分类占比。
 * spendTrend 只统计支出（DEDUCT）；WEEK 按日 7 点，MONTH 按周分桶 4~5 点，label 供图表横轴直接使用。
 * spendCategories 与 grantCategories 分别对应“消费占比”和“收入来源占比”两张图；
 * percent 为占各自合计的整数百分比（合计为 0 时返回空列表，由前端展示空态）。
 */
public record WalletStatsResp(String childId, String range, LocalDate from, LocalDate to,
        List<TrendPoint> spendTrend, List<CategorySlice> spendCategories, List<CategorySlice> grantCategories,
        String totalSpend, String totalGrant) {

    /** 趋势序列单点：label 为横轴文案（周一…/第1周），date 为该点起始日。 */
    public record TrendPoint(String label, LocalDate date, String amount) {
    }

    /** 分类占比切片：scene 为流水场景枚举原值，中文与配色由前端映射。 */
    public record CategorySlice(String scene, String amount, int percent) {
    }
}
