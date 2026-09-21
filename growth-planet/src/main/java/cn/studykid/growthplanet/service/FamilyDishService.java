package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.dto.request.FamilyDishReq;
import cn.studykid.growthplanet.dto.response.FamilyDishResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.entity.DishCategory;
import cn.studykid.growthplanet.entity.FamilyDish;
import cn.studykid.growthplanet.entity.MenuDaily;
import cn.studykid.growthplanet.mapper.DishCategoryMapper;
import cn.studykid.growthplanet.mapper.FamilyDishMapper;
import cn.studykid.growthplanet.mapper.MenuDailyMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.*;

import static cn.studykid.growthplanet.util.BusinessRequest.money;

/**
 * 家庭私有菜品服务：家长录入/管理本家庭私有菜品，跨家庭 100% 隔离。
 * familyId/visibility 由后端从家长鉴权派生，客户端禁传。
 * version 为手写乐观锁（沿用家务模块 transition() 约定）。
 */
@Service
@Transactional
public class FamilyDishService {
    private static final Set<String> DISH_STATUSES = Set.of("ON_SALE", "OFF_SALE");

    private final FamilyDishMapper familyDishes;
    private final DishCategoryMapper categories;
    private final MenuDailyMapper menus;
    private final ComplianceProperties policy;
    private final AuditService audit;
    private final Validator validator;
    private final ChildAuthorizationService authorization;

    public FamilyDishService(FamilyDishMapper familyDishes, DishCategoryMapper categories, MenuDailyMapper menus,
            ComplianceProperties policy, AuditService audit, Validator validator,
            ChildAuthorizationService authorization) {
        this.familyDishes = familyDishes;
        this.categories = categories;
        this.menus = menus;
        this.policy = policy;
        this.audit = audit;
        this.validator = validator;
        this.authorization = authorization;
    }

    /** 家长录入一道家庭私有菜品。 */
    public FamilyDishResp create(FamilyDishReq req) {
        Long familyId = requireParentFamily();
        validateFamilyDish(req);
        FamilyDish dish = new FamilyDish();
        applyFamilyDish(dish, req);
        dish.setFamilyId(familyId);
        dish.setVisibility("PRIVATE");
        dish.setStatus("ON_SALE");
        dish.setVersion(0);
        familyDishes.insert(dish);
        audit.record("FAMILY_DISH_CREATE", UserContext.userId(), familyId, "FAMILY_DISH", dish.getId(),
                null, "name=" + req.getName() + ";price=" + money(req.getVirtualPrice()));
        return toResp(dish);
    }

    /** 家长分页查询本家庭私有菜品。 */
    public PageResp<FamilyDishResp> list(int page, int pageSize, Long categoryId, String status, String keyword) {
        Long familyId = requireParentFamily();
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        if (categoryId != null && categoryId <= 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        if (status != null && !DISH_STATUSES.contains(status)
                || keyword != null && (keyword.isBlank() || keyword.length() > 64)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        long offset = ((long) page - 1) * pageSize;
        QueryWrapper<FamilyDish> query = new QueryWrapper<FamilyDish>()
                .eq("family_id", familyId).eq("delete_at", 0L)
                .eq(categoryId != null, "category_id", categoryId)
                .eq(status != null, "status", status)
                .like(keyword != null, "name", keyword);
        long total = familyDishes.selectCount(query);
        List<FamilyDishResp> items = familyDishes.selectList(
                query.orderByDesc("status").orderByDesc("update_time").last("LIMIT " + offset + ", " + pageSize))
                .stream().map(this::toResp).toList();
        return new PageResp<>(items, total, page, pageSize);
    }

    /** 菜品详情（本家庭可见）。 */
    public FamilyDishResp detail(Long id) {
        Long familyId = requireParentFamily();
        return toResp(lockFamilyDish(id, familyId));
    }

    /** 家长编辑菜品（不改 status，上下架走独立接口）。 */
    public FamilyDishResp update(Long id, FamilyDishReq req, Integer expectedVersion) {
        Long familyId = requireParentFamily();
        validateFamilyDish(req);
        FamilyDish dish = lockFamilyDish(id, familyId);
        requireVersion(dish, expectedVersion);
        applyFamilyDish(dish, req);
        transition(dish, expectedVersion);
        audit.record("FAMILY_DISH_UPDATE", UserContext.userId(), familyId, "FAMILY_DISH", id, null,
                "name=" + req.getName());
        return toResp(dish);
    }

    /** 家长上下架菜品。 */
    public FamilyDishResp changeStatus(Long id, String targetStatus, Integer expectedVersion) {
        Long familyId = requireParentFamily();
        if (!DISH_STATUSES.contains(targetStatus)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        FamilyDish dish = lockFamilyDish(id, familyId);
        requireVersion(dish, expectedVersion);
        dish.setStatus(targetStatus);
        transition(dish, expectedVersion);
        audit.record("FAMILY_DISH_STATUS", UserContext.userId(), familyId, "FAMILY_DISH", id, null,
                "status=" + targetStatus);
        return toResp(dish);
    }

    /** 删除前查询引用该菜品的菜单数（供前端二次确认）。 */
    public int countMenuReferences(Long id) {
        Long familyId = requireParentFamily();
        lockFamilyDish(id, familyId);
        return Math.toIntExact(menus.selectCount(new QueryWrapper<MenuDaily>()
                .eq("source_type", "FAMILY").eq("family_id", familyId).eq("delete_at", 0L)
                .apply("JSON_CONTAINS(dish_ids, JSON_OBJECT('type', 'FAMILY', 'id', {0}))", id)));
    }

    /** 家长软删除菜品（前端应先调 countMenuReferences 二次确认）。 */
    public void delete(Long id, Integer expectedVersion) {
        Long familyId = requireParentFamily();
        FamilyDish dish = lockFamilyDish(id, familyId);
        requireVersion(dish, expectedVersion);
        int rows = familyDishes.update(null, new UpdateWrapper<FamilyDish>()
                .eq("id", id).eq("version", expectedVersion)
                .setSql("delete_at = UNIX_TIMESTAMP() * 1000")
                .setSql("update_time = CURRENT_TIMESTAMP")
                .set("version", expectedVersion + 1));
        if (rows != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        audit.record("FAMILY_DISH_DELETE", UserContext.userId(), familyId, "FAMILY_DISH", id, null, null);
    }

    // ---- 私有辅助 ----

    private FamilyDish lockFamilyDish(Long id, Long familyId) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        FamilyDish dish = familyDishes.selectOne(new QueryWrapper<FamilyDish>()
                .eq("id", id).eq("family_id", familyId).eq("delete_at", 0L).last("FOR UPDATE"));
        if (dish == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        return dish;
    }

    private void requireVersion(FamilyDish dish, Integer expected) {
        if (expected == null || !expected.equals(dish.getVersion())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
    }

    private void transition(FamilyDish dish, Integer expectedVersion) {
        int next = Math.incrementExact(expectedVersion);
        dish.setVersion(next);
        int rows = familyDishes.update(dish, new UpdateWrapper<FamilyDish>()
                .eq("id", dish.getId()).eq("version", expectedVersion));
        if (rows != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
    }

    private void validateFamilyDish(FamilyDishReq req) {
        if (req == null || !validator.validate(req).isEmpty()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        DishCategory category = categories.selectById(req.getCategoryId());
        if (category == null || !"ENABLED".equals(category.getStatus()) || !validAllergens(req.getAllergens())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        if (req.getImageUrl() != null) {
            try {
                URI uri = URI.create(req.getImageUrl());
                if (!Set.of("http", "https").contains(Objects.toString(uri.getScheme(), ""))
                        || uri.getHost() == null || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException("Invalid image URL");
                }
            } catch (IllegalArgumentException ex) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
            }
        }
    }

    private boolean validAllergens(List<String> allergens) {
        return policy.getCatalogReference() != null && !policy.getCatalogReference().isBlank()
                && allergens != null && allergens.size() <= 20
                && allergens.stream().allMatch(value -> value != null && !value.isBlank()
                && value.length() <= 64 && policy.getAllergens().contains(value))
                && new HashSet<>(allergens).size() == allergens.size();
    }

    private void applyFamilyDish(FamilyDish dish, FamilyDishReq req) {
        dish.setCategoryId(req.getCategoryId());
        dish.setName(req.getName().trim());
        dish.setImageUrl(req.getImageUrl());
        dish.setVirtualPrice(req.getVirtualPrice().setScale(2));
        dish.setCalories(req.getCalories());
        dish.setTags(req.getTags());
        dish.setAllergens(List.copyOf(req.getAllergens()));
        dish.setAllergenStatus(req.getAllergenStatus());
        dish.setSpiceLevel(req.getSpiceLevel());
    }

    private FamilyDishResp toResp(FamilyDish d) {
        return FamilyDishResp.builder().dishId(d.getId()).familyId(d.getFamilyId()).categoryId(d.getCategoryId())
                .name(d.getName()).imageUrl(d.getImageUrl())
                .virtualPrice(d.getVirtualPrice().setScale(2).toPlainString())
                .calories(d.getCalories()).tags(d.getTags()).allergens(d.getAllergens())
                .allergenStatus(d.getAllergenStatus()).spiceLevel(d.getSpiceLevel())
                .status(d.getStatus()).version(d.getVersion()).build();
    }

    private Long requireParentFamily() {
        if (!"PARENT".equals(UserContext.role())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        Long familyId = UserContext.familyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        authorization.requireParent(familyId);
        return familyId;
    }
}
