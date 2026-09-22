package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.constant.AdminResource;
import cn.studykid.growthplanet.common.context.AdminUserContext;
import cn.studykid.growthplanet.common.enums.AdminAction;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.AdminChoreTaskCreateReq;
import cn.studykid.growthplanet.dto.request.AdminChoreTaskUpdateReq;
import cn.studykid.growthplanet.dto.request.AdminMedalReq;
import cn.studykid.growthplanet.dto.request.AdminUgcReviewReq;
import cn.studykid.growthplanet.dto.request.AdminWishConfigReq;
import cn.studykid.growthplanet.dto.request.DishCategoryReq;
import cn.studykid.growthplanet.dto.request.DishReq;
import cn.studykid.growthplanet.dto.request.MenuDailyReq;
import cn.studykid.growthplanet.dto.response.AdminChoreTaskResp;
import cn.studykid.growthplanet.dto.response.AdminMedalResp;
import cn.studykid.growthplanet.dto.response.AdminMenuRowResp;
import cn.studykid.growthplanet.dto.response.AdminUgcDishResp;
import cn.studykid.growthplanet.dto.response.AdminWishConfigResp;
import cn.studykid.growthplanet.dto.response.DishCategoryResp;
import cn.studykid.growthplanet.dto.response.DishResp;
import cn.studykid.growthplanet.dto.response.MenuUpsertResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.entity.ChoreTask;
import cn.studykid.growthplanet.entity.Dish;
import cn.studykid.growthplanet.entity.DishCategory;
import cn.studykid.growthplanet.entity.FamilyDish;
import cn.studykid.growthplanet.entity.FamilySetting;
import cn.studykid.growthplanet.entity.MedalAward;
import cn.studykid.growthplanet.entity.MedalDefinition;
import cn.studykid.growthplanet.entity.MenuDaily;
import cn.studykid.growthplanet.mapper.ChoreTaskMapper;
import cn.studykid.growthplanet.mapper.DishCategoryMapper;
import cn.studykid.growthplanet.mapper.DishMapper;
import cn.studykid.growthplanet.mapper.FamilyDishMapper;
import cn.studykid.growthplanet.mapper.FamilyMapper;
import cn.studykid.growthplanet.mapper.FamilySettingMapper;
import cn.studykid.growthplanet.mapper.MedalAwardMapper;
import cn.studykid.growthplanet.mapper.MedalDefinitionMapper;
import cn.studykid.growthplanet.mapper.MenuDailyMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 运营端内容管理（里程碑 A 批次 2 / M3）。
 * <p>
 * 职责边界：
 * <ul>
 *   <li>菜品/分类/校餐菜单：复用 {@link CatalogService} 的 console 变体（校验与审计同源，仅鉴权换轨）；</li>
 *   <li>任务库（跨家庭治理视图 + 代客创建）、勋章配置、心愿菜单家庭配置、UGC 审核队列：本类直接落库；</li>
 *   <li>所有写操作经 {@code AdminUserContext.requirePerm} 细粒度权限点守卫，并落 sys_audit_log
 *       （actor = 运营账号 id，与 C 端 userId 域隔离，详情列以 "console" 前缀标识）。</li>
 * </ul>
 * 状态约定：任务/勋章 NORMAL|DISABLED；菜单 DRAFT|PUBLISHED；UGC review_status NONE|PENDING|APPROVED|REJECTED。
 */
@Service
@Transactional
public class AdminContentService {

    private static final Set<String> DISH_STATUSES = Set.of("ON_SALE", "OFF_SALE");
    private static final Set<String> CATEGORY_STATUSES = Set.of("ENABLED", "DISABLED");
    private static final Set<String> TASK_STATUSES = Set.of("NORMAL", "ARCHIVED");
    private static final Set<String> TASK_CYCLES = Set.of("DAILY", "WEEKLY", "ONCE");
    private static final Set<String> MEDAL_STATUSES = Set.of("NORMAL", "ARCHIVED");
    private static final Set<String> UGC_REVIEW_STATUSES = Set.of("NONE", "PENDING", "APPROVED", "REJECTED");
    private static final Set<String> MEALS = Set.of("BREAKFAST", "LUNCH", "DINNER");

    private final CatalogService catalog;
    private final DishMapper dishes;
    private final DishCategoryMapper categories;
    private final FamilyDishMapper familyDishes;
    private final MenuDailyMapper menus;
    private final ChoreTaskMapper chores;
    private final MedalDefinitionMapper medalDefs;
    private final MedalAwardMapper medalAwards;
    private final FamilySettingMapper familySettings;
    private final FamilyMapper families;
    private final AuditService audit;
    private final Validator validator;

    public AdminContentService(CatalogService catalog, DishMapper dishes, DishCategoryMapper categories,
            FamilyDishMapper familyDishes, MenuDailyMapper menus, ChoreTaskMapper chores,
            MedalDefinitionMapper medalDefs, MedalAwardMapper medalAwards, FamilySettingMapper familySettings,
            FamilyMapper families, AuditService audit, Validator validator) {
        this.catalog = catalog;
        this.dishes = dishes;
        this.categories = categories;
        this.familyDishes = familyDishes;
        this.menus = menus;
        this.chores = chores;
        this.medalDefs = medalDefs;
        this.medalAwards = medalAwards;
        this.familySettings = familySettings;
        this.families = families;
        this.audit = audit;
        this.validator = validator;
    }

    private Long actor() {
        return AdminUserContext.adminId();
    }

    private void record(String action, Long familyId, String targetType, Long targetId, String detail) {
        audit.record(action, actor(), familyId, targetType, targetId, null,
                detail == null ? "console" : "console;" + detail);
    }

    // ==================== 菜品库 ====================

    public PageResp<DishResp> listDishes(int page, int pageSize, Long categoryId, String status,
            String keyword, String allergenStatus) {
        AdminUserContext.requirePerm(AdminResource.DISH, AdminAction.VIEW.code());
        return catalog.listDishesAs(page, pageSize, categoryId, status, keyword, allergenStatus);
    }

    public DishResp getDish(Long dishId) {
        AdminUserContext.requirePerm(AdminResource.DISH, AdminAction.VIEW.code());
        return catalog.getDishAs(dishId);
    }

    public DishResp createDish(DishReq req) {
        AdminUserContext.requirePerm(AdminResource.DISH, AdminAction.CREATE.code());
        return catalog.createDishAs(actor(), req);
    }

    public DishResp updateDish(Long dishId, DishReq req) {
        AdminUserContext.requirePerm(AdminResource.DISH, AdminAction.EDIT.code());
        return catalog.updateDishAs(actor(), dishId, req);
    }

    public void deleteDish(Long dishId) {
        // §3.4：预置菜品删除仅 SA（OP 无 delete 权限点，requirePerm 天然拦截）。
        AdminUserContext.requirePerm(AdminResource.DISH, AdminAction.DELETE.code());
        catalog.deleteDishAs(actor(), dishId);
    }

    /** 上架/下架。上架须已声明过敏原（与 validateDish 的 R5-c 红线一致）。 */
    public DishResp toggleDishStatus(Long dishId, String status) {
        AdminUserContext.requirePerm(AdminResource.DISH, AdminAction.EDIT.code());
        if (!DISH_STATUSES.contains(status)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        Dish dish = dishes.selectOne(new QueryWrapper<Dish>().eq("id", dishId).last("FOR UPDATE"));
        if (dish == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        if ("ON_SALE".equals(status) && !"DECLARED".equals(dish.getAllergenStatus())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "上架菜品必须先声明过敏原");
        }
        if (!status.equals(dish.getStatus())) {
            dishes.update(null, new UpdateWrapper<Dish>().eq("id", dishId).set("status", status)
                    .setSql("update_time = CURRENT_TIMESTAMP"));
            record("DISH_STATUS", null, "DISH", dishId, "status=" + status);
        }
        dish.setStatus(status);
        return catalog.getDishAs(dishId);
    }

    // ==================== 菜品分类 ====================

    public PageResp<DishCategoryResp> listCategories(int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.DISH_CATEGORY, AdminAction.VIEW.code());
        return catalog.listCategoriesAs(page, pageSize);
    }

    public DishCategoryResp createCategory(DishCategoryReq req) {
        AdminUserContext.requirePerm(AdminResource.DISH_CATEGORY, AdminAction.CREATE.code());
        return catalog.createCategoryAs(actor(), req);
    }

    /** 编辑分类（name/sort/status 全可选）。 */
    public DishCategoryResp updateCategory(Long categoryId, DishCategoryReq req) {
        AdminUserContext.requirePerm(AdminResource.DISH_CATEGORY, AdminAction.EDIT.code());
        DishCategory category = categories.selectById(categoryId);
        if (category == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        if (req.getName() != null) {
            if (req.getName().isBlank() || req.getName().length() > 64) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
            }
            category.setName(req.getName().trim());
        }
        if (req.getSort() != null) {
            category.setSort(req.getSort());
        }
        if (req.getStatus() != null) {
            if (!CATEGORY_STATUSES.contains(req.getStatus())) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
            }
            category.setStatus(req.getStatus());
        }
        categories.updateById(category);
        record("CATEGORY_UPDATE", null, "CATEGORY", categoryId, "name=" + category.getName());
        return toCategoryResp(category);
    }

    /** 删除分类：被任何菜品（预置或家庭）引用时拒绝，避免菜单/点单解析断链。 */
    public void deleteCategory(Long categoryId) {
        AdminUserContext.requirePerm(AdminResource.DISH_CATEGORY, AdminAction.DELETE.code());
        if (categories.selectById(categoryId) == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        long used = dishes.selectCount(new QueryWrapper<Dish>().eq("category_id", categoryId))
                + familyDishes.selectCount(new QueryWrapper<FamilyDish>().eq("category_id", categoryId));
        if (used > 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "分类使用中，不能删除");
        }
        categories.deleteById(categoryId);
        record("CATEGORY_DELETE", null, "CATEGORY", categoryId, null);
    }

    private DishCategoryResp toCategoryResp(DishCategory category) {
        return DishCategoryResp.builder().categoryId(category.getId()).name(category.getName())
                .sort(category.getSort()).status(category.getStatus()).build();
    }

    // ==================== 校餐菜单（SCHOOL） ====================

    public PageResp<AdminMenuRowResp> listSchoolMenus(String school, LocalDate from, LocalDate to,
            String mealType, int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.MENU, AdminAction.VIEW.code());
        if (mealType != null && !MEALS.contains(mealType)
                || from != null && to != null && from.isAfter(to)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        QueryWrapper<MenuDaily> query = new QueryWrapper<MenuDaily>().eq("source_type", "SCHOOL")
                .eq(school != null && !school.isBlank(), "owner_key", school == null ? null : school.trim())
                .eq(mealType != null, "meal_type", mealType)
                .ge(from != null, "menu_date", from)
                .le(to != null, "menu_date", to);
        long total = menus.selectCount(query);
        long offset = (long) (page - 1) * pageSize;
        List<AdminMenuRowResp> items = menus.selectList(query.orderByDesc("menu_date")
                        .orderByAsc("meal_type").last("LIMIT " + offset + ", " + pageSize))
                .stream().map(this::menuRow).toList();
        return new PageResp<>(items, total, page, pageSize);
    }

    public MenuUpsertResp upsertSchoolMenu(MenuDailyReq req) {
        AdminUserContext.requirePerm(AdminResource.MENU, AdminAction.CONFIG.code());
        return catalog.upsertSchoolMenuAs(actor(), req);
    }

    private AdminMenuRowResp menuRow(MenuDaily menu) {
        return AdminMenuRowResp.builder().id(menu.getId()).sourceType(menu.getSourceType())
                .ownerKey(menu.getOwnerKey()).school(menu.getSchool()).familyId(menu.getFamilyId())
                .menuDate(menu.getMenuDate()).mealType(menu.getMealType()).status(menu.getStatus())
                .dishIds(menu.getDishIds()).version(menu.getVersion()).build();
    }

    // ==================== 任务库（跨家庭治理） ====================

    public PageResp<AdminChoreTaskResp> listChoreTasks(Long familyId, String status, String keyword,
            int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.TASK_LIB, AdminAction.VIEW.code());
        if (status != null && !TASK_STATUSES.contains(status)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        QueryWrapper<ChoreTask> query = new QueryWrapper<ChoreTask>()
                .eq(familyId != null, "family_id", familyId)
                .eq(status != null, "status", status);
        // 同 ugcQueue：MP 条件参数急切求值，keyword.trim() 必须先判空。
        if (keyword != null && !keyword.isBlank()) {
            query.like("title", keyword.trim());
        }
        long total = chores.selectCount(query);
        long offset = (long) (page - 1) * pageSize;
        List<AdminChoreTaskResp> items = chores.selectList(query.orderByDesc("id")
                        .last("LIMIT " + offset + ", " + pageSize))
                .stream().map(this::taskResp).toList();
        return new PageResp<>(items, total, page, pageSize);
    }

    /** 代家庭创建任务模板：现模型按家庭归属，familyId 必填（全局模板建模归 B 期）。 */
    public AdminChoreTaskResp createChoreTask(AdminChoreTaskCreateReq req) {
        AdminUserContext.requirePerm(AdminResource.TASK_LIB, AdminAction.CREATE.code());
        validate(req);
        if (families.selectById(req.getFamilyId()) == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        ChoreTask task = new ChoreTask();
        task.setFamilyId(req.getFamilyId());
        task.setTitle(req.getTitle().trim());
        task.setDescription(orEmpty(req.getDescription()));
        task.setIcon(orEmpty(req.getIcon()));
        task.setEstimatedMinutes(orZero(req.getEstimatedMinutes()));
        task.setRewardAmount(req.getRewardAmount() == null ? BigDecimal.ZERO : req.getRewardAmount());
        task.setCycle(req.getCycle());
        task.setSortOrder(orZero(req.getSortOrder()));
        task.setStatus("NORMAL");
        chores.insert(task);
        record("CHORE_TASK_CREATE", req.getFamilyId(), "CHORE_TASK", task.getId(),
                "title=" + task.getTitle());
        return taskResp(task);
    }

    /** 编辑任务模板（全可选字段；status 用于启停治理）。 */
    public AdminChoreTaskResp updateChoreTask(Long taskId, AdminChoreTaskUpdateReq req) {
        AdminUserContext.requirePerm(AdminResource.TASK_LIB, AdminAction.EDIT.code());
        ChoreTask task = chores.selectById(taskId);
        if (task == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        if (req.getStatus() != null && !TASK_STATUSES.contains(req.getStatus())
                || req.getCycle() != null && !TASK_CYCLES.contains(req.getCycle())
                || req.getTitle() != null && (req.getTitle().isBlank() || req.getTitle().length() > 64)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        if (req.getTitle() != null) {
            task.setTitle(req.getTitle().trim());
        }
        if (req.getDescription() != null) {
            task.setDescription(req.getDescription());
        }
        if (req.getIcon() != null) {
            task.setIcon(req.getIcon());
        }
        if (req.getEstimatedMinutes() != null) {
            task.setEstimatedMinutes(req.getEstimatedMinutes());
        }
        if (req.getRewardAmount() != null) {
            task.setRewardAmount(req.getRewardAmount());
        }
        if (req.getCycle() != null) {
            task.setCycle(req.getCycle());
        }
        if (req.getSortOrder() != null) {
            task.setSortOrder(req.getSortOrder());
        }
        if (req.getStatus() != null) {
            task.setStatus(req.getStatus());
        }
        chores.updateById(task);
        record("CHORE_TASK_UPDATE", task.getFamilyId(), "CHORE_TASK", taskId,
                req.getStatus() == null ? null : "status=" + req.getStatus());
        return taskResp(task);
    }

    private AdminChoreTaskResp taskResp(ChoreTask task) {
        return AdminChoreTaskResp.builder().id(task.getId()).familyId(task.getFamilyId())
                .title(task.getTitle()).description(task.getDescription()).icon(task.getIcon())
                .estimatedMinutes(task.getEstimatedMinutes()).rewardAmount(task.getRewardAmount())
                .cycle(task.getCycle()).sortOrder(task.getSortOrder()).status(task.getStatus())
                .createTime(task.getCreateTime()).build();
    }

    // ==================== 勋章配置 ====================

    public List<AdminMedalResp> listMedals(String status) {
        AdminUserContext.requirePerm(AdminResource.MEDAL, AdminAction.VIEW.code());
        if (status != null && !MEDAL_STATUSES.contains(status)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        return medalDefs.selectList(new QueryWrapper<MedalDefinition>()
                        .eq(status != null, "status", status).orderByAsc("sort_order", "id"))
                .stream().map(this::medalResp).toList();
    }

    public AdminMedalResp createMedal(AdminMedalReq req) {
        AdminUserContext.requirePerm(AdminResource.MEDAL, AdminAction.CREATE.code());
        validate(req);
        if (medalDefs.selectCount(new QueryWrapper<MedalDefinition>().eq("code", req.getCode())) > 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "勋章 code 已存在");
        }
        MedalDefinition def = new MedalDefinition();
        applyMedal(def, req);
        medalDefs.insert(def);
        record("MEDAL_CREATE", null, "MEDAL", def.getId(), "code=" + def.getCode());
        return medalResp(def);
    }

    public AdminMedalResp updateMedal(Long definitionId, AdminMedalReq req) {
        AdminUserContext.requirePerm(AdminResource.MEDAL, AdminAction.EDIT.code());
        MedalDefinition def = medalDefs.selectById(definitionId);
        if (def == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        validate(req);
        Long clash = medalDefs.selectCount(new QueryWrapper<MedalDefinition>()
                .eq("code", req.getCode()).ne("id", definitionId));
        if (clash > 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "勋章 code 已存在");
        }
        applyMedal(def, req);
        medalDefs.updateById(def);
        record("MEDAL_UPDATE", null, "MEDAL", definitionId, "code=" + def.getCode());
        return medalResp(def);
    }

    private void applyMedal(MedalDefinition def, AdminMedalReq req) {
        def.setCode(req.getCode());
        def.setName(req.getName().trim());
        def.setDescription(orEmpty(req.getDescription()));
        def.setIcon(orEmpty(req.getIcon()));
        def.setCategory(req.getCategory());
        def.setConditionType(req.getConditionType());
        def.setThreshold(req.getThreshold());
        def.setSortOrder(orZero(req.getSortOrder()));
        def.setStatus(req.getStatus());
    }

    private AdminMedalResp medalResp(MedalDefinition def) {
        long awarded = medalAwards.selectCount(
                new QueryWrapper<MedalAward>().eq("definition_id", def.getId()));
        return AdminMedalResp.builder().id(def.getId()).code(def.getCode()).name(def.getName())
                .description(def.getDescription()).icon(def.getIcon()).category(def.getCategory())
                .conditionType(def.getConditionType()).threshold(def.getThreshold())
                .sortOrder(def.getSortOrder()).status(def.getStatus()).awardedCount(awarded).build();
    }

    // ==================== 心愿菜单家庭配置 ====================

    public AdminWishConfigResp getWishConfig(Long familyId) {
        AdminUserContext.requirePerm(AdminResource.WISH_MENU, AdminAction.VIEW.code());
        FamilySetting setting = familySettings.selectOne(
                new QueryWrapper<FamilySetting>().eq("family_id", familyId));
        if (setting == null) {
            // 未落过配置行的家庭按 DDL 默认值回显（enabled=1, max=5），可直接编辑保存。
            return AdminWishConfigResp.builder().familyId(familyId)
                    .wishMenuEnabled(1).wishMenuMaxDishes(5).version(0).build();
        }
        return AdminWishConfigResp.builder().familyId(familyId)
                .wishMenuEnabled(setting.getWishMenuEnabled())
                .wishMenuMaxDishes(setting.getWishMenuMaxDishes())
                .version(setting.getVersion()).build();
    }

    /** 更新心愿菜单家庭配置：行不存在则按默认骨架插入，存在则走手写乐观锁（E007 冲突）。 */
    public AdminWishConfigResp updateWishConfig(Long familyId, AdminWishConfigReq req) {
        AdminUserContext.requirePerm(AdminResource.WISH_MENU, AdminAction.EDIT.code());
        if (families.selectById(familyId) == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        FamilySetting setting = familySettings.selectOne(
                new QueryWrapper<FamilySetting>().eq("family_id", familyId));
        if (setting == null) {
            if (req.getExpectedVersion() != 0) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
            setting = new FamilySetting();
            setting.setFamilyId(familyId);
            setting.setWishMenuEnabled(req.getWishMenuEnabled());
            setting.setWishMenuMaxDishes(req.getWishMenuMaxDishes());
            setting.setVersion(1);
            familySettings.insert(setting);
        } else {
            Integer before = setting.getVersion();
            if (before == null || !before.equals(req.getExpectedVersion())) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
            setting.setWishMenuEnabled(req.getWishMenuEnabled());
            setting.setWishMenuMaxDishes(req.getWishMenuMaxDishes());
            setting.setVersion(before + 1);
            if (familySettings.update(setting, new UpdateWrapper<FamilySetting>()
                    .eq("id", setting.getId()).eq("version", before)) != 1) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
        }
        record("WISH_CONFIG_UPDATE", familyId, "FAMILY_SETTING", setting.getId(),
                "enabled=" + setting.getWishMenuEnabled() + ";max=" + setting.getWishMenuMaxDishes());
        return AdminWishConfigResp.builder().familyId(familyId)
                .wishMenuEnabled(setting.getWishMenuEnabled())
                .wishMenuMaxDishes(setting.getWishMenuMaxDishes())
                .version(setting.getVersion()).build();
    }

    // ==================== UGC 审核队列 ====================

    /** 队列：家庭私有菜品（visibility=PRIVATE），按审核状态/名称筛选。 */
    public PageResp<AdminUgcDishResp> ugcQueue(String reviewStatus, String keyword, int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.UGC_QUEUE, AdminAction.VIEW.code());
        if (reviewStatus != null && !UGC_REVIEW_STATUSES.contains(reviewStatus)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        QueryWrapper<FamilyDish> query = new QueryWrapper<FamilyDish>()
                .eq("visibility", "PRIVATE")
                .eq(reviewStatus != null, "review_status", reviewStatus);
        // 注意：MyBatis-Plus 条件参数为急切求值，keyword.trim() 必须先判空再传入。
        if (keyword != null && !keyword.isBlank()) {
            query.like("name", keyword.trim());
        }
        long total = familyDishes.selectCount(query);
        long offset = (long) (page - 1) * pageSize;
        List<AdminUgcDishResp> items = familyDishes.selectList(query
                        .orderByDesc("create_time").orderByDesc("id")
                        .last("LIMIT " + offset + ", " + pageSize))
                .stream().map(this::ugcResp).toList();
        return new PageResp<>(items, total, page, pageSize);
    }

    /** 审核：通过（visibility→PUBLIC + APPROVED）/驳回（REJECTED + reason，visibility 保持 PRIVATE）。 */
    public AdminUgcDishResp reviewUgc(Long familyDishId, AdminUgcReviewReq req) {
        AdminUserContext.requirePerm(AdminResource.UGC_QUEUE, AdminAction.APPROVE.code());
        FamilyDish dish = familyDishes.selectOne(
                new QueryWrapper<FamilyDish>().eq("id", familyDishId).last("FOR UPDATE"));
        if (dish == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        if (!dish.getVersion().equals(req.getExpectedVersion())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        UpdateWrapper<FamilyDish> update = new UpdateWrapper<FamilyDish>()
                .eq("id", familyDishId).eq("version", req.getExpectedVersion())
                .set("version", req.getExpectedVersion() + 1)
                .setSql("update_time = CURRENT_TIMESTAMP");
        if ("APPROVE".equals(req.getAction())) {
            update.set("visibility", "PUBLIC").set("review_status", "APPROVED").set("reject_reason", null);
        } else {
            if (req.getReason() == null || req.getReason().isBlank()) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "驳回必须填写原因");
            }
            update.set("review_status", "REJECTED").set("reject_reason", req.getReason().trim());
        }
        if (familyDishes.update(null, update) != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        record("APPROVE".equals(req.getAction()) ? "UGC_APPROVE" : "UGC_REJECT",
                dish.getFamilyId(), "FAMILY_DISH", familyDishId,
                "action=" + req.getAction() + (req.getReason() == null ? "" : ";reason=" + req.getReason()));
        return ugcResp(familyDishes.selectById(familyDishId));
    }

    /** 移除违规家庭菜品（软删 + 下架；§3.4 UGC 审核队列 delete 仅 CR/SA）。 */
    public void deleteUgc(Long familyDishId) {
        AdminUserContext.requirePerm(AdminResource.UGC_QUEUE, AdminAction.DELETE.code());
        FamilyDish dish = familyDishes.selectById(familyDishId);
        if (dish == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        familyDishes.update(null, new UpdateWrapper<FamilyDish>().eq("id", familyDishId)
                .set("status", "OFF_SALE").setSql("delete_at = UNIX_TIMESTAMP() * 1000")
                .setSql("update_time = CURRENT_TIMESTAMP"));
        record("UGC_DELETE", dish.getFamilyId(), "FAMILY_DISH", familyDishId, "name=" + dish.getName());
    }

    private AdminUgcDishResp ugcResp(FamilyDish dish) {
        return AdminUgcDishResp.builder().id(dish.getId()).familyId(dish.getFamilyId())
                .categoryId(dish.getCategoryId()).name(dish.getName()).imageUrl(dish.getImageUrl())
                .virtualPrice(dish.getVirtualPrice()).calories(dish.getCalories()).tags(dish.getTags())
                .allergens(dish.getAllergens()).allergenStatus(dish.getAllergenStatus())
                .spiceLevel(dish.getSpiceLevel()).status(dish.getStatus()).visibility(dish.getVisibility())
                .reviewStatus(dish.getReviewStatus()).rejectReason(dish.getRejectReason())
                .version(dish.getVersion()).createTime(dish.getCreateTime()).build();
    }

    // ==================== 通用 ====================

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
