package cn.studykid.growthplanet.dto.response;

import cn.studykid.growthplanet.entity.DishRef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日想吃响应：返回某 (childId, menuDate, mealType) 下的想吃菜品引用列表。
 * 用于 mark-favorite 后的刷新，以及家长/本人查询想吃清单。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WantEatResp {
    private Long childId;
    private LocalDate menuDate;
    private String mealType;
    private List<DishRef> wantEat;
}
