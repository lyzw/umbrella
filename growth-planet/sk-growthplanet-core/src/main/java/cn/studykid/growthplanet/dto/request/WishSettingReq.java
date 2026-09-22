package cn.studykid.growthplanet.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * 家长心愿菜单设置（P3）：可选菜品上限 + 功能开关。
 * expectedVersion 为 {@code usr_family_setting.version}；无设置行时传 0（表示"尚未配置"）。
 */
@Data
public class WishSettingReq {
    @NotNull @Min(1) @Max(10)
    private Integer maxDishes;
    @NotNull
    private Boolean enabled;
    @NotNull @PositiveOrZero
    private Integer expectedVersion;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported wish setting field");
    }
}
