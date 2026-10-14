package cn.studykid.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * WHO 生长标准分位表（按性别 + 指标返回每岁一行）。rows 为空表示该组合暂不提供。
 * 数值为示意数据，正式版须以 WHO 官方 LMS 参数校准。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthStandardResp {
    private String ageBand;
    private String gender;
    private String metric;
    private String unit;
    private List<GrowthStandardRow> rows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrowthStandardRow {
        private Integer ageYears;
        private double p3;
        private double p15;
        private double p50;
        private double p85;
        private double p97;
    }
}
