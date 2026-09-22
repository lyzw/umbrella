package cn.studykid.growthplanet.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MarkFavoriteReq {
    @NotNull @Positive
    private Long dishId;
    /** 必填：PRESET / FAMILY。由每日菜单的菜品引用权威给出，后端据此校验菜品确属该菜单（含消除 PRESET/FAMILY 同号歧义）。 */
    private String dishType;
    @NotNull
    private Boolean favorite;
    /** 标记时所在菜单 id：后端据其派生 source_type 并校验菜单归属，避免客户端伪造归属字段。 */
    @NotNull @Positive
    private Long menuId;
    /** 想吃所属日期（每日想吃的维度之一）。 */
    @NotNull
    private LocalDate menuDate;
    /** 想吃所属餐次（每日想吃的维度之一）。 */
    @NotNull
    private String mealType;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported favorite field");
    }
}
