package cn.studykid.growthplanet.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新增儿童成长记录（家长录入）。recordDate 不传默认当天，ageYears 用于分位对照、可空。
 */
@Data
public class GrowthRecordReq {
    @NotNull @Positive
    private Long childId;
    @NotNull
    @Pattern(regexp = "BOY|GIRL", message = "性别仅支持 BOY/GIRL")
    private String gender;
    private LocalDate recordDate;
    @NotNull
    @DecimalMin("30.00") @DecimalMax("250.00")
    private BigDecimal heightCm;
    @NotNull
    @DecimalMin("1.00") @DecimalMax("200.00")
    private BigDecimal weightKg;
    @Min(1) @Max(19)
    private Integer ageYears;
    @Size(max = 200)
    private String note;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported growth record field");
    }
}
