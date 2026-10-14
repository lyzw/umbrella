package cn.studykid.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单条成长记录（含服务端计算的分位区间与 BMI 类别评估）。
 * heightBand / weightBand 示例："P50–P85"；bmiCategory：消瘦 / 正常 / 超重 / 肥胖 / 暂无阈值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthRecordItem {
    private Long id;
    private LocalDate recordDate;
    private String gender;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal bmi;
    private Integer ageYears;
    private String note;
    private String heightBand;
    private String weightBand;
    private String bmiCategory;
}
