package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.*;
import cn.studykid.growthplanet.dto.response.*;
import cn.studykid.growthplanet.service.ConfirmService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ConfirmController {
    private final ConfirmService service;

    public ConfirmController(ConfirmService service) {
        this.service = service;
    }

    @PostMapping("/menu/confirm")
    @RequireRole(RoleEnum.CHILD)
    public Result<ConfirmResp> submit(@RequestBody @Valid ConfirmSubmitReq req,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(service.submit(req, key));
    }

    @GetMapping("/menu/confirm/{id}")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<ConfirmResp> detail(@PathVariable @Positive Long id) {
        return Result.ok(service.detail(id));
    }

    @GetMapping("/menu/confirm/status")
    @RequireRole(RoleEnum.CHILD)
    public Result<ConfirmStatusResp> status(@RequestParam @Positive Long confirmId) {
        return Result.ok(service.status(confirmId));
    }

    @GetMapping("/menu/confirms")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<PageResp<ConfirmResp>> list(@RequestParam @Positive Long childId,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.list(childId, status, page, pageSize));
    }

    @GetMapping("/parent/approvals")
    @RequireRole(RoleEnum.PARENT)
    public Result<PageResp<ConfirmResp>> approvals(@RequestParam @Positive Long childId,
            @RequestParam(defaultValue = "PENDING") String status, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.list(childId, status, page, pageSize));
    }

    @PostMapping("/menu/confirm/{id}/withdraw")
    @RequireRole(RoleEnum.CHILD)
    public Result<ConfirmResp> withdraw(@PathVariable @Positive Long id, @RequestBody @Valid ConfirmVersionReq req) {
        return Result.ok(service.withdraw(id, req));
    }

    @PostMapping("/parent/approve/{id}/approve")
    @RequireRole(RoleEnum.PARENT)
    public Result<ConfirmResp> approve(@PathVariable @Positive Long id, @RequestBody @Valid ConfirmApproveReq req) {
        return Result.ok(service.approve(id, req));
    }

    @PostMapping("/parent/approve/{id}/reject")
    @RequireRole(RoleEnum.PARENT)
    public Result<ConfirmResp> reject(@PathVariable @Positive Long id, @RequestBody @Valid ConfirmRejectReq req) {
        return Result.ok(service.reject(id, req));
    }

    @PostMapping("/parent/approve/{id}/modify")
    @RequireRole(RoleEnum.PARENT)
    public Result<ConfirmResp> modify(@PathVariable @Positive Long id, @RequestBody @Valid ConfirmModifyReq req) {
        return Result.ok(service.modify(id, req));
    }
}
