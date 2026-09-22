package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 运营端勋章定义创建/编辑请求（M3 勋章配置）。
 * conditionType：COUNT 累计次数 / STREAK 连续天数；status：NORMAL 启用 / DISABLED 停用。
 */
@Data
public class AdminMedalReq {

    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "[A-Z0-9_]+", message = "code 须为大写字母/数字/下划线")
    private String code;

    @NotBlank
    @Size(max = 64)
    private String name;

    @Size(max = 255)
    private String description;

    @Size(max = 255)
    private String icon;

    @NotBlank
    @Size(max = 32)
    private String category;

    @NotBlank
    @Pattern(regexp = "COUNT|STREAK")
    private String conditionType;

    @NotNull
    @Min(1)
    @Max(9999)
    private Integer threshold;

    @Min(0)
    @Max(999)
    private Integer sortOrder = 0;

    @Pattern(regexp = "NORMAL|DISABLED")
    private String status = "NORMAL";
}
