package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.LoginUser;
import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.dto.DishIngredient;
import cn.studykid.growthplanet.dto.request.*;
import cn.studykid.growthplanet.dto.response.*;
import cn.studykid.growthplanet.entity.*;
import cn.studykid.growthplanet.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional
public class CatalogService {
    private static final Set<String> MEALS = Set.of("BREAKFAST", "LUNCH", "DINNER");
    /** 家长按日清单最大跨度（含首尾），防大区间扫描。 */
    private static final int BOARD_MAX_DAYS = 31;
    /** 派生「爱吃/常点」统计窗口（天）。 */
    private static final int FREQUENT_WINDOW_DAYS = 30;
    /** 派生「爱吃/常点」默认返回条数。 */
    private static final int FREQUENT_DEFAULT_LIMIT = 6;
    /** 周视图最大跨度（含首尾）。 */
    private static final int WEEK_MAX_DAYS = 14;
    /** 批量发布单批上限（7 天 × 3 餐）。 */
    private static final int BATCH_MAX_ITEMS = 21;
    /** 推荐默认/最大返回条数。 */
    private static final int RECOMMEND_DEFAULT_LIMIT = 3;
    private static final int RECOMMEND_MAX_LIMIT = 5;
    /** 画像里出现这些词即视为"偏好清淡"，用于给低辣度菜品加分。 */
    private static final List<String> MILD_TASTE_WORDS = List.of("清淡", "少油", "少盐", "不辣");
    private final DishCategoryMapper categories;
    private final DishMapper dishes;
    private final FamilyDishMapper familyDishes;
    private final MenuDailyMapper menus;
    private final FamilyMapper families;
    private final ChildProfileMapper profiles;
    private final ChildWantEatMapper wantEats;
    private final WishMenuMapper wishMenus;
    private final ChildAuthorizationService authorization;
    private final ComplianceProperties policy;
    private final AuditService audit;
    private final Validator validator;
    private final BusinessTime time;
    private final MedalDefinitionMapper medalDefs;
    private final MedalService medalService;
    /** 配方（食材子表 + 做法 / 小贴士 / 时长 / 份量 / 难度）读写；与家庭菜品路径共用同一套规则。 */
    private final DishRecipeService recipes;
    /** 批量发布用：每条 item 走独立事务（REQUIRES_NEW），单条失败不回滚整批。 */
    private final TransactionTemplate perItemTransactions;

    public CatalogService(DishCategoryMapper categories, DishMapper dishes, FamilyDishMapper familyDishes,
            MenuDailyMapper menus, FamilyMapper families, ChildProfileMapper profiles,
            ChildWantEatMapper wantEats, WishMenuMapper wishMenus, ChildAuthorizationService authorization,
            ComplianceProperties policy, AuditService audit, Validator validator, BusinessTime time,
            MedalDefinitionMapper medalDefs, MedalService medalService, DishRecipeService recipes,
            PlatformTransactionManager transactionManager) {
        this.categories = categories;
        this.dishes = dishes;
        this.familyDishes = familyDishes;
        this.menus = menus;
        this.families = families;
        this.profiles = profiles;
        this.wantEats = wantEats;
        this.wishMenus = wishMenus;
        this.authorization = authorization;
        this.policy = policy;
        this.audit = audit;
        this.validator = validator;
        this.time = time;
        this.medalDefs = medalDefs;
        this.medalService = medalService;
        this.recipes = recipes;
        this.perItemTransactions = new TransactionTemplate(transactionManager);
        this.perItemTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public DishCategoryResp createCategory(DishCategoryReq req) {
        requireRole("ADMIN");
        return createCategoryAs(UserContext.userId(), req);
    }

    /** 运营端变体：鉴权由调用方（AdminUserContext.requirePerm）完成，actorId 由运营会话提供。 */
    public DishCategoryResp createCategoryAs(Long actorId, DishCategoryReq req) {
        validate(req);
        DishCategory category = new DishCategory();
        category.setName(req.getName().trim());
        category.setSort(req.getSort());
        category.setStatus(req.getStatus());
        categories.insert(category);
        audit.record("CATEGORY_CREATE", actorId, null, "CATEGORY", category.getId(), null, null);
        return categoryResponse(category);
    }

    public PageResp<DishCategoryResp> listCategories(int page, int pageSize) {
        requireRole("ADMIN");
        return listCategoriesAs(page, pageSize);
    }

    /** 运营端变体：纯读，无审计。 */
    public PageResp<DishCategoryResp> listCategoriesAs(int page, int pageSize) {
        long offset = offset(page, pageSize);
        long total = categories.selectCount(new QueryWrapper<>());
        List<DishCategoryResp> items = categories.selectList(new QueryWrapper<DishCategory>()
                .orderByAsc("sort", "id").last("LIMIT " + offset + ", " + pageSize))
                .stream().map(this::categoryResponse).toList();
        return new PageResp<>(items, total, page, pageSize);
    }

    public DishResp createDish(DishReq req) {
        requireRole("ADMIN");
        return createDishAs(UserContext.userId(), req);
    }

    /** 运营端变体：鉴权由调用方完成。 */
    public DishResp createDishAs(Long actorId, DishReq req) {
        validateDish(req);
        Dish dish = new Dish();
        applyDish(dish, req);
        dishes.insert(dish);
        // 食材整体替换与菜品主表同事务；applyDish 已把归一化结果放进瞬时字段，响应无需回查
        recipes.replace(DishRecipeService.OWNER_PRESET, dish.getId(), req.getIngredients());
        audit.record("DISH_CREATE", actorId, null, "DISH", dish.getId(), null, null);
        return dishResponse(dish);
    }

    public DishResp updateDish(Long dishId, DishReq req) {
        requireRole("ADMIN");
        return updateDishAs(UserContext.userId(), dishId, req);
    }

    /** 运营端变体：鉴权由调用方完成。 */
    public DishResp updateDishAs(Long actorId, Long dishId, DishReq req) {
        positive(dishId);
        validateDish(req);
        Dish dish = lockDish(dishId);
        applyDish(dish, req);
        requireUpdated(dishes.updateById(dish));
        // 传空数组 / null 即清空：旧食材行整体软删；标量靠 updateStrategy=ALWAYS 写回 null
        recipes.replace(DishRecipeService.OWNER_PRESET, dishId, req.getIngredients());
        audit.record("DISH_UPDATE", actorId, null, "DISH", dishId, null, null);
        return dishResponse(dish);
    }

    public void deleteDish(Long dishId) {
        requireRole("ADMIN");
        deleteDishAs(UserContext.userId(), dishId);
    }

    /** 运营端变体：鉴权由调用方完成（详设 §3.4：预置菜品删除仅 SA）。 */
    public void deleteDishAs(Long actorId, Long dishId) {
        positive(dishId);
        lockDish(dishId);
        // Keep menu references and historical snapshots; subsequent validation observes the missing dish.
        requireUpdated(dishes.update(null, new UpdateWrapper<Dish>().eq("id", dishId)
                .set("status", "OFF_SALE").setSql("delete_at = UNIX_TIMESTAMP() * 1000")
                .setSql("update_time = CURRENT_TIMESTAMP")));
        // 级联软删食材行，避免「菜没了食材还在」的悬挂数据
        recipes.softDelete(DishRecipeService.OWNER_PRESET, dishId);
        audit.record("DISH_DELETE", actorId, null, "DISH", dishId, null, null);
    }

    public DishResp getDish(Long dishId) {
        requireRole("ADMIN");
        return getDishAs(dishId);
    }

    /** 运营端变体：纯读，无审计。 */
    public DishResp getDishAs(Long dishId) {
        positive(dishId);
        Dish dish = dishes.selectById(dishId);
        if (dish == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        return dishResponse(dish);
    }

    /** 管理端菜品列表；allergenStatus 用于筛出「未声明」待补全清单（R5-a）。 */
    public PageResp<DishResp> listDishes(int page, int pageSize, Long categoryId, String status, String keyword,
            String allergenStatus) {
        requireRole("ADMIN");
        return listDishesAs(page, pageSize, categoryId, status, keyword, allergenStatus);
    }

    /** 运营端变体：纯读，无审计。 */
    public PageResp<DishResp> listDishesAs(int page, int pageSize, Long categoryId, String status, String keyword,
            String allergenStatus) {
        return queryDishes(page, pageSize, categoryId, status, keyword, allergenStatus);
    }

    public PageResp<DishResp> listParentDishes(int page, int pageSize, Long categoryId, String keyword) {
        requireCurrentFamilyParent();
        return queryDishes(page, pageSize, categoryId, "ON_SALE", keyword, null);
    }

    /** 家长可见的预置分类（ENABLED），供家庭私有菜品类目选择（F-01，不可自建分类）。 */
    public List<DishCategoryResp> listParentCategories() {
        requireCurrentFamilyParent();
        return categories.selectList(new QueryWrapper<DishCategory>()
                .eq("status", "ENABLED").orderByAsc("sort", "id"))
                .stream().map(this::categoryResponse).toList();
    }

    /**
     * C 端配方（只读、懒加载）：清单页一次列十余道菜，把做法长文本塞进清单响应是纯浪费，
     * 因此单独开端点，用户点开某道菜才请求。家长与孩子均可读（只读、非敏感）。
     * <p>
     * 隔离红线：type=FAMILY 时 family_id 必须等于当前登录用户所属家庭，归属一律从鉴权派生、
     * 绝不接受客户端传入 familyId；不等时按「不存在」处理，不泄露「这盘菜存在但不属于你」。
     * 可见性沿用既有规则：预置菜品下架即 E-404，家庭菜品不额外放宽。
     */
    public DishRecipeResp dishRecipe(String type, Long dishId) {
        LoginUser ctx = requireRole("CHILD", "PARENT");
        if (!Set.of("PRESET", "FAMILY").contains(type)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        positive(dishId);
        if ("PRESET".equals(type)) {
            Dish dish = dishes.selectById(dishId);
            if (dish == null || !"ON_SALE".equals(dish.getStatus())) {
                throw new BizException(ResultCode.E404_NOT_FOUND);
            }
            return recipeOf(DishRecipeService.OWNER_PRESET, dishId, dish.getCookSteps(), dish.getCookTips(),
                    dish.getCookMinutes(), dish.getServings(), dish.getDifficulty());
        }
        Long familyId = currentFamilyId(ctx);
        FamilyDish fd = familyDishes.selectOne(new QueryWrapper<FamilyDish>()
                .eq("id", dishId).eq("family_id", familyId).eq("delete_at", 0L));
        if (fd == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        return recipeOf(DishRecipeService.OWNER_FAMILY, dishId, fd.getCookSteps(), fd.getCookTips(),
                fd.getCookMinutes(), fd.getServings(), fd.getDifficulty());
    }

    private DishRecipeResp recipeOf(String ownerType, Long dishId, List<String> cookSteps, String cookTips,
            Integer cookMinutes, Integer servings, String difficulty) {
        return new DishRecipeResp(recipes.loadByDishId(ownerType, dishId),
                cookSteps == null ? List.of() : cookSteps, cookTips, cookMinutes, servings, difficulty);
    }

    /**
     * 取当前登录用户的家庭 id：孩子走绑定关系（boundChild），家长走成员身份校验。
     * 只读路径，故不加行锁 —— 与 requireCurrentFamilyParent 的写路径锁策略区分开。
     */
    private Long currentFamilyId(LoginUser ctx) {
        if ("CHILD".equals(ctx.getRole())) {
            return authorization.boundChild(ctx.getUserId()).getFamilyId();
        }
        Long familyId = ctx.firstFamilyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        authorization.requireParent(familyId);
        return familyId;
    }

    private PageResp<DishResp> queryDishes(int page, int pageSize, Long categoryId, String status, String keyword,
            String allergenStatus) {
        long offset = offset(page, pageSize);
        if (categoryId != null) {
            positive(categoryId);
        }
        if (status != null && !Set.of("ON_SALE", "OFF_SALE").contains(status)
                || allergenStatus != null && !Set.of("UNKNOWN", "DECLARED").contains(allergenStatus)
                || keyword != null && (keyword.isBlank() || keyword.length() > 64)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        QueryWrapper<Dish> query = new QueryWrapper<Dish>()
                .eq(categoryId != null, "category_id", categoryId)
                .eq(status != null, "status", status)
                .eq(allergenStatus != null, "allergen_status", allergenStatus)
                .like(keyword != null, "name", keyword);
        long total = dishes.selectCount(query);
        List<Dish> pageItems = dishes.selectList(query.orderByAsc("id")
                .last("LIMIT " + offset + ", " + pageSize));
        // 列表路径：一次 IN 查询取回整页食材做计数摘要，不逐条查子表（防 N+1）
        attachPresetIngredients(pageItems);
        Map<Long, String> categoryNames = categoryNamesOf(pageItems);
        List<DishResp> items = pageItems.stream().map(dish -> dishResponse(dish, categoryNames)).toList();
        return new PageResp<>(items, total, page, pageSize);
    }

    public MenuUpsertResp upsertSchoolMenu(MenuDailyReq req) {
        requireRole("ADMIN");
        return upsertSchoolMenuAs(UserContext.userId(), req);
    }

    /** 运营端变体：鉴权由调用方完成；校名发布目录校验保留（R4-lite）。 */
    public MenuUpsertResp upsertSchoolMenuAs(Long actorId, MenuDailyReq req) {
        validateMenu(req);
        if (req.getSchool() == null || req.getSchool().isBlank()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        String school = req.getSchool().trim();
        // R4-lite：校名必须来自发布目录。校名仍是 SCHOOL 菜单的归属键文本，若管理员手输错字，
        // 与家长档案里的校名逐字不一致 ⇒ 该校孩子看不到菜单（daily/recommend/menuIndex 均按文本比对）。
        if (!policy.getSchools().contains(school)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "学校不在发布目录");
        }
        return upsertMenu(req, "SCHOOL", null, school, actorId);
    }

    public MenuUpsertResp upsertFamilyMenu(MenuDailyReq req) {
        LoginUser ctx = requireCurrentFamilyParent();
        validateMenu(req);
        if (req.getSchool() != null) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        Long familyId = ctx.firstFamilyId();
        return upsertMenu(req, "FAMILY", familyId, null);
    }

    private MenuUpsertResp upsertMenu(MenuDailyReq req, String sourceType, Long familyId, String school) {
        return upsertMenu(req, sourceType, familyId, school, UserContext.userId());
    }

    private MenuUpsertResp upsertMenu(MenuDailyReq req, String sourceType, Long familyId, String school,
            Long actorId) {
        MenuDaily proposed = new MenuDaily();
        proposed.setSourceType(sourceType);
        proposed.setFamilyId(familyId);
        proposed.setSchool(school);
        proposed.setOwnerKey(familyId == null ? school : familyId.toString());
        proposed.setMenuDate(req.getMenuDate());
        proposed.setMealType(req.getMealType());
        proposed.setDishIds(new ArrayList<>(new LinkedHashSet<>(req.getDishIds())));
        proposed.setStatus(req.getStatus());
        menus.insertOrKeep(proposed);
        MenuDaily menu = menus.selectOne(menuKey(sourceType, proposed.getOwnerKey(),
                req.getMenuDate(), req.getMealType()).last("FOR UPDATE"));
        if (menu == null) {
            // A logically deleted unique key must not silently create a replacement menu identity.
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        // 混合菜品校验：按 DishRef.type 分流，全部存在且 ON_SALE
        Map<String, Dish> locked = lockVisibleDishesByRef(proposed.getDishIds(), familyId);
        if (locked.size() != new HashSet<>(proposed.getDishIds()).size()) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        for (Dish dish : locked.values()) {
            if (!"ON_SALE".equals(dish.getStatus())) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
        }
        Integer oldVersion = menu.getVersion();
        int expected = oldVersion == null ? 0 : oldVersion;
        menu.setDishIds(proposed.getDishIds());
        menu.setStatus(proposed.getStatus());
        menu.setVersion(expected + 1);
        if (menus.update(menu, new UpdateWrapper<MenuDaily>()
                .eq("id", menu.getId()).eq("version", expected)) != 1) {
            // 菜单已被他人更新，提示刷新后重试（乐观锁）
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        audit.record("MENU_UPSERT", actorId, familyId, "MENU", menu.getId(), null,
                "sourceType=" + sourceType);
        return new MenuUpsertResp(menu.getId());
    }

    public MenuMaintenanceResp getFamilyMenu(LocalDate menuDate, String mealType) {
        LoginUser ctx = requireCurrentFamilyParent();
        validateDateAndMeal(menuDate, mealType);
        Long familyId = ctx.firstFamilyId();
        // 展示路径：仅读，不加行锁（避免家长查看菜单时阻塞发布/点单等写事务）。
        MenuDaily menu = menus.selectOne(menuKey("FAMILY", familyId.toString(), menuDate, mealType));
        if (menu == null) {
            // The maintenance page treats an unpublished date/meal as a normal empty state.
            audit.record("MENU_MAINTENANCE_QUERY", ctx.getUserId(), familyId, "MENU", null, null, "empty");
            return null;
        }
        Map<String, Dish> current = visibleDishesByRef(menu.getDishIds(), familyId);
        // 菜单路径仅带计数字段（时长/份量/难度/食材数/步骤数），做法长文本不进清单响应
        attachIngredientsByRef(current);
        Map<Long, String> categoryNames = categoryNamesOf(current.values());
        List<DishResp> visible = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (DishRef ref : menu.getDishIds()) {
            Dish dish = current.get(ref.getType() + ":" + ref.getId());
            if (dish == null) {
                missing.add(ref.getId().toString());
            } else {
                DishResp resp = dishResponse(dish, categoryNames);
                resp.setSourceType(ref.getType());
                visible.add(resp);
            }
        }
        audit.record("MENU_MAINTENANCE_QUERY", ctx.getUserId(), familyId, "MENU", menu.getId(), null, null);
        return MenuMaintenanceResp.builder().menuId(menu.getId()).menuDate(menuDate).mealType(mealType)
                .status(menu.getStatus()).dishes(visible).missingDishIds(missing).build();
    }

    public MenuDailyResp daily(String sourceType, LocalDate menuDate, String mealType, Long childId) {
        LoginUser ctx = requireRole("CHILD", "PARENT");
        if (sourceType == null || !Set.of("SCHOOL", "FAMILY").contains(sourceType)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        validateDateAndMeal(menuDate, mealType);
        if ("CHILD".equals(ctx.getRole())) {
            if (childId != null && !childId.equals(ctx.getUserId())) {
                throw new BizException(ResultCode.E009_FORBIDDEN);
            }
            childId = ctx.getUserId();
        } else {
            positive(childId);
        }
        // 展示路径：仅读，不加行锁（避免孩子看菜单时阻塞发布/点单等写事务）。
        FamilyMember member = authorization.boundChild(childId);
        var consent = authorization.requireConsentReadOnly(member);
        ChildProfile profile = requireCompleteProfile(member);
        String ownerKey = "FAMILY".equals(sourceType) ? member.getFamilyId().toString()
                : profile.getSchool() == null ? null : profile.getSchool().trim();
        MenuDaily menu = menus.selectOne(menuKey(sourceType, ownerKey, menuDate, mealType)
                .eq("status", "PUBLISHED"));
        if (menu == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        Map<String, Dish> current = visibleDishesByRef(menu.getDishIds(), menu.getFamilyId());
        attachIngredientsByRef(current);
        Map<Long, String> categoryNames = categoryNamesOf(current.values());
        boolean submittable = "CHILD".equals(ctx.getRole()) && "FAMILY".equals(sourceType)
                && time.today().equals(menuDate);
        List<MenuDailyResp.MenuDishResp> visible = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (DishRef ref : menu.getDishIds()) {
            Dish dish = current.get(ref.getType() + ":" + ref.getId());
            if (dish == null) {
                missing.add(ref.getId().toString());
                continue;
            }
            String safety = safetyStatus(dish, profile);
            DishResp resp = dishResponse(dish, categoryNames);
            resp.setSourceType(ref.getType());
            visible.add(MenuDailyResp.MenuDishResp.builder().dish(resp)
                    .disliked(isDisliked(dish, profile))
                    .favorite(wantEatRefs(childId, menuDate, mealType).contains(ref.getType() + ":" + ref.getId()))
                    .allergyConflict(allergyConflict(dish, profile))
                    .canSelect(submittable && "DECLARED".equals(safety)).safetyStatus(safety).build());
        }
        audit.record("MENU_QUERY", ctx.getUserId(), member.getFamilyId(), "CHILD", childId, null,
                "consentId=" + consent.getId());
        return MenuDailyResp.builder().menuId(menu.getId()).sourceType(sourceType).menuDate(menuDate)
                .mealType(mealType).status(menu.getStatus())
                .canSubmit(submittable && visible.stream().anyMatch(MenuDailyResp.MenuDishResp::isCanSelect))
                .dishes(visible).missingDishIds(missing).build();
    }

    /**
     * 标记/取消「每日想吃」。想吃按 (childId, menuDate, mealType) 落库，与终身收藏解耦。
     * source_type 由 menuId 反查菜单权威派生（不可客户端伪造）。
     */
    public WantEatResp markFavorite(MarkFavoriteReq req) {
        LoginUser ctx = requireRole("CHILD");
        validate(req);
        validateDateAndMeal(req.getMenuDate(), req.getMealType());
        FamilyMember member = authorization.lockBoundChild(ctx.getUserId());
        var consent = authorization.requireConsent(member);
        ChildProfile profile = completeProfile(member);
        MenuDaily menu = menus.selectOne(new QueryWrapper<MenuDaily>().eq("id", req.getMenuId()));
        if (menu == null || !"PUBLISHED".equals(menu.getStatus())
                || !menu.getMenuDate().equals(req.getMenuDate()) || !menu.getMealType().equals(req.getMealType())) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        String sourceType = menu.getSourceType();
        if ("FAMILY".equals(sourceType)) {
            if (!member.getFamilyId().equals(menu.getFamilyId())) {
                throw new BizException(ResultCode.E009_FORBIDDEN);
            }
        } else if ("SCHOOL".equals(sourceType)) {
            if (profile.getSchool() == null || !profile.getSchool().trim().equals(menu.getOwnerKey())) {
                throw new BizException(ResultCode.E009_FORBIDDEN);
            }
        } else {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        // P3 心愿菜单：当日心愿菜单已提交（SUBMITTED）时，当日想吃标记整体锁定，撤回后恢复。
        // 锁定的唯一依据是头表状态，不需要给明细行打锁标记。
        if (wishMenus.selectCount(new QueryWrapper<WishMenu>().eq("child_id", ctx.getUserId())
                .eq("menu_date", req.getMenuDate()).eq("status", "SUBMITTED")) > 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "心愿菜单已提交，请先撤回再调整想吃");
        }
        String type = req.getFavorite() ? resolveDishType(req) : null;
        if (req.getFavorite()) {
            // 菜品必须在菜单的菜品引用中（type+id 双键校验），既防越权又消 PRESET/FAMILY 同号歧义。
            boolean inMenu = menu.getDishIds().stream()
                    .anyMatch(r -> req.getDishId().equals(r.getId()) && type.equals(r.getType()));
            if (!inMenu) {
                throw new BizException(ResultCode.E404_NOT_FOUND);
            }
            lockDishByType(type, req.getDishId(), member.getFamilyId());
            ChildWantEat existing = wantEats.selectOne(wantEatKey(ctx.getUserId(), req.getMenuDate(),
                    req.getMealType(), type, req.getDishId()));
            if (existing == null) {
                ChildWantEat row = new ChildWantEat();
                row.setChildId(ctx.getUserId());
                row.setFamilyId(member.getFamilyId());
                row.setMenuDate(req.getMenuDate());
                row.setMealType(req.getMealType());
                row.setSourceType(sourceType);
                row.setDishType(type);
                row.setDishId(req.getDishId());
                row.setStatus("MARKED");
                requireInserted(wantEats.insert(row));
            }
        } else {
            // 取消想吃不要求菜品当前存在（下架/删除后仍可取消）。
            // 不按 dish_type 过滤：取消时无需（也无法）重推导类型，且可覆盖 PRESET/FAMILY 撞号的两行。
            List<ChildWantEat> existing = wantEats.selectList(new QueryWrapper<ChildWantEat>()
                    .eq("child_id", ctx.getUserId()).eq("menu_date", req.getMenuDate())
                    .eq("meal_type", req.getMealType()).eq("dish_id", req.getDishId()));
            for (ChildWantEat row : existing) {
                requireUpdated(wantEats.deleteById(row.getId()));
            }
        }
        audit.record("WANT_EAT", ctx.getUserId(), member.getFamilyId(), "CHILD", ctx.getUserId(), null,
                "consentId=" + consent.getId() + ";menuId=" + req.getMenuId());
        return WantEatResp.builder().childId(ctx.getUserId()).menuDate(req.getMenuDate())
                .mealType(req.getMealType()).wantEat(currentWantEat(ctx.getUserId(), req.getMenuDate(), req.getMealType()))
                .build();
    }

    /** 查询某 (childId, menuDate, mealType) 下的想吃菜品引用集合（"TYPE:ID"），读操作不加锁。 */
    public Set<String> wantEatRefs(Long childId, LocalDate menuDate, String mealType) {
        List<ChildWantEat> rows = wantEats.selectList(new QueryWrapper<ChildWantEat>()
                .eq("child_id", childId).eq("menu_date", menuDate).eq("meal_type", mealType));
        Set<String> refs = new LinkedHashSet<>();
        for (ChildWantEat row : rows) {
            refs.add(row.getDishType() + ":" + row.getDishId());
        }
        return refs;
    }

    /** 取某 (childId, menuDate, mealType) 下想吃的 DishRef 列表，供响应/家长查询使用。 */
    private List<DishRef> currentWantEat(Long childId, LocalDate menuDate, String mealType) {
        List<DishRef> refs = new ArrayList<>();
        for (String key : wantEatRefs(childId, menuDate, mealType)) {
            int sep = key.indexOf(':');
            DishRef ref = new DishRef();
            ref.setType(key.substring(0, sep));
            ref.setId(Long.parseLong(key.substring(sep + 1)));
            refs.add(ref);
        }
        return refs;
    }

    /** 家长/本人查询某 (childId, menuDate[, mealType]) 的每日想吃清单。 */
    public WantEatResp childWantEat(Long childId, LocalDate menuDate, String mealType) {
        LoginUser ctx = requireRole("CHILD", "PARENT");
        if (ctx.getRole().equals("PARENT")) {
            positive(childId);
            // 纯读接口：只校验授权，不加行锁。
            FamilyMember member = authorization.boundChild(childId);
            authorization.requireParent(member.getFamilyId());
        } else {
            if (childId != null && !childId.equals(ctx.getUserId())) {
                throw new BizException(ResultCode.E009_FORBIDDEN);
            }
            childId = ctx.getUserId();
        }
        validateDateAndMeal(menuDate, mealType);
        return WantEatResp.builder().childId(childId).menuDate(menuDate).mealType(mealType)
                .wantEat(currentWantEat(childId, menuDate, mealType)).build();
    }

    /**
     * 家长「按日想吃清单」（R3）：以「日期 × 餐次」聚合孩子的想吃标记，附采纳状态、过期标记与区间采购汇总。
     * 纯读路径，不加行锁。过期（menuDate &lt; today）的行照常返回并置 expired，不丢数据。
     */
    public WantEatBoardResp parentWantEatBoard(Long childId, LocalDate from, LocalDate to) {
        requireRole("PARENT");
        positive(childId);
        validateBoardRange(from, to);
        FamilyMember member = authorization.boundChild(childId);
        authorization.requireParent(member.getFamilyId());
        var consent = authorization.requireConsentReadOnly(member);
        ChildProfile profile = requireCompleteProfile(member);
        LocalDate today = time.today();
        List<ChildWantEat> rows = wantEatRows(childId, from, to);
        Map<String, Dish> dishByRef = visibleDishesByRef(rows.stream().map(this::refOf).toList(),
                member.getFamilyId());
        Map<Long, String> categoryNames = categoryNamesOf(dishByRef.values());
        Map<String, Long> menuIndex = menuIndex(from, to, member, profile);

        Map<LocalDate, Map<String, List<ChildWantEat>>> grouped = new TreeMap<>();
        for (ChildWantEat row : rows) {
            grouped.computeIfAbsent(row.getMenuDate(), date -> new TreeMap<>(mealOrder()))
                    .computeIfAbsent(row.getMealType(), meal -> new ArrayList<>()).add(row);
        }
        List<WantEatBoardResp.Day> days = new ArrayList<>();
        Map<String, SummaryAccumulator> accumulatorByRef = new LinkedHashMap<>();
        int expiredCount = 0;
        for (var dateEntry : grouped.entrySet()) {
            LocalDate date = dateEntry.getKey();
            boolean expired = date.isBefore(today);
            List<WantEatBoardResp.Meal> meals = new ArrayList<>();
            for (var mealEntry : dateEntry.getValue().entrySet()) {
                List<WantEatBoardResp.Item> items = new ArrayList<>();
                for (ChildWantEat row : mealEntry.getValue()) {
                    Dish dish = dishByRef.get(row.getDishType() + ":" + row.getDishId());
                    items.add(boardItem(row, dish, categoryNames, profile, expired));
                    if (expired && "MARKED".equals(row.getStatus())) {
                        expiredCount++;
                    }
                    accumulatorByRef.computeIfAbsent(row.getDishType() + ":" + row.getDishId(),
                            key -> new SummaryAccumulator(row.getDishType(), row.getDishId())).add(row, dish);
                }
                ChildWantEat first = mealEntry.getValue().get(0);
                meals.add(WantEatBoardResp.Meal.builder().mealType(mealEntry.getKey())
                        .sourceType(first.getSourceType())
                        .menuId(menuIndex.get(first.getSourceType() + "|" + date + "|" + mealEntry.getKey()))
                        .items(items).build());
            }
            days.add(WantEatBoardResp.Day.builder().menuDate(date).meals(meals).build());
        }
        List<WantEatBoardResp.SummaryDish> summaryDishes = new ArrayList<>();
        for (SummaryAccumulator accumulator : accumulatorByRef.values()) {
            summaryDishes.add(accumulator.toSummaryDish());
        }
        audit.record("WANT_EAT_BOARD_QUERY", UserContext.userId(), member.getFamilyId(), "CHILD", childId, null,
                "consentId=" + consent.getId() + ";from=" + from + ";to=" + to);
        return WantEatBoardResp.builder().childId(childId).from(from).to(to).today(today).days(days)
                .expiredCount(expiredCount).summary(WantEatBoardResp.Summary.builder()
                        .totalItems(summaryDishes.size()).dishes(summaryDishes).build())
                .build();
    }

    /**
     * 家长流转想吃状态：ADOPTED（已采购/已安排）/ COOKED（已做）/ MARKED（回退）。
     * 沿用项目手写乐观锁范式（UpdateWrapper 带 version 条件），版本不符 → E007。
     */
    public WantEatResp transitionWantEat(Long wantEatId, String status, Integer expectedVersion) {
        requireRole("PARENT");
        positive(wantEatId);
        if (status == null || !Set.of("MARKED", "ADOPTED", "COOKED").contains(status)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        ChildWantEat row = wantEats.selectOne(new QueryWrapper<ChildWantEat>().eq("id", wantEatId));
        if (row == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        // 归属校验必须先于写入；越权统一返回越权码，不暴露该行是否存在。
        FamilyMember member = authorization.boundChild(row.getChildId());
        authorization.requireParent(member.getFamilyId());
        if (expectedVersion == null) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        if (row.getVersion() == null || !row.getVersion().equals(expectedVersion)) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        int beforeVersion = row.getVersion();
        ChildWantEat update = new ChildWantEat();
        update.setStatus(status);
        update.setVersion(Math.incrementExact(beforeVersion));
        if (wantEats.update(update, new UpdateWrapper<ChildWantEat>().eq("id", row.getId())
                .eq("version", beforeVersion)) != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        audit.record("WANT_EAT_STATUS", UserContext.userId(), member.getFamilyId(), "CHILD", row.getChildId(),
                null, "wantEatId=" + wantEatId + ";status=" + status);
        if ("COOKED".equals(status)) {
            // 采纳反馈闭环：「家长真的做了孩子点的菜」是正向激励时刻，按累计被做次数发勋章。
            // 必须放在乐观锁更新成功之后，否则会把失败的流转也算进去。
            awardMenuMedals(member);
        }
        return WantEatResp.builder().childId(row.getChildId()).menuDate(row.getMenuDate())
                .mealType(row.getMealType())
                .wantEat(currentWantEat(row.getChildId(), row.getMenuDate(), row.getMealType())).build();
    }

    /**
     * 想吃被做（COOKED）累计计数 → 发 {@code category='MEAL'} 的 COUNT 类勋章。
     * 幂等由 {@link MedalService#award} 的 (definition_id, child_id, ref_id=阈值) 唯一键保证（重复标记、回退再标都不会重复发）。
     * 必须在已持有写事务时调用（MedalService.award 为 {@code Propagation.MANDATORY}）。
     */
    private void awardMenuMedals(FamilyMember member) {
        long cooked = wantEats.selectCount(new QueryWrapper<ChildWantEat>()
                .eq("child_id", member.getUserId()).eq("status", "COOKED"));
        if (cooked <= 0) {
            return;
        }
        List<MedalDefinition> defs = medalDefs.selectList(new QueryWrapper<MedalDefinition>()
                .eq("category", "MEAL").eq("status", "NORMAL").eq("condition_type", "COUNT"));
        for (MedalDefinition def : defs) {
            if (def.getThreshold() != null && cooked >= def.getThreshold()) {
                medalService.award(member.getUserId(), member.getFamilyId(), def.getCode(),
                        (long) def.getThreshold(), 0);
            }
        }
    }

    /**
     * 派生「爱吃/常点」：近 {@link #FREQUENT_WINDOW_DAYS} 天按 (type,id) 统计出现天数并做近期加权
     * （1/(1+距今天数/7)），取 Top N。菜品已删除或非在售的直接跳过（派生视图允许缺失）。纯读，不加锁。
     */
    public FrequentDishResp frequentDishes(Long childId, int limit) {
        LoginUser ctx = requireRole("CHILD", "PARENT");
        if ("CHILD".equals(ctx.getRole())) {
            if (childId != null && !childId.equals(ctx.getUserId())) {
                throw new BizException(ResultCode.E009_FORBIDDEN);
            }
            childId = ctx.getUserId();
        } else {
            positive(childId);
        }
        FamilyMember member = authorization.boundChild(childId);
        if ("PARENT".equals(ctx.getRole())) {
            authorization.requireParent(member.getFamilyId());
        } else {
            authorization.requireConsentReadOnly(member);
        }
        int top = limit <= 0 ? FREQUENT_DEFAULT_LIMIT : Math.min(limit, 20);
        LocalDate today = time.today();
        List<ChildWantEat> rows = wantEatRows(childId, today.minusDays(FREQUENT_WINDOW_DAYS - 1L), today);
        Map<String, Set<LocalDate>> datesByRef = new LinkedHashMap<>();
        Map<String, ChildWantEat> sampleByRef = new LinkedHashMap<>();
        for (ChildWantEat row : rows) {
            String key = row.getDishType() + ":" + row.getDishId();
            datesByRef.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(row.getMenuDate());
            sampleByRef.putIfAbsent(key, row);
        }
        Map<String, Dish> dishByRef = visibleDishesByRef(
                sampleByRef.values().stream().map(this::refOf).toList(), member.getFamilyId());
        Map<Long, String> categoryNames = categoryNamesOf(dishByRef.values());
        Map<String, Double> scores = new HashMap<>();
        datesByRef.forEach((key, dates) -> scores.put(key, dates.stream()
                .mapToDouble(date -> 1.0 / (1.0 + ChronoUnit.DAYS.between(date, today) / 7.0)).sum()));
        List<String> ranked = dishByRef.entrySet().stream()
                .filter(entry -> "ON_SALE".equals(entry.getValue().getStatus()))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingDouble((String key) -> scores.getOrDefault(key, 0.0)).reversed()
                        .thenComparing(key -> datesByRef.get(key).stream().max(LocalDate::compareTo)
                                .orElse(LocalDate.MIN), Comparator.reverseOrder()))
                .limit(top).toList();
        List<FrequentDishResp.Item> dishes = new ArrayList<>();
        for (String key : ranked) {
            Dish dish = dishByRef.get(key);
            ChildWantEat sample = sampleByRef.get(key);
            dishes.add(FrequentDishResp.Item.builder().type(sample.getDishType()).id(sample.getDishId())
                    .name(dish.getName()).imageUrl(dish.getImageUrl())
                    .categoryId(dish.getCategoryId()).categoryName(categoryNames.get(dish.getCategoryId()))
                    .count(datesByRef.get(key).size()).build());
        }
        return FrequentDishResp.builder().dishes(dishes).build();
    }

    /**
     * 「今天吃什么」引导式推荐：在指定菜单内按画像 + 历史 + 忌口打分排序，返回 Top N 与推荐理由。
     * 安全硬约束：含过敏原的菜品一律剔除，不受任何加分影响。纯读，不加锁。
     */
    public RecommendResp recommend(Long menuId, Long childId, int limit) {
        LoginUser ctx = requireRole("CHILD", "PARENT");
        positive(menuId);
        int top = limit <= 0 ? RECOMMEND_DEFAULT_LIMIT : limit;
        if (top > RECOMMEND_MAX_LIMIT) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        if ("CHILD".equals(ctx.getRole())) {
            if (childId != null && !childId.equals(ctx.getUserId())) {
                throw new BizException(ResultCode.E009_FORBIDDEN);
            }
            childId = ctx.getUserId();
        } else {
            positive(childId);
        }
        FamilyMember member = authorization.boundChild(childId);
        if ("PARENT".equals(ctx.getRole())) {
            authorization.requireParent(member.getFamilyId());
        }
        // 与 parentWantEatBoard 一致：读写孩子的餐食画像数据前，家长侧也校验一次有效同意（只读，不锁）。
        authorization.requireConsentReadOnly(member);
        ChildProfile profile = requireCompleteProfile(member);
        // 菜单校验与 markFavorite 同源：只认已发布且确实属于该孩子的菜单（越权统一 404，不暴露存在性）。
        MenuDaily menu = menus.selectOne(new QueryWrapper<MenuDaily>().eq("id", menuId).eq("status", "PUBLISHED"));
        if (menu == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        if ("FAMILY".equals(menu.getSourceType())) {
            if (!member.getFamilyId().equals(menu.getFamilyId())) {
                throw new BizException(ResultCode.E404_NOT_FOUND);
            }
        } else if ("SCHOOL".equals(menu.getSourceType())) {
            String school = profile.getSchool() == null ? null : profile.getSchool().trim();
            if (school == null || school.isEmpty() || !school.equals(menu.getOwnerKey())) {
                throw new BizException(ResultCode.E404_NOT_FOUND);
            }
        } else {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        Map<String, Dish> candidates = visibleDishesByRef(menu.getDishIds(), member.getFamilyId());
        Map<Long, String> categoryNames = categoryNamesOf(candidates.values());
        LocalDate today = time.today();
        // 常吃：近 30 天出现天数
        Map<String, Integer> frequent = new HashMap<>();
        for (ChildWantEat row : wantEatRows(childId, today.minusDays(FREQUENT_WINDOW_DAYS - 1L), today)) {
            frequent.merge(row.getDishType() + ":" + row.getDishId(), 1, Integer::sum);
        }
        // 「本周还没吃过」以菜单所在自然周（周一起）为准，而非"今天所在周"，否则选未来菜单时该加分恒失效。
        LocalDate weekStart = menu.getMenuDate().with(java.time.DayOfWeek.MONDAY);
        Set<String> markedThisWeek = new HashSet<>();
        for (ChildWantEat row : wantEatRows(childId, weekStart, weekStart.plusDays(6))) {
            markedThisWeek.add(row.getDishType() + ":" + row.getDishId());
        }
        boolean mildPreference = prefersMild(profile);
        List<RecommendResp.Item> scored = new ArrayList<>();
        for (DishRef ref : menu.getDishIds()) {
            String key = ref.getType() + ":" + ref.getId();
            Dish dish = candidates.get(key);
            if (dish == null || !"ON_SALE".equals(dish.getStatus())) {
                continue;
            }
            if (allergyConflict(dish, profile)) {
                continue; // 安全红线：过敏原命中直接剔除
            }
            if (isDisliked(dish, profile)) {
                continue;
            }
            int score = 0;
            List<String> reasons = new ArrayList<>();
            int count = frequent.getOrDefault(key, 0);
            if (count > 0) {
                score += 2 * Math.min(count, 5);
                reasons.add("近 30 天想吃 " + count + " 次");
            }
            if (!markedThisWeek.contains(key)) {
                score += 2;
                reasons.add("本周还没吃过");
            }
            boolean mildDish = dish.getSpiceLevel() != null && dish.getSpiceLevel() <= 1;
            if (mildPreference && mildDish) {
                score += 1;
                reasons.add("符合清淡偏好");
            }
            if (dish.getSpiceLevel() != null && dish.getSpiceLevel() >= 3) {
                score -= 2;
                reasons.add("偏辣");
            }
            scored.add(RecommendResp.Item.builder().type(ref.getType()).id(ref.getId()).name(dish.getName())
                    .imageUrl(dish.getImageUrl()).categoryId(dish.getCategoryId())
                    .categoryName(categoryNames.get(dish.getCategoryId()))
                    .score(score).reasons(reasons).build());
        }
        scored.sort(Comparator.comparingInt(RecommendResp.Item::getScore).reversed()
                .thenComparing(RecommendResp.Item::getId));
        return RecommendResp.builder().menuId(menu.getId()).menuDate(menu.getMenuDate())
                .mealType(menu.getMealType())
                .dishes(scored.stream().limit(top).toList()).build();
    }

    /**
     * 家长周视图：区间内「每天 × 每餐」的家庭菜单概览，用于"整周发布"。
     * 纯读、不加锁；未发布的餐次也返回格子（menuId=null），不丢格。
     */
    public MenuWeekResp parentMenuWeek(LocalDate from, LocalDate to) {
        LoginUser ctx = requireRole("PARENT");
        validateWeekRange(from, to);
        Long familyId = ctx.firstFamilyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        // 只读校验：不锁 family 行（与 R6 约定一致，避免家长查看周视图时阻塞发布/点单写事务）。
        authorization.requireParent(familyId);
        return MenuWeekResp.builder().from(from).to(to).today(time.today())
                .days(weekSkeleton(from, to, weekMenuIndex(from, to, "FAMILY", familyId.toString()), null)).build();
    }

    /**
     * 孩子周视图：整周各餐次是否有菜单 + 我已标记几道，用于周条与"提前挑选"。
     * FAMILY（本家庭）优先于 SCHOOL（档案学校）占用同一格。纯读、不加锁。
     */
    public MenuWeekResp childMenuWeek(Long childId, LocalDate from, LocalDate to) {
        LoginUser ctx = requireRole("CHILD", "PARENT");
        validateWeekRange(from, to);
        if ("CHILD".equals(ctx.getRole())) {
            if (childId != null && !childId.equals(ctx.getUserId())) {
                throw new BizException(ResultCode.E009_FORBIDDEN);
            }
            childId = ctx.getUserId();
        } else {
            positive(childId);
        }
        FamilyMember member = authorization.boundChild(childId);
        if ("PARENT".equals(ctx.getRole())) {
            authorization.requireParent(member.getFamilyId());
        }
        authorization.requireConsentReadOnly(member);
        ChildProfile profile = requireCompleteProfile(member);
        Map<String, MenuDaily> index = weekMenuIndex(from, to, "FAMILY", member.getFamilyId().toString());
        String school = profile.getSchool() == null ? null : profile.getSchool().trim();
        if (school != null && !school.isEmpty()) {
            for (Map.Entry<String, MenuDaily> entry : weekMenuIndex(from, to, "SCHOOL", school).entrySet()) {
                index.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        Map<String, Integer> wantCount = new HashMap<>();
        for (ChildWantEat row : wantEatRows(childId, from, to)) {
            wantCount.merge(row.getMenuDate() + "|" + row.getMealType(), 1, Integer::sum);
        }
        return MenuWeekResp.builder().from(from).to(to).today(time.today())
                .days(weekSkeleton(from, to, index, wantCount)).build();
    }

    /**
     * 家长「整周发布」：逐条独立事务、允许部分成功（单条失败不回滚整批）。
     * <p>
     * <b>必须挂起类级事务（{@code NOT_SUPPORTED}）</b>：鉴权 {@code requireCurrentFamilyParent} 会对家庭行
     * 加 {@code FOR UPDATE}，而每条 item 的子事务用的是 {@code REQUIRES_NEW}。若本方法仍在外层事务内，
     * 子事务会先<b>挂起外层事务</b>（外层事务的行锁不释放）再去抢同一行 → 自己把自己锁死，批量请求挂起不返回。
     * 因此这里：① 方法本身无事务；② 鉴权单独放进一个短事务并立刻提交，不跨批量循环持锁。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MenuBatchResp publishFamilyMenuBatch(List<MenuDailyBatchReq.Item> items) {
        if (items == null || items.isEmpty() || items.size() > BATCH_MAX_ITEMS
                || items.stream().anyMatch(Objects::isNull)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        // ① 鉴权只做一次（独立短事务，提交后立即释放家庭行锁），并拿到 familyId 供各条复用。
        Long familyId = perItemTransactions.execute(status -> requireCurrentFamilyParent().firstFamilyId());
        // ② 逐条独立事务：此时无外层事务可挂起，子事务之间串行抢锁、各自提交，单条失败不影响其余。
        List<MenuBatchResp.Result> results = new ArrayList<>();
        int ok = 0;
        for (MenuDailyBatchReq.Item item : items) {
            String code = null;
            try {
                perItemTransactions.executeWithoutResult(status -> upsertAuthorizedFamilyMenu(familyId, item));
                ok++;
            } catch (BizException error) {
                code = error.getResultCode().getCode();
            } catch (RuntimeException error) {
                code = ResultCode.E500_SYSTEM_ERROR.getCode();
            }
            results.add(MenuBatchResp.Result.builder().menuDate(item.getMenuDate()).mealType(item.getMealType())
                    .ok(code == null).code(code).build());
        }
        return MenuBatchResp.builder().okCount(ok).failCount(results.size() - ok).results(results).build();
    }

    /**
     * 批量发布单条：复用 {@link #upsertFamilyMenu} 的校验与写入链路，但<b>不重复鉴权</b>
     * （批量入口已鉴权并解析出 familyId，避免 21 次 FOR UPDATE 家庭行）。
     */
    private MenuUpsertResp upsertAuthorizedFamilyMenu(Long familyId, MenuDailyBatchReq.Item item) {
        MenuDailyReq req = new MenuDailyReq();
        req.setMenuDate(item.getMenuDate());
        req.setMealType(item.getMealType());
        req.setDishIds(item.getDishIds());
        req.setStatus(item.getStatus());
        validateMenu(req);
        return upsertMenu(req, "FAMILY", familyId, null);
    }

    /** 周视图区间校验：必填、from&lt;=to、跨度（含首尾）不超过 {@link #WEEK_MAX_DAYS} 天。 */
    private void validateWeekRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)
                || ChronoUnit.DAYS.between(from, to) + 1 > WEEK_MAX_DAYS) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
    }

    /** 区间菜单索引 key="日期|餐次" → 菜单（同一格 FAMILY 优先，调用方用 putIfAbsent 保证）。 */
    private Map<String, MenuDaily> weekMenuIndex(LocalDate from, LocalDate to, String sourceType, String ownerKey) {
        Map<String, MenuDaily> index = new HashMap<>();
        for (MenuDaily menu : menus.selectList(new QueryWrapper<MenuDaily>().eq("source_type", sourceType)
                .eq("owner_key", ownerKey).between("menu_date", from, to))) {
            index.put(menu.getMenuDate() + "|" + menu.getMealType(), menu);
        }
        return index;
    }

    /** 生成"每一天 × 三餐"骨架（未发布也占位）；wantCount 为 null 时 wantEatCount/marked 固定 0/false。 */
    private List<MenuWeekResp.Day> weekSkeleton(LocalDate from, LocalDate to, Map<String, MenuDaily> index,
            Map<String, Integer> wantCount) {
        List<MenuWeekResp.Day> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<MenuWeekResp.Meal> meals = new ArrayList<>();
            for (String mealType : List.of("BREAKFAST", "LUNCH", "DINNER")) {
                String key = date + "|" + mealType;
                MenuDaily menu = index.get(key);
                int count = wantCount == null ? 0 : wantCount.getOrDefault(key, 0);
                meals.add(MenuWeekResp.Meal.builder().mealType(mealType)
                        .menuId(menu == null ? null : menu.getId())
                        .sourceType(menu == null ? null : menu.getSourceType())
                        .status(menu == null ? null : menu.getStatus())
                        .dishCount(menu == null || menu.getDishIds() == null ? 0 : menu.getDishIds().size())
                        .wantEatCount(count).marked(count > 0).build());
            }
            days.add(MenuWeekResp.Day.builder().menuDate(date).meals(meals).build());
        }
        return days;
    }

    /** 画像是否偏好清淡（用于给低辣度菜品加分；仅在画像明确表达时生效，避免给所有菜无差别加分）。 */
    private boolean prefersMild(ChildProfile profile) {
        return profile.getTastes() != null && profile.getTastes().stream().filter(Objects::nonNull)
                .anyMatch(taste -> MILD_TASTE_WORDS.stream().anyMatch(taste::contains));
    }

    private void validateBoardRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)
                || ChronoUnit.DAYS.between(from, to) + 1 > BOARD_MAX_DAYS) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
    }

    private WantEatBoardResp.Item boardItem(ChildWantEat row, Dish dish, Map<Long, String> categoryNames,
            ChildProfile profile, boolean expired) {
        return WantEatBoardResp.Item.builder().wantEatId(row.getId()).type(row.getDishType()).id(row.getDishId())
                .name(dish == null ? null : dish.getName())
                .imageUrl(dish == null ? null : dish.getImageUrl())
                .categoryId(dish == null ? null : dish.getCategoryId())
                .categoryName(dish == null ? null : categoryNames.get(dish.getCategoryId()))
                .status(row.getStatus()).version(row.getVersion()).expired(expired)
                .allergyConflict(dish != null && allergyConflict(dish, profile))
                .disliked(dish != null && isDisliked(dish, profile)).missing(dish == null).build();
    }

    private List<ChildWantEat> wantEatRows(Long childId, LocalDate from, LocalDate to) {
        return wantEats.selectList(new QueryWrapper<ChildWantEat>().eq("child_id", childId)
                .between("menu_date", from, to)
                .orderByAsc("menu_date").orderByAsc("meal_type").orderByAsc("id"));
    }

    private DishRef refOf(ChildWantEat row) {
        DishRef ref = new DishRef();
        ref.setType(row.getDishType());
        ref.setId(row.getDishId());
        return ref;
    }

    private static Comparator<String> mealOrder() {
        List<String> order = List.of("BREAKFAST", "LUNCH", "DINNER");
        return Comparator.comparingInt(meal -> {
            int index = order.indexOf(meal);
            return index < 0 ? order.size() : index;
        });
    }

    /** 区间菜单索引 key="来源|日期|餐次" → menuId；菜单已删则缺键（返回 null，但不丢想吃行）。 */
    private Map<String, Long> menuIndex(LocalDate from, LocalDate to, FamilyMember member, ChildProfile profile) {
        Map<String, Long> index = new HashMap<>();
        for (MenuDaily menu : menus.selectList(new QueryWrapper<MenuDaily>().eq("source_type", "FAMILY")
                .eq("owner_key", member.getFamilyId().toString()).between("menu_date", from, to))) {
            index.put("FAMILY|" + menu.getMenuDate() + "|" + menu.getMealType(), menu.getId());
        }
        String school = profile.getSchool() == null ? null : profile.getSchool().trim();
        if (school != null && !school.isEmpty()) {
            for (MenuDaily menu : menus.selectList(new QueryWrapper<MenuDaily>().eq("source_type", "SCHOOL")
                    .eq("owner_key", school).between("menu_date", from, to))) {
                index.put("SCHOOL|" + menu.getMenuDate() + "|" + menu.getMealType(), menu.getId());
            }
        }
        return index;
    }

    /** 采购汇总累加器：按 (type,id) 去重，记录出现天数与菜品名。 */
    private static final class SummaryAccumulator {
        private final String type;
        private final Long id;
        private final Set<LocalDate> dates = new LinkedHashSet<>();
        private String name;

        private SummaryAccumulator(String type, Long id) {
            this.type = type;
            this.id = id;
        }

        private void add(ChildWantEat row, Dish dish) {
            dates.add(row.getMenuDate());
            if (name == null && dish != null) {
                name = dish.getName();
            }
        }

        private WantEatBoardResp.SummaryDish toSummaryDish() {
            return WantEatBoardResp.SummaryDish.builder().type(type).id(id).name(name)
                    .count(dates.size()).dates(new ArrayList<>(dates)).build();
        }
    }

    private QueryWrapper<ChildWantEat> wantEatKey(Long childId, LocalDate menuDate, String mealType,
            String dishType, Long dishId) {
        return new QueryWrapper<ChildWantEat>().eq("child_id", childId).eq("menu_date", menuDate)
                .eq("meal_type", mealType).eq("dish_type", dishType).eq("dish_id", dishId);
    }

    /**
     * 想吃标记必须显式携带 dishType（PRESET/FAMILY）。该类型由每日菜单的菜品引用权威给出，
     * 客户端从 daily 响应中即可获得；服务端不再按存在性猜测，从根本上消除 PRESET/FAMILY 同号歧义。
     */
    private String resolveDishType(MarkFavoriteReq req) {
        if (req.getDishType() == null || !Set.of("PRESET", "FAMILY").contains(req.getDishType())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        return req.getDishType();
    }

    private void lockDishByType(String type, Long dishId, Long familyId) {
        if ("PRESET".equals(type)) {
            lockDish(dishId);
            return;
        }
        FamilyDish fd = familyDishes.selectOne(new QueryWrapper<FamilyDish>()
                .eq("id", dishId).eq("family_id", familyId).last("FOR UPDATE"));
        if (fd == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
    }

    /**
     * Caller holds authorization and (when applicable) confirmation locks in the same transaction.
     * Menu and dish locks remain held until that outer transaction commits.
     */
    @Transactional
    public ValidatedMenu validateOrder(Long menuId, FamilyMember member, List<OrderLineReq> items) {
        return validateOrder(menuId, member, items, time.today());
    }

    @Transactional
    public ValidatedMenu validateOrder(Long menuId, FamilyMember member, List<OrderLineReq> items,
            LocalDate usageDate) {
        LoginUser ctx = requireRole("CHILD", "PARENT");
        positive(menuId);
        if (member == null || member.getFamilyId() == null || member.getUserId() == null
                || !"CHILD".equals(member.getRole()) || !"BOUND".equals(member.getBindStatus())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        if ("CHILD".equals(ctx.getRole())) {
            if (!ctx.getUserId().equals(member.getUserId())) {
                throw new BizException(ResultCode.E009_FORBIDDEN);
            }
        } else {
            authorization.requireParent(member.getFamilyId());
        }
        authorization.requireConsent(member);
        ChildProfile profile = completeProfile(member);
        if (items == null || items.isEmpty() || items.size() > 20) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        Set<DishRef> requested = new LinkedHashSet<>();
        for (OrderLineReq item : items) {
            validate(item);
            if (!requested.add(item.getDishRef())) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
            }
        }
        MenuDaily menu = menus.selectOne(new QueryWrapper<MenuDaily>().eq("id", menuId).last("FOR UPDATE"));
        if (menu == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        if (!"FAMILY".equals(menu.getSourceType()) || !member.getFamilyId().equals(menu.getFamilyId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        if (!"PUBLISHED".equals(menu.getStatus()) || !menu.getMenuDate().equals(usageDate)) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        if (menu.getDishIds() == null || !menu.getDishIds().containsAll(requested)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        Map<String, Dish> locked = lockVisibleDishesByRef(requested, menu.getFamilyId());
        if (locked.size() != requested.size()) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        List<Dish> ordered = new ArrayList<>();
        for (OrderLineReq item : items) {
            DishRef ref = item.getDishRef();
            Dish dish = locked.get(ref.getType() + ":" + ref.getId());
            if (dish == null || !"DECLARED".equals(safetyStatus(dish, profile))) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
            ordered.add(dish);
        }
        return new ValidatedMenu(menu, List.copyOf(ordered));
    }

    public record ValidatedMenu(MenuDaily menu, List<Dish> dishes) {
    }

    private Map<Long, Dish> lockDishes(Collection<Long> dishIds) {
        Map<Long, Dish> locked = lockVisibleDishes(dishIds);
        if (locked.size() != new HashSet<>(dishIds).size()) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        return locked;
    }

    private Map<Long, Dish> lockVisibleDishes(Collection<Long> dishIds) {
        Map<Long, Dish> locked = new HashMap<>();
        // Separate primary-key reads guarantee lock order independently of the optimizer's IN-query plan.
        for (Long dishId : new TreeSet<>(dishIds)) {
            Dish dish = dishes.selectOne(new QueryWrapper<Dish>().eq("id", dishId).last("FOR UPDATE"));
            if (dish != null) {
                locked.put(dishId, dish);
            }
        }
        return locked;
    }

    private Dish lockDish(Long dishId) {
        Dish dish = dishes.selectOne(new QueryWrapper<Dish>().eq("id", dishId).last("FOR UPDATE"));
        if (dish == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        return dish;
    }

    private ChildProfile completeProfile(FamilyMember member) {
        return requireProfile(member, true);
    }

    /** 只读版：与 completeProfile 判定一致，但不加行锁（纯展示路径用）。 */
    private ChildProfile requireCompleteProfile(FamilyMember member) {
        return requireProfile(member, false);
    }

    private ChildProfile requireProfile(FamilyMember member, boolean lock) {
        QueryWrapper<ChildProfile> query = new QueryWrapper<ChildProfile>()
                .eq("family_id", member.getFamilyId()).eq("user_id", member.getUserId());
        ChildProfile profile = profiles.selectOne(lock ? query.last("FOR UPDATE") : query);
        if (profile == null || !"COMPLETE".equals(profile.getProfileStatus())) {
            throw new BizException(ResultCode.E002_PROFILE_INCOMPLETE);
        }
        return profile;
    }

    private String safetyStatus(Dish dish, ChildProfile profile) {
        if (!"ON_SALE".equals(dish.getStatus())) {
            return "OFF_SALE";
        }
        // Legacy/null/unpublished codes fail closed, even if an old row claims DECLARED.
        if (!"DECLARED".equals(dish.getAllergenStatus()) || !validAllergens(dish.getAllergens())
                || !validAllergens(profile.getAllergies())) {
            return "UNKNOWN";
        }
        return allergyConflict(dish, profile) ? "ALLERGY_CONFLICT" : "DECLARED";
    }

    private boolean allergyConflict(Dish dish, ChildProfile profile) {
        return dish.getAllergens() != null && profile.getAllergies() != null
                && dish.getAllergens().stream().anyMatch(profile.getAllergies()::contains);
    }

    private boolean isDisliked(Dish dish, ChildProfile profile) {
        return profile.getDislikes() != null && profile.getDislikes().stream()
                .filter(Objects::nonNull).filter(value -> !value.isBlank())
                .anyMatch(value -> dish.getName().contains(value)
                        || dish.getTags() != null && dish.getTags().contains(value));
    }

    private void validateDish(DishReq req) {
        validate(req);
        // 配方六个字段全部可选、且不参与「在售」闸门；规则与可定位的中文原因由 DishRecipeService 统一给出
        recipes.validate(req.getIngredients(), req.getCookSteps(), req.getCookTips(),
                req.getCookMinutes(), req.getServings(), req.getDifficulty());
        DishCategory category = categories.selectById(req.getCategoryId());
        if (category == null || !"ENABLED".equals(category.getStatus())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "菜品分类不存在或已停用");
        }
        if (!validAllergens(req.getAllergens())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT,
                    policy.getCatalogReference() == null || policy.getCatalogReference().isBlank()
                            ? "过敏原发布目录未配置（compliance.catalog-reference）"
                            : "过敏原取值不在发布目录内，或存在空值 / 重复值 / 超长值");
        }
        // R5-c：上架即对儿童可见 ⇒ 必须先声明过敏原。未声明只能存在于下架态（保留草稿式录入体验），
        // 避免「未登记过敏原」的菜悄悄上线让孩子点不了（safetyStatus 会判 UNKNOWN、canSelect=false）。
        if ("ON_SALE".equals(req.getStatus()) && !"DECLARED".equals(req.getAllergenStatus())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "上架菜品必须先声明过敏原");
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

    private void validateMenu(MenuDailyReq req) {
        validate(req);
        validateDateAndMeal(req.getMenuDate(), req.getMealType());
    }

    private void validateDateAndMeal(LocalDate date, String mealType) {
        if (date == null || date.getYear() < 1000 || date.getYear() > 9999
                || mealType == null || !MEALS.contains(mealType)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
    }

    private QueryWrapper<MenuDaily> menuKey(String sourceType, String ownerKey, LocalDate date, String mealType) {
        return new QueryWrapper<MenuDaily>().eq("source_type", sourceType).eq("owner_key", ownerKey)
                .eq("menu_date", date).eq("meal_type", mealType);
    }

    private void applyDish(Dish dish, DishReq req) {
        dish.setCategoryId(req.getCategoryId());
        dish.setName(req.getName().trim());
        dish.setImageUrl(req.getImageUrl());
        dish.setVirtualPrice(req.getVirtualPrice().setScale(2));
        dish.setCalories(req.getCalories());
        dish.setTags(req.getTags());
        dish.setAllergens(List.copyOf(req.getAllergens()));
        dish.setAllergenStatus(req.getAllergenStatus());
        dish.setSpiceLevel(req.getSpiceLevel());
        dish.setStatus(req.getStatus());
        applyRecipe(dish, req);
    }

    /**
     * 配方字段填充（归一化：空串 → null、null / 空数组 → 空列表）。
     * cookSteps 归一化成空数组而非 null 是有意的：列定义 NOT NULL，且让「清空做法」可落库。
     */
    private void applyRecipe(Dish dish, DishReq req) {
        dish.setCookSteps(DishRecipeService.normalizeSteps(req.getCookSteps()));
        dish.setCookTips(DishRecipeService.normalizeTips(req.getCookTips()));
        dish.setCookMinutes(req.getCookMinutes());
        dish.setServings(req.getServings());
        dish.setDifficulty(DishRecipeService.normalizeDifficulty(req.getDifficulty()));
        dish.setIngredients(DishRecipeService.normalizeIngredients(req.getIngredients()));
    }

    /**
     * 单品响应（详情路径）：确保食材明细已装配后返回完整 recipe。
     * 创建 / 更新路径已把归一化结果放进瞬时字段，这里不再回查；其余情况单次查询补齐。
     */
    private DishResp dishResponse(Dish dish) {
        if (dish.getIngredients() == null) {
            dish.setIngredients(recipes.loadByDishId(DishRecipeService.OWNER_PRESET, dish.getId()));
        }
        return dishResponse(dish, null, true);
    }

    /** 列表 / 菜单路径响应：只带轻量摘要与计数，recipe 为 null（明细不进清单响应）。 */
    private DishResp dishResponse(Dish dish, Map<Long, String> categoryNames) {
        return dishResponse(dish, categoryNames, false);
    }

    /**
     * 统一响应装配：withRecipe 由调用点显式传入，不用「categoryNames == null 就当详情」这类隐式推断。
     * 食材明细未装配时按空列表处理，与「这道菜确实没配方」的语义一致。
     */
    private DishResp dishResponse(Dish dish, Map<Long, String> categoryNames, boolean withRecipe) {
        List<DishIngredient> ingredientItems = dish.getIngredients() == null ? List.of() : dish.getIngredients();
        List<String> cookSteps = dish.getCookSteps() == null ? List.of() : dish.getCookSteps();
        return DishResp.builder().dishId(dish.getId()).categoryId(dish.getCategoryId())
                .categoryName(categoryNames == null ? categoryName(dish.getCategoryId())
                        : categoryNames.get(dish.getCategoryId()))
                .name(dish.getName())
                .imageUrl(dish.getImageUrl()).virtualPrice(dish.getVirtualPrice().setScale(2).toPlainString())
                .calories(dish.getCalories()).tags(dish.getTags()).allergens(dish.getAllergens())
                .allergenStatus(dish.getAllergenStatus()).spiceLevel(dish.getSpiceLevel())
                .status(dish.getStatus()).sourceType("PRESET")
                .cookMinutes(dish.getCookMinutes()).servings(dish.getServings()).difficulty(dish.getDifficulty())
                .ingredientCount(ingredientItems.size()).stepCount(cookSteps.size())
                .recipe(withRecipe ? new DishRecipeResp(ingredientItems, cookSteps, dish.getCookTips(),
                        dish.getCookMinutes(), dish.getServings(), dish.getDifficulty()) : null)
                .build();
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        DishCategory category = categories.selectById(categoryId);
        return category == null ? null : category.getName();
    }

    /** 批量取分类名 id→name，供列表响应避免 N+1。 */
    private Map<Long, String> categoryNameMap(Collection<Long> categoryIds) {
        List<Long> ids = categoryIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (DishCategory category : categories.selectBatchIds(ids)) {
            names.put(category.getId(), category.getName());
        }
        return names;
    }

    /** 从菜品集合批量预取分类名（列表路径用）。 */
    private Map<Long, String> categoryNamesOf(Collection<Dish> dishes) {
        return categoryNameMap(dishes.stream().map(Dish::getCategoryId).toList());
    }

    /**
     * 列表路径批量装配食材（全部为预置菜品）：一次 IN 查询后内存分组，未命中（确无食材）的补空列表。
     * 补空列表而非留 null 是关键：让「确无配方」与「本响应没带配方」在响应里可区分。
     */
    private void attachPresetIngredients(Collection<Dish> dishList) {
        List<Dish> targets = dishList.stream().filter(Objects::nonNull).toList();
        if (targets.isEmpty()) {
            return;
        }
        Map<Long, List<DishIngredient>> grouped = recipes.loadByDishIds(DishRecipeService.OWNER_PRESET,
                targets.stream().map(Dish::getId).toList());
        targets.forEach(dish -> dish.setIngredients(grouped.getOrDefault(dish.getId(), List.of())));
    }

    /**
     * 菜单路径批量装配食材：入参可能是预置与家庭菜品的混合集合，按 ref key 的 type 前缀分组，
     * 每种 owner_type 各一次 IN 查询（最多 2 次），同样不做逐条查询。
     */
    private void attachIngredientsByRef(Map<String, Dish> byRefKey) {
        if (byRefKey.isEmpty()) {
            return;
        }
        attachIngredientsOfType(byRefKey, DishRecipeService.OWNER_PRESET);
        attachIngredientsOfType(byRefKey, DishRecipeService.OWNER_FAMILY);
        byRefKey.values().forEach(dish -> {
            if (dish.getIngredients() == null) {
                dish.setIngredients(List.of());
            }
        });
    }

    private void attachIngredientsOfType(Map<String, Dish> byRefKey, String ownerType) {
        List<Long> ids = byRefKey.keySet().stream().filter(key -> key.startsWith(ownerType + ":"))
                .map(key -> Long.valueOf(key.substring(ownerType.length() + 1))).toList();
        if (ids.isEmpty()) {
            return;
        }
        recipes.loadByDishIds(ownerType, ids).forEach((dishId, items) -> {
            Dish dish = byRefKey.get(ownerType + ":" + dishId);
            if (dish != null) {
                dish.setIngredients(items);
            }
        });
    }

    /** 家庭私有菜品 → Dish 临时对象（复制字段，用于统一的安全校验与点单，不持久化）。 */
    private Dish toDish(FamilyDish fd) {
        Dish dish = new Dish();
        dish.setId(fd.getId());
        dish.setCategoryId(fd.getCategoryId());
        dish.setName(fd.getName());
        dish.setImageUrl(fd.getImageUrl());
        dish.setVirtualPrice(fd.getVirtualPrice());
        dish.setCalories(fd.getCalories());
        dish.setTags(fd.getTags());
        dish.setAllergens(fd.getAllergens());
        dish.setAllergenStatus(fd.getAllergenStatus());
        dish.setSpiceLevel(fd.getSpiceLevel());
        dish.setStatus(fd.getStatus());
        // v011 配方标量一并复制，否则菜单里的家庭菜品摘要（时长 / 份量 / 难度）会丢
        dish.setCookSteps(fd.getCookSteps());
        dish.setCookTips(fd.getCookTips());
        dish.setCookMinutes(fd.getCookMinutes());
        dish.setServings(fd.getServings());
        dish.setDifficulty(fd.getDifficulty());
        return dish;
    }

    /** 家庭私有菜品 → 响应（sourceType=FAMILY）。 */
    private DishResp familyDishResponse(FamilyDish fd) {
        return familyDishResponse(fd, null);
    }

    /** 家庭私有菜品 → 响应（分类名批量预取版，避免列表 N+1）；食材按 owner_type=FAMILY 装配。 */
    private DishResp familyDishResponse(FamilyDish fd, Map<Long, String> categoryNames) {
        Dish dish = toDish(fd);
        dish.setIngredients(recipes.loadByDishId(DishRecipeService.OWNER_FAMILY, fd.getId()));
        DishResp resp = dishResponse(dish, categoryNames);
        resp.setSourceType("FAMILY");
        return resp;
    }

    /**
     * 按 DishRef.type 分流加锁：PRESET 查 life_dish，FAMILY 查 life_family_dish（带 family_id 隔离）。
     * 返回 key="type:id" 的映射，值为 Dish（FAMILY 已通过 toDish 转换）。
     */
    private Map<String, Dish> lockVisibleDishesByRef(Collection<DishRef> refs, Long familyId) {
        return visibleDishes(refs, familyId, true);
    }

    /** 只读版：与 lockVisibleDishesByRef 逻辑一致，但不加行锁（纯展示路径用）。 */
    private Map<String, Dish> visibleDishesByRef(Collection<DishRef> refs, Long familyId) {
        return visibleDishes(refs, familyId, false);
    }

    private Map<String, Dish> visibleDishes(Collection<DishRef> refs, Long familyId, boolean lock) {
        Map<String, Dish> visible = new HashMap<>();
        List<Long> presetIds = refs.stream().filter(r -> "PRESET".equals(r.getType()))
                .map(DishRef::getId).distinct().sorted().toList();
        for (Long dishId : presetIds) {
            QueryWrapper<Dish> query = new QueryWrapper<Dish>().eq("id", dishId);
            Dish dish = dishes.selectOne(lock ? query.last("FOR UPDATE") : query);
            if (dish != null) {
                visible.put("PRESET:" + dishId, dish);
            }
        }
        List<Long> familyIds = refs.stream().filter(r -> "FAMILY".equals(r.getType()))
                .map(DishRef::getId).distinct().sorted().toList();
        for (Long dishId : familyIds) {
            QueryWrapper<FamilyDish> query = new QueryWrapper<FamilyDish>()
                    .eq("id", dishId).eq(familyId != null, "family_id", familyId);
            FamilyDish fd = familyDishes.selectOne(lock ? query.last("FOR UPDATE") : query);
            if (fd != null) {
                visible.put("FAMILY:" + dishId, toDish(fd));
            }
        }
        return visible;
    }

    private DishCategoryResp categoryResponse(DishCategory category) {
        return DishCategoryResp.builder().categoryId(category.getId()).name(category.getName())
                .sort(category.getSort()).status(category.getStatus()).build();
    }

    private LoginUser requireCurrentFamilyParent() {
        LoginUser ctx = requireRole("PARENT");
        Long familyId = ctx.firstFamilyId();
        if (familyId == null || families.selectOne(new QueryWrapper<Family>()
                .eq("id", familyId).last("FOR UPDATE")) == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        // Family maintenance always locks and verifies the authenticated membership before catalog/menu locks.
        authorization.requireParent(familyId);
        return ctx;
    }

    private LoginUser requireRole(String... roles) {
        LoginUser ctx = UserContext.get();
        if (ctx == null) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH);
        }
        if (!Arrays.asList(roles).contains(ctx.getRole())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        return ctx;
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
    }

    private void positive(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
    }

    private long offset(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        return ((long) page - 1) * pageSize;
    }

    private void requireUpdated(int count) {
        if (count != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
    }

    private void requireInserted(int count) {
        if (count != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
    }
}
