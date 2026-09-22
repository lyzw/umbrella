package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 家务健康分页响应（M4）：分页行 + 全量口径状态汇总（完成率按全量过滤集而非当前页计算）。
 * 完成率 = CONFIRMED / 总数（总数为 0 时取 0）。
 */
@Data
@Builder
public class AdminChorePageResp {
    private List<AdminChoreInstanceResp> items;
    private long total;
    private int page;
    private int pageSize;
    /** 各状态行数（CLAIMED/SUBMITTED/CONFIRMED/REJECTED → 数量，仅含出现的键）。 */
    private Map<String, Long> statusCounts;
    /** 完成率（0~100，保留 1 位小数）。 */
    private double completionRate;
}
