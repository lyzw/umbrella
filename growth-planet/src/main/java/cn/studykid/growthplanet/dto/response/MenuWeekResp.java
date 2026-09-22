package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 周视图：区间内「每一天 × 每一餐」的菜单概览，用于家长整周发布与孩子提前挑选。
 * 契约：**区间内每一天 × 三餐都会返回格子**（未发布 → {@code menuId=null, status=null, dishCount=0}），不丢格。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuWeekResp {
    private LocalDate from;
    private LocalDate to;
    /** 服务端「今天」，供前端判定"哪几天已过去"。 */
    private LocalDate today;
    private List<Day> days;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Day {
        private LocalDate menuDate;
        private List<Meal> meals;
    }

    /**
     * 某天一餐的概览。
     * {@code wantEatCount}/{@code marked} 仅孩子周视图填值；家长周视图固定 0/false
     * （家长要看"谁想吃什么"用 {@code GET /api/parent/want-eat} 的清单视图）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meal {
        private String mealType;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long menuId;
        private String sourceType;
        private String status;
        private int dishCount;
        private int wantEatCount;
        private boolean marked;
    }
}
