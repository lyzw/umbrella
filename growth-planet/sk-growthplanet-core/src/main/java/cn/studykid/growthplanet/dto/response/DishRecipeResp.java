package cn.studykid.growthplanet.dto.response;

import cn.studykid.growthplanet.dto.DishIngredient;

import java.util.List;

/**
 * 菜品配方响应（食材明细 + 做法 + 轻量烹饪属性）。
 * <p>
 * 只在详情路径与 C 端专用配方端点填充；列表 / 菜单 / 想吃清单路径为 null，
 * 使「本响应没带配方」与「这道菜确实没配方」（ingredients=[]、cookSteps=[]）语义精确分开。
 */
public record DishRecipeResp(List<DishIngredient> ingredients, List<String> cookSteps, String cookTips,
        Integer cookMinutes, Integer servings, String difficulty) {
}
