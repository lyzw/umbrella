package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.DishIngredient;
import cn.studykid.growthplanet.entity.DishIngredientRow;
import cn.studykid.growthplanet.mapper.DishIngredientMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 菜品配方（食材子表 + 做法 / 小贴士 / 时长 / 份量 / 难度）共用服务。
 * <p>
 * 预置菜品（CatalogService）与家庭菜品（FamilyDishService）是两条独立写路径，但配方规则必须完全一致，
 * 因此统一收敛到本类：校验、归一化、整体替换、批量装配、级联软删。
 * 六个字段<b>全部可选且不参与「在售」闸门</b> —— R5-c 红线（在售必须先声明过敏原）针对的是儿童安全，
 * 配方缺失不构成安全风险；设成必填会让存量菜品无法上下架。
 * <p>
 * 本类不带独立事务语义，加入调用方事务（REQUIRED）：食材替换必须与菜品主表写入同生共死。
 */
@Service
@Transactional
public class DishRecipeService {
    /** owner_type：预置菜品。 */
    public static final String OWNER_PRESET = "PRESET";
    /** owner_type：家庭菜品。 */
    public static final String OWNER_FAMILY = "FAMILY";

    private static final int MAX_INGREDIENTS = 30;
    private static final int MAX_INGREDIENT_NAME = 32;
    private static final int MAX_INGREDIENT_AMOUNT = 32;
    private static final int MAX_STEPS = 20;
    private static final int MAX_STEP_LENGTH = 300;
    private static final int MAX_TIPS = 500;
    private static final int MIN_COOK_MINUTES = 1;
    private static final int MAX_COOK_MINUTES = 1440;
    private static final int MIN_SERVINGS = 1;
    private static final int MAX_SERVINGS = 20;
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");

    private final DishIngredientMapper ingredients;

    public DishRecipeService(DishIngredientMapper ingredients) {
        this.ingredients = ingredients;
    }

    // ---- 校验 ----

    /** 校验六个配方字段；失败抛 E-400 并带可定位的中文原因（预置 / 家庭共用）。 */
    public void validate(List<DishIngredient> items, List<String> steps, String tips,
            Integer cookMinutes, Integer servings, String difficulty) {
        List<DishIngredient> normalizedItems = normalizeIngredients(items);
        if (normalizedItems.size() > MAX_INGREDIENTS) {
            throw invalid("食材最多 " + MAX_INGREDIENTS + " 条");
        }
        Set<String> seen = new HashSet<>();
        for (DishIngredient item : normalizedItems) {
            if (!seen.add(item.name().toLowerCase(Locale.ROOT))) {
                throw invalid("食材名称重复：" + item.name());
            }
        }
        List<String> normalizedSteps = normalizeSteps(steps);
        if (normalizedSteps.size() > MAX_STEPS) {
            throw invalid("做法最多 " + MAX_STEPS + " 步");
        }
        for (int i = 0; i < normalizedSteps.size(); i++) {
            if (normalizedSteps.get(i).length() > MAX_STEP_LENGTH) {
                throw invalid("第 " + (i + 1) + " 步做法最长 " + MAX_STEP_LENGTH + " 字");
            }
        }
        String normalizedTips = normalizeTips(tips);
        if (normalizedTips != null && normalizedTips.length() > MAX_TIPS) {
            throw invalid("小贴士最长 " + MAX_TIPS + " 字");
        }
        if (cookMinutes != null && (cookMinutes < MIN_COOK_MINUTES || cookMinutes > MAX_COOK_MINUTES)) {
            throw invalid("烹饪时长需在 " + MIN_COOK_MINUTES + " 至 " + MAX_COOK_MINUTES + " 分钟之间");
        }
        if (servings != null && (servings < MIN_SERVINGS || servings > MAX_SERVINGS)) {
            throw invalid("份量需在 " + MIN_SERVINGS + " 至 " + MAX_SERVINGS + " 人份之间");
        }
        if (difficulty != null && !DIFFICULTIES.contains(difficulty)) {
            throw invalid("难度取值不合法");
        }
    }

    // ---- 归一化（校验与写入共用，保证判定与落库一致） ----

    /** 食材归一化：null → 空列表；名称必填（去空白后为空即拒）；用量去空白后为空 → null。 */
    public static List<DishIngredient> normalizeIngredients(List<DishIngredient> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<DishIngredient> normalized = new ArrayList<>(items.size());
        for (DishIngredient item : items) {
            if (item == null) {
                throw invalid("食材名称不能为空");
            }
            String name = item.name() == null ? "" : item.name().trim();
            if (name.isEmpty()) {
                throw invalid("食材名称不能为空");
            }
            if (name.length() > MAX_INGREDIENT_NAME) {
                throw invalid("食材名称最长 " + MAX_INGREDIENT_NAME + " 字");
            }
            String amount = item.amount() == null ? null : item.amount().trim();
            if (amount != null && amount.isEmpty()) {
                amount = null;
            }
            if (amount != null && amount.length() > MAX_INGREDIENT_AMOUNT) {
                throw invalid("食材用量最长 " + MAX_INGREDIENT_AMOUNT + " 字");
            }
            normalized.add(new DishIngredient(name, amount));
        }
        return normalized;
    }

    /** 做法步骤归一化：null → 空列表；每步去首尾空白，空步即拒（不接受空步占位）。 */
    public static List<String> normalizeSteps(List<String> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i) == null ? "" : steps.get(i).trim();
            if (step.isEmpty()) {
                throw invalid("第 " + (i + 1) + " 步做法不能为空");
            }
            normalized.add(step);
        }
        return normalized;
    }

    /** 小贴士归一化：null / 空白 → null（空串不落库，避免「有值但无意义」）。 */
    public static String normalizeTips(String tips) {
        if (tips == null) {
            return null;
        }
        String trimmed = tips.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 难度归一化：null / 空白 → null；取值校验由 validate 负责。 */
    public static String normalizeDifficulty(String difficulty) {
        if (difficulty == null) {
            return null;
        }
        String trimmed = difficulty.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ---- 写入 ----

    /**
     * 整体替换某菜品的食材集合：软删旧行 + 批量插入新行。不做差量比对 ——
     * 配方条目量级为个位数，整体替换的代码与心智成本远低于差量算法。
     *
     * @param ownerType 只允许 {@link #OWNER_PRESET} / {@link #OWNER_FAMILY}，由代码路径固定传入
     */
    public void replace(String ownerType, Long dishId, List<DishIngredient> items) {
        requireOwnerType(ownerType);
        if (dishId == null) {
            throw new IllegalArgumentException("dishId is required");
        }
        softDelete(ownerType, dishId);
        List<DishIngredient> normalized = normalizeIngredients(items);
        if (normalized.isEmpty()) {
            return;
        }
        List<DishIngredientRow> rows = new ArrayList<>(normalized.size());
        for (int i = 0; i < normalized.size(); i++) {
            DishIngredientRow row = new DishIngredientRow();
            row.setOwnerType(ownerType);
            row.setDishId(dishId);
            row.setName(normalized.get(i).name());
            row.setAmount(normalized.get(i).amount());
            row.setSort(i);
            rows.add(row);
        }
        ingredients.insert(rows);
    }

    /**
     * 软删某菜品的全部食材行（编辑时清空、或菜品软删时级联清理，避免「菜没了食材还在」的悬挂数据）。
     */
    public void softDelete(String ownerType, Long dishId) {
        requireOwnerType(ownerType);
        if (dishId == null) {
            return;
        }
        ingredients.update(null, new UpdateWrapper<DishIngredientRow>()
                .eq("owner_type", ownerType).eq("dish_id", dishId).eq("delete_at", 0L)
                .setSql("delete_at = UNIX_TIMESTAMP() * 1000")
                .setSql("update_time = CURRENT_TIMESTAMP"));
    }

    // ---- 读取装配 ----

    /** 单品装配：详情路径单次查询。 */
    public List<DishIngredient> loadByDishId(String ownerType, Long dishId) {
        requireOwnerType(ownerType);
        if (dishId == null) {
            return List.of();
        }
        return toItems(ingredients.selectList(new QueryWrapper<DishIngredientRow>()
                .eq("owner_type", ownerType).eq("dish_id", dishId).eq("delete_at", 0L)
                .orderByAsc("sort", "id")));
    }

    /**
     * 批量装配：列表路径用一条 {@code IN (...)} 查询取回整页菜品的食材后内存分组，避免 N+1。
     * 返回的 Map 只包含「确有食材」的菜品，调用方取值需兜底为空列表。
     */
    public Map<Long, List<DishIngredient>> loadByDishIds(String ownerType, Collection<Long> dishIds) {
        requireOwnerType(ownerType);
        List<Long> ids = dishIds == null ? List.of()
                : dishIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<DishIngredient>> grouped = new LinkedHashMap<>();
        for (DishIngredientRow row : ingredients.selectList(new QueryWrapper<DishIngredientRow>()
                .eq("owner_type", ownerType).in("dish_id", ids).eq("delete_at", 0L)
                .orderByAsc("sort", "id"))) {
            grouped.computeIfAbsent(row.getDishId(), key -> new ArrayList<>())
                    .add(new DishIngredient(row.getName(), row.getAmount()));
        }
        return grouped;
    }

    // ---- 私有辅助 ----

    private List<DishIngredient> toItems(List<DishIngredientRow> rows) {
        return rows.stream().map(row -> new DishIngredient(row.getName(), row.getAmount())).toList();
    }

    private void requireOwnerType(String ownerType) {
        if (!OWNER_PRESET.equals(ownerType) && !OWNER_FAMILY.equals(ownerType)) {
            throw new IllegalArgumentException("Unsupported owner type: " + ownerType);
        }
    }

    private static BizException invalid(String detail) {
        return new BizException(ResultCode.E400_INVALID_ARGUMENT, detail);
    }
}
