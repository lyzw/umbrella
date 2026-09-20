package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.*;
import cn.studykid.growthplanet.dto.response.*;
import cn.studykid.growthplanet.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class MenuController {
    private final CatalogService catalog;

    public MenuController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @PostMapping("/admin/dish-category")
    @RequireRole(RoleEnum.ADMIN)
    public Result<DishCategoryResp> createCategory(@RequestBody @Valid DishCategoryReq req) {
        return Result.ok(catalog.createCategory(req));
    }

    @GetMapping("/admin/dish-category")
    @RequireRole(RoleEnum.ADMIN)
    public Result<PageResp<DishCategoryResp>> categories(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(catalog.listCategories(page, pageSize));
    }

    @PostMapping("/admin/dish")
    @RequireRole(RoleEnum.ADMIN)
    public Result<DishResp> createDish(@RequestBody @Valid DishReq req) {
        return Result.ok(catalog.createDish(req));
    }

    @PutMapping("/admin/dish/{id}")
    @RequireRole(RoleEnum.ADMIN)
    public Result<DishResp> updateDish(@PathVariable @Positive Long id, @RequestBody @Valid DishReq req) {
        return Result.ok(catalog.updateDish(id, req));
    }

    @DeleteMapping("/admin/dish/{id}")
    @RequireRole(RoleEnum.ADMIN)
    public Result<Void> deleteDish(@PathVariable @Positive Long id) {
        catalog.deleteDish(id);
        return Result.ok();
    }

    @GetMapping("/admin/dish/{id}")
    @RequireRole(RoleEnum.ADMIN)
    public Result<DishResp> dish(@PathVariable @Positive Long id) {
        return Result.ok(catalog.getDish(id));
    }

    @GetMapping("/admin/dish")
    @RequireRole(RoleEnum.ADMIN)
    public Result<PageResp<DishResp>> dishes(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status, @RequestParam(required = false) String keyword) {
        return Result.ok(catalog.listDishes(page, pageSize, categoryId, status, keyword));
    }

    @PostMapping("/admin/menu-daily")
    @RequireRole(RoleEnum.ADMIN)
    public Result<MenuUpsertResp> schoolMenu(@RequestBody @Valid MenuDailyReq req) {
        return Result.ok(catalog.upsertSchoolMenu(req));
    }

    @PostMapping("/parent/menu-daily")
    @RequireRole(RoleEnum.PARENT)
    public Result<MenuUpsertResp> familyMenu(@RequestBody @Valid MenuDailyReq req) {
        return Result.ok(catalog.upsertFamilyMenu(req));
    }

    @GetMapping("/parent/dish")
    @RequireRole(RoleEnum.PARENT)
    public Result<PageResp<DishResp>> parentDishes(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(catalog.listParentDishes(page, pageSize, categoryId, keyword));
    }

    @GetMapping("/parent/menu-daily")
    @RequireRole(RoleEnum.PARENT)
    public Result<MenuMaintenanceResp> familyMenu(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate menuDate,
            @RequestParam String mealType) {
        return Result.ok(catalog.getFamilyMenu(menuDate, mealType));
    }

    @GetMapping("/menu/daily")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<MenuDailyResp> daily(@RequestParam String sourceType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate menuDate,
            @RequestParam String mealType, @RequestParam(required = false) @Positive Long childId) {
        return Result.ok(catalog.daily(sourceType, menuDate, mealType, childId));
    }

    @PostMapping("/menu/mark-favorite")
    @RequireRole(RoleEnum.CHILD)
    public Result<ChildPreferencesResp> favorite(@RequestBody @Valid MarkFavoriteReq req) {
        return Result.ok(catalog.markFavorite(req));
    }
}
