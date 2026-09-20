package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.LoginUser;
import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.dto.request.*;
import cn.studykid.growthplanet.dto.response.*;
import cn.studykid.growthplanet.entity.*;
import cn.studykid.growthplanet.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class CatalogService {
    private static final Set<String> MEALS = Set.of("BREAKFAST", "LUNCH", "DINNER");
    private final DishCategoryMapper categories;
    private final DishMapper dishes;
    private final MenuDailyMapper menus;
    private final FamilyMapper families;
    private final ChildProfileMapper profiles;
    private final ChildAuthorizationService authorization;
    private final ComplianceProperties policy;
    private final AuditService audit;
    private final Validator validator;
    private final BusinessTime time;

    public CatalogService(DishCategoryMapper categories, DishMapper dishes, MenuDailyMapper menus,
            FamilyMapper families, ChildProfileMapper profiles, ChildAuthorizationService authorization,
            ComplianceProperties policy, AuditService audit, Validator validator, BusinessTime time) {
        this.categories = categories;
        this.dishes = dishes;
        this.menus = menus;
        this.families = families;
        this.profiles = profiles;
        this.authorization = authorization;
        this.policy = policy;
        this.audit = audit;
        this.validator = validator;
        this.time = time;
    }

    public DishCategoryResp createCategory(DishCategoryReq req) {
        requireRole("ADMIN");
        validate(req);
        DishCategory category = new DishCategory();
        category.setName(req.getName().trim());
        category.setSort(req.getSort());
        category.setStatus(req.getStatus());
        categories.insert(category);
        audit.record("CATEGORY_CREATE", UserContext.userId(), null, "CATEGORY", category.getId(), null, null);
        return categoryResponse(category);
    }

    public PageResp<DishCategoryResp> listCategories(int page, int pageSize) {
        requireRole("ADMIN");
        long offset = offset(page, pageSize);
        long total = categories.selectCount(new QueryWrapper<>());
        List<DishCategoryResp> items = categories.selectList(new QueryWrapper<DishCategory>()
                .orderByAsc("sort", "id").last("LIMIT " + offset + ", " + pageSize))
                .stream().map(this::categoryResponse).toList();
        return new PageResp<>(items, total, page, pageSize);
    }

    public DishResp createDish(DishReq req) {
        requireRole("ADMIN");
        validateDish(req);
        Dish dish = new Dish();
        applyDish(dish, req);
        dishes.insert(dish);
        audit.record("DISH_CREATE", UserContext.userId(), null, "DISH", dish.getId(), null, null);
        return dishResponse(dish);
    }

    public DishResp updateDish(Long dishId, DishReq req) {
        requireRole("ADMIN");
        positive(dishId);
        validateDish(req);
        Dish dish = lockDish(dishId);
        applyDish(dish, req);
        requireUpdated(dishes.updateById(dish));
        audit.record("DISH_UPDATE", UserContext.userId(), null, "DISH", dishId, null, null);
        return dishResponse(dish);
    }

    public void deleteDish(Long dishId) {
        requireRole("ADMIN");
        positive(dishId);
        lockDish(dishId);
        // Keep menu references and historical snapshots; subsequent validation observes the missing dish.
        requireUpdated(dishes.update(null, new UpdateWrapper<Dish>().eq("id", dishId)
                .set("status", "OFF_SALE").setSql("delete_at = UNIX_TIMESTAMP() * 1000")
                .setSql("update_time = CURRENT_TIMESTAMP")));
        audit.record("DISH_DELETE", UserContext.userId(), null, "DISH", dishId, null, null);
    }

    public DishResp getDish(Long dishId) {
        requireRole("ADMIN");
        positive(dishId);
        Dish dish = dishes.selectById(dishId);
        if (dish == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        return dishResponse(dish);
    }

    public PageResp<DishResp> listDishes(int page, int pageSize, Long categoryId, String status, String keyword) {
        requireRole("ADMIN");
        return queryDishes(page, pageSize, categoryId, status, keyword);
    }

    public PageResp<DishResp> listParentDishes(int page, int pageSize, Long categoryId, String keyword) {
        requireCurrentFamilyParent();
        return queryDishes(page, pageSize, categoryId, "ON_SALE", keyword);
    }

    private PageResp<DishResp> queryDishes(int page, int pageSize, Long categoryId, String status, String keyword) {
        long offset = offset(page, pageSize);
        if (categoryId != null) {
            positive(categoryId);
        }
        if (status != null && !Set.of("ON_SALE", "OFF_SALE").contains(status)
                || keyword != null && (keyword.isBlank() || keyword.length() > 64)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        QueryWrapper<Dish> query = new QueryWrapper<Dish>()
                .eq(categoryId != null, "category_id", categoryId)
                .eq(status != null, "status", status)
                .like(keyword != null, "name", keyword);
        long total = dishes.selectCount(query);
        List<DishResp> items = dishes.selectList(query.orderByAsc("id")
                .last("LIMIT " + offset + ", " + pageSize)).stream().map(this::dishResponse).toList();
        return new PageResp<>(items, total, page, pageSize);
    }

    public MenuUpsertResp upsertSchoolMenu(MenuDailyReq req) {
        requireRole("ADMIN");
        validateMenu(req);
        if (req.getSchool() == null || req.getSchool().isBlank()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        return upsertMenu(req, "SCHOOL", null, req.getSchool().trim());
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
        for (Dish dish : lockDishes(proposed.getDishIds()).values()) {
            if (!"ON_SALE".equals(dish.getStatus())) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
        }
        menu.setDishIds(proposed.getDishIds());
        menu.setStatus(proposed.getStatus());
        requireUpdated(menus.updateById(menu));
        audit.record("MENU_UPSERT", UserContext.userId(), familyId, "MENU", menu.getId(), null,
                "sourceType=" + sourceType);
        return new MenuUpsertResp(menu.getId());
    }

    public MenuMaintenanceResp getFamilyMenu(LocalDate menuDate, String mealType) {
        LoginUser ctx = requireCurrentFamilyParent();
        validateDateAndMeal(menuDate, mealType);
        Long familyId = ctx.firstFamilyId();
        MenuDaily menu = menus.selectOne(menuKey("FAMILY", familyId.toString(), menuDate, mealType)
                .last("FOR UPDATE"));
        if (menu == null) {
            // The maintenance page treats an unpublished date/meal as a normal empty state.
            audit.record("MENU_MAINTENANCE_QUERY", ctx.getUserId(), familyId, "MENU", null, null, "empty");
            return null;
        }
        Map<Long, Dish> current = lockVisibleDishes(menu.getDishIds());
        List<DishResp> visible = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Long dishId : menu.getDishIds()) {
            Dish dish = current.get(dishId);
            if (dish == null) {
                missing.add(dishId.toString());
            } else {
                visible.add(dishResponse(dish));
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
        FamilyMember member = authorization.lockBoundChild(childId);
        var consent = authorization.requireConsent(member);
        ChildProfile profile = completeProfile(member);
        String ownerKey = "FAMILY".equals(sourceType) ? member.getFamilyId().toString()
                : profile.getSchool() == null ? null : profile.getSchool().trim();
        MenuDaily menu = menus.selectOne(menuKey(sourceType, ownerKey, menuDate, mealType)
                .eq("status", "PUBLISHED").last("FOR UPDATE"));
        if (menu == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        Map<Long, Dish> current = lockVisibleDishes(menu.getDishIds());
        boolean submittable = "CHILD".equals(ctx.getRole()) && "FAMILY".equals(sourceType)
                && time.today().equals(menuDate);
        List<MenuDailyResp.MenuDishResp> visible = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Long dishId : menu.getDishIds()) {
            Dish dish = current.get(dishId);
            if (dish == null) {
                missing.add(dishId.toString());
                continue;
            }
            String safety = safetyStatus(dish, profile);
            visible.add(MenuDailyResp.MenuDishResp.builder().dish(dishResponse(dish))
                    .disliked(isDisliked(dish, profile)).favorite(favorites(profile).contains(dishId))
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

    public ChildPreferencesResp markFavorite(MarkFavoriteReq req) {
        LoginUser ctx = requireRole("CHILD");
        validate(req);
        FamilyMember member = authorization.lockBoundChild(ctx.getUserId());
        var consent = authorization.requireConsent(member);
        ChildProfile profile = completeProfile(member);
        Set<Long> favoriteIds = new LinkedHashSet<>(favorites(profile));
        if (req.getFavorite()) {
            lockDish(req.getDishId());
            favoriteIds.add(req.getDishId());
        } else {
            // Removal remains possible after the dish has been deleted from the catalog.
            favoriteIds.remove(req.getDishId());
        }
        ChildProfile update = new ChildProfile();
        update.setId(profile.getId());
        update.setFavoriteDishIds(new ArrayList<>(favoriteIds));
        requireUpdated(profiles.updateById(update));
        audit.record("FAVORITE", ctx.getUserId(), member.getFamilyId(), "CHILD", ctx.getUserId(), null,
                "consentId=" + consent.getId());
        return ChildPreferencesResp.builder().childId(ctx.getUserId()).dislikes(profile.getDislikes())
                .tastes(profile.getTastes()).favoriteDishIds(favoriteIds.stream().map(String::valueOf).toList()).build();
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
        Set<Long> requested = new LinkedHashSet<>();
        for (OrderLineReq item : items) {
            validate(item);
            if (!requested.add(item.getDishId())) {
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
        Map<Long, Dish> locked = lockDishes(requested);
        List<Dish> ordered = new ArrayList<>();
        for (OrderLineReq item : items) {
            Dish dish = locked.get(item.getDishId());
            if (!"DECLARED".equals(safetyStatus(dish, profile))) {
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
        ChildProfile profile = profiles.selectOne(new QueryWrapper<ChildProfile>()
                .eq("family_id", member.getFamilyId()).eq("user_id", member.getUserId()).last("FOR UPDATE"));
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

    private List<Long> favorites(ChildProfile profile) {
        return profile.getFavoriteDishIds() == null ? List.of() : profile.getFavoriteDishIds();
    }

    private void validateDish(DishReq req) {
        validate(req);
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
    }

    private DishResp dishResponse(Dish dish) {
        return DishResp.builder().dishId(dish.getId()).categoryId(dish.getCategoryId()).name(dish.getName())
                .imageUrl(dish.getImageUrl()).virtualPrice(dish.getVirtualPrice().setScale(2).toPlainString())
                .calories(dish.getCalories()).tags(dish.getTags()).allergens(dish.getAllergens())
                .allergenStatus(dish.getAllergenStatus()).spiceLevel(dish.getSpiceLevel()).status(dish.getStatus()).build();
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
}
