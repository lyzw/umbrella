package cn.studykid.growthplanet.dto.response;

import java.util.List;

/**
 * 菜品表单参考字典（M3 内容管理）。
 *
 * <p>过敏原取值受发布目录约束（{@code compliance.allergens}），且 {@code compliance.catalog-reference}
 * 为空时后端一律拒绝（{@code CatalogService#validAllergens}）。前端若自行硬编码候选值，
 * 会因与发布目录不一致而拿到笼统的 E-400，故由本接口下发真实目录。</p>
 *
 * @param allergens    过敏原发布目录（英文标识，如 PEANUT），按配置顺序
 * @param catalogReady 目录是否可用于落库：{@code catalog-reference} 非空且 {@code allergens} 非空
 */
public record DishReferenceResp(
        List<String> allergens,
        boolean catalogReady) {
}
