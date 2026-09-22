package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 派生「爱吃/常点」视图：由历史每日想吃归纳得到，不是手动持久收藏。
 * 用于菜单页顶部快捷区，降低孩子的选择成本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrequentDishResp {
    private List<Item> dishes;

    /** 一道常吃菜。count = 统计窗口内出现的天数。 */
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
        private int count;
    }
}
