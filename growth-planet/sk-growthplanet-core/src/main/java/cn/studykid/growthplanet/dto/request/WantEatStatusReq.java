package cn.studykid.growthplanet.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 家长流转「想吃」状态：MARKED（未处理）/ ADOPTED（已采购或已安排）/ COOKED（已做）。
 * expectedVersion 用于乐观锁，与确认单审批同款范式。
 */
@Data
@NoArgsConstructor
public class WantEatStatusReq {
    @NotBlank
    @Pattern(regexp = "MARKED|ADOPTED|COOKED")
    private String status;

    @NotNull
    @PositiveOrZero
    private Integer expectedVersion;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported want-eat status field: " + name);
    }
}
