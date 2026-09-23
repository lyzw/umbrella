package cn.studykid.growthplanet.dto;

/**
 * 食材条目：请求与响应共用（预置菜品与家庭菜品配方）。
 * <p>
 * 校验规则（名称去空白后非空 / ≤32 字 / 忽略大小写与首尾空白后不重复、用量 ≤32 字）由
 * {@code DishRecipeService.validate} 统一执行，预置与家庭路径共用同一套规则，避免规则漂移。
 * 此处刻意不挂 Bean Validation 注解：DTO 上的注解会在 {@code validate(req)} 阶段抢先失败，
 * 把可定位的中文原因（如「食材名称重复：猪肉」）替换成笼统的字段名列表。
 */
public record DishIngredient(String name, String amount) {
}
