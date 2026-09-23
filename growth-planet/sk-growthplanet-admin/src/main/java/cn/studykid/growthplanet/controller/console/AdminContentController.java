package cn.studykid.growthplanet.controller.console;

import cn.studykid.growthplanet.common.result.Result;
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
import cn.studykid.growthplanet.dto.response.DishReferenceResp;
import cn.studykid.growthplanet.dto.response.DishResp;
import cn.studykid.growthplanet.dto.response.MenuUpsertResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.service.AdminContentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * M3 内容管理接口（菜品/分类/校餐菜单/任务库/勋章/心愿配置/UGC 审核）。
 * 全部需 admin token；权限在 Service 层经 AdminUserContext.requirePerm 校验（详设 §3.4）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminContentController {

    private final AdminContentService service;

    public AdminContentController(AdminContentService service) {
        this.service = service;
    }

    // ==================== 菜品库 ====================

    @GetMapping("/dishes")
    public Result<PageResp<DishResp>> dishes(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String allergenStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listDishes(page, pageSize, categoryId, status, keyword, allergenStatus));
    }

    @GetMapping("/dishes/{id}")
    public Result<DishResp> dishDetail(@PathVariable Long id) {
        return Result.ok(service.getDish(id));
    }

    /** 菜品表单参考字典（过敏原发布目录）：候选值随环境配置变化，前端不得硬编码。 */
    @GetMapping("/dish-references")
    public Result<DishReferenceResp> dishReferences() {
        return Result.ok(service.getDishReferences());
    }

    @PostMapping("/dishes")
    public Result<DishResp> createDish(@Valid @RequestBody DishReq req) {
        return Result.ok(service.createDish(req));
    }

    @PutMapping("/dishes/{id}")
    public Result<DishResp> updateDish(@PathVariable Long id, @Valid @RequestBody DishReq req) {
        return Result.ok(service.updateDish(id, req));
    }

    @DeleteMapping("/dishes/{id}")
    public Result<Void> deleteDish(@PathVariable Long id) {
        service.deleteDish(id);
        return Result.ok(null);
    }

    /** 上架/下架：body 传 {"status":"ON_SALE"|"OFF_SALE"}（手工绑定，简单类型不走校验器）。 */
    @PutMapping("/dishes/{id}/status")
    public Result<DishResp> toggleDishStatus(@PathVariable Long id,
            @RequestBody DishStatusReq req) {
        return Result.ok(service.toggleDishStatus(id, req.status));
    }

    /** 上下架请求体（仅 status 一个字段）。 */
    public record DishStatusReq(String status) {
    }

    // ==================== 菜品分类 ====================

    @GetMapping("/dish-categories")
    public Result<PageResp<DishCategoryResp>> dishCategories(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return Result.ok(service.listCategories(page, pageSize));
    }

    @PostMapping("/dish-categories")
    public Result<DishCategoryResp> createDishCategory(@Valid @RequestBody DishCategoryReq req) {
        return Result.ok(service.createCategory(req));
    }

    @PutMapping("/dish-categories/{id}")
    public Result<DishCategoryResp> updateDishCategory(@PathVariable Long id,
            @RequestBody DishCategoryReq req) {
        return Result.ok(service.updateCategory(id, req));
    }

    @DeleteMapping("/dish-categories/{id}")
    public Result<Void> deleteDishCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return Result.ok(null);
    }

    // ==================== 校餐菜单（SCHOOL） ====================

    @GetMapping("/menus/school")
    public Result<PageResp<AdminMenuRowResp>> schoolMenus(
            @RequestParam(required = false) String school,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String mealType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listSchoolMenus(school, from, to, mealType, page, pageSize));
    }

    @PostMapping("/menus/school")
    public Result<MenuUpsertResp> upsertSchoolMenu(@Valid @RequestBody MenuDailyReq req) {
        return Result.ok(service.upsertSchoolMenu(req));
    }

    // ==================== 任务库 ====================

    @GetMapping("/chore-tasks")
    public Result<PageResp<AdminChoreTaskResp>> choreTasks(
            @RequestParam(required = false) Long familyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listChoreTasks(familyId, status, keyword, page, pageSize));
    }

    @PostMapping("/chore-tasks")
    public Result<AdminChoreTaskResp> createChoreTask(@Valid @RequestBody AdminChoreTaskCreateReq req) {
        return Result.ok(service.createChoreTask(req));
    }

    @PutMapping("/chore-tasks/{id}")
    public Result<AdminChoreTaskResp> updateChoreTask(@PathVariable Long id,
            @RequestBody AdminChoreTaskUpdateReq req) {
        return Result.ok(service.updateChoreTask(id, req));
    }

    // ==================== 勋章配置 ====================

    @GetMapping("/medals")
    public Result<List<AdminMedalResp>> medals(@RequestParam(required = false) String status) {
        return Result.ok(service.listMedals(status));
    }

    @PostMapping("/medals")
    public Result<AdminMedalResp> createMedal(@Valid @RequestBody AdminMedalReq req) {
        return Result.ok(service.createMedal(req));
    }

    @PutMapping("/medals/{id}")
    public Result<AdminMedalResp> updateMedal(@PathVariable Long id, @Valid @RequestBody AdminMedalReq req) {
        return Result.ok(service.updateMedal(id, req));
    }

    // ==================== 心愿菜单家庭配置 ====================

    @GetMapping("/wish-menu-config/{familyId}")
    public Result<AdminWishConfigResp> wishConfig(@PathVariable Long familyId) {
        return Result.ok(service.getWishConfig(familyId));
    }

    @PutMapping("/wish-menu-config/{familyId}")
    public Result<AdminWishConfigResp> updateWishConfig(@PathVariable Long familyId,
            @Valid @RequestBody AdminWishConfigReq req) {
        return Result.ok(service.updateWishConfig(familyId, req));
    }

    // ==================== UGC 审核队列 ====================

    @GetMapping("/ugc/queue")
    public Result<PageResp<AdminUgcDishResp>> ugcQueue(
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.ugcQueue(reviewStatus, keyword, page, pageSize));
    }

    @PostMapping("/ugc/{id}/review")
    public Result<AdminUgcDishResp> reviewUgc(@PathVariable Long id, @Valid @RequestBody AdminUgcReviewReq req) {
        return Result.ok(service.reviewUgc(id, req));
    }

    @DeleteMapping("/ugc/{id}")
    public Result<Void> deleteUgc(@PathVariable Long id) {
        service.deleteUgc(id);
        return Result.ok(null);
    }
}
