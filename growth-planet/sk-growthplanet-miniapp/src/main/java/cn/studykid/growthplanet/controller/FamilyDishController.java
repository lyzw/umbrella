package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.FamilyDishReq;
import cn.studykid.growthplanet.dto.response.FamilyDishResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.service.FamilyDishService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

/**
 * 家长家庭私有菜品接口：录入/查询/编辑/上下架/删除，跨家庭 100% 隔离。
 * expectedVersion 为乐观锁版本号，编辑/上下架/删除必传。
 */
@RestController
@RequestMapping("/api/mini/parent/family-dish")
public class FamilyDishController {
    private final FamilyDishService service;

    public FamilyDishController(FamilyDishService service) {
        this.service = service;
    }

    @PostMapping
    @RequireRole(RoleEnum.PARENT)
    public Result<FamilyDishResp> create(@RequestBody @Valid FamilyDishReq req) {
        return Result.ok(service.create(req));
    }

    @GetMapping
    @RequireRole(RoleEnum.PARENT)
    public Result<PageResp<FamilyDishResp>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status, @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(page, pageSize, categoryId, status, keyword));
    }

    @GetMapping("/{id}")
    @RequireRole(RoleEnum.PARENT)
    public Result<FamilyDishResp> detail(@PathVariable @Positive Long id) {
        return Result.ok(service.detail(id));
    }

    @PutMapping("/{id}")
    @RequireRole(RoleEnum.PARENT)
    public Result<FamilyDishResp> update(@PathVariable @Positive Long id, @RequestBody @Valid FamilyDishReq req,
            @RequestParam Integer expectedVersion) {
        return Result.ok(service.update(id, req, expectedVersion));
    }

    @PostMapping("/{id}/status")
    @RequireRole(RoleEnum.PARENT)
    public Result<FamilyDishResp> changeStatus(@PathVariable @Positive Long id, @RequestParam String targetStatus,
            @RequestParam Integer expectedVersion) {
        return Result.ok(service.changeStatus(id, targetStatus, expectedVersion));
    }

    /** 删除前查询引用该菜品的菜单数（供前端二次确认）。 */
    @GetMapping("/{id}/references")
    @RequireRole(RoleEnum.PARENT)
    public Result<ReferenceCountResp> references(@PathVariable @Positive Long id) {
        return Result.ok(new ReferenceCountResp(service.countMenuReferences(id)));
    }

    @DeleteMapping("/{id}")
    @RequireRole(RoleEnum.PARENT)
    public Result<Void> delete(@PathVariable @Positive Long id, @RequestParam Integer expectedVersion) {
        service.delete(id, expectedVersion);
        return Result.ok();
    }

    /** 删除前引用数响应。 */
    public record ReferenceCountResp(int menuCount) {
    }
}
