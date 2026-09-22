package cn.studykid.growthplanet.dto.request;

import cn.studykid.growthplanet.entity.DishRef;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 家长「整周发布」批量请求：一次提交最多 21 条（7 天 × 3 餐）。
 * 语义：**逐条独立事务，允许部分成功**（单条失败不回滚整批），家长据响应逐条回补即可。
 */
@Data
public class MenuDailyBatchReq {
    @NotNull @Size(min = 1, max = 21)
    private List<@NotNull @Valid Item> items;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported batch field");
    }

    /** 单条待发布菜单；字段与 {@link MenuDailyReq} 对齐（批量接口仅用于 FAMILY，故不接受 school）。 */
    @Data
    public static class Item {
        @NotNull
        private LocalDate menuDate;
        @NotNull @Pattern(regexp = "BREAKFAST|LUNCH|DINNER")
        private String mealType;
        @NotNull @Size(min = 1, max = 50)
        private List<@NotNull @Valid DishRef> dishIds;
        @NotNull @Pattern(regexp = "DRAFT|PUBLISHED")
        private String status = "PUBLISHED";

        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Unsupported batch item field");
        }
    }
}
