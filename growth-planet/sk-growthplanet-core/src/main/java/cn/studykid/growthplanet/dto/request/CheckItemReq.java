package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 打卡项配置请求（F-033）。familyId 由后端派生，客户端禁传。 */
@Data
@EqualsAndHashCode(callSuper = false)
public class CheckItemReq extends StrictRequest {
    @NotBlank
    @Size(max = 32)
    private String name;
    @Size(max = 64)
    private String icon;
    @Size(max = 8)
    private String unit;
    @Min(0)
    @Max(9999)
    private Integer dailyTarget;
    @Min(0)
    @Max(9999)
    private Integer sortOrder;
}
