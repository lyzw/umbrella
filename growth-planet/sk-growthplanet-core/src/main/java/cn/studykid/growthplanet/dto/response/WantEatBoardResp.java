package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 家长「按日想吃清单」响应：以「日期 × 餐次」为主轴聚合孩子的想吃标记，
 * 附带采纳状态与区间采购汇总。对应 R3（家长视图缺口）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WantEatBoardResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long childId;
    private LocalDate from;
    private LocalDate to;
    /** 服务端「今天」，供前端判定过期与默认区间。 */
    private LocalDate today;
    private List<Day> days;
    /** 过期（menuDate &lt; today）且仍为 MARKED 的条数。 */
    private int expiredCount;
    private Summary summary;

    /** 某一天的想吃集合。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Day {
        private LocalDate menuDate;
        private List<Meal> meals;
    }

    /** 某天某一餐的想吃集合。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meal {
        private String mealType;
        /** 标记时所在菜单来源 SCHOOL/FAMILY（上下文）。 */
        private String sourceType;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long menuId;
        private List<Item> items;
    }

    /** 单条想吃标记（含菜品展示信息与状态）。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long wantEatId;
        private String type;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long id;
        private String name;
        private String imageUrl;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long categoryId;
        private String categoryName;
        private String status;
        /** 乐观锁版本，供家长端调用状态流转接口时回传。 */
        private Integer version;
        /** menuDate &lt; today。 */
        private boolean expired;
        private boolean allergyConflict;
        private boolean disliked;
        /** 菜品已下架/删除导致取不到展示信息（不丢行，以 null 字段占位）。 */
        private boolean missing;
    }

    /** 区间采购/烹饪汇总（按菜品去重；当前数据模型无食材字段，故不按食材聚合）。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int totalItems;
        private List<SummaryDish> dishes;
    }

    /** 汇总中的一道菜。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryDish {
        private String type;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long id;
        private String name;
        /** 区间内出现的天数。 */
        private int count;
        private List<LocalDate> dates;
    }
}
