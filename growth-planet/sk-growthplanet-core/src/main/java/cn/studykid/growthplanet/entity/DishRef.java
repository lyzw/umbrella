package cn.studykid.growthplanet.entity;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜品引用值对象：菜单 dish_ids 与点单行项引用菜品时，用 {type, id} 区分预置菜品与家庭私有菜品。
 * type=PRESET 查 life_dish；type=FAMILY 查 life_family_dish。
 */
@Data
@NoArgsConstructor
public class DishRef {
    @NotNull @Pattern(regexp = "PRESET|FAMILY")
    private String type;
    @NotNull @Positive
    private Long id;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported dish ref field: " + name);
    }
}
