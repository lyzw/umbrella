package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 「今天吃什么」引导式推荐：在指定菜单内按画像 + 历史 + 忌口打分排序。
 * 安全红线：含过敏原的菜品一律不出现在结果里（不会被分数覆盖）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long menuId;
    private LocalDate menuDate;
    private String mealType;
    private List<Item> dishes;

    /** 一条推荐，附可解释的推荐理由（前端直接展示文案）。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String type;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long id;
        private String name;
        private String imageUrl;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long categoryId;
        private String categoryName;
        /** 整数分（避免浮点噪声导致排序/断言不稳定）。 */
        private int score;
        private List<String> reasons;
    }
}
