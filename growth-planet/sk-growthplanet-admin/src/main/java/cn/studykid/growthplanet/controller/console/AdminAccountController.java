package cn.studykid.growthplanet.controller.console;

import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.AdminAccountUpsertReq;
import cn.studykid.growthplanet.dto.request.AdminPasswordResetReq;
import cn.studykid.growthplanet.dto.response.AdminAccountResp;
import cn.studykid.growthplanet.dto.response.AdminPermissionRow;
import cn.studykid.growthplanet.dto.response.AdminRoleResp;
import cn.studykid.growthplanet.dto.response.AdminWorkbenchResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.service.AdminAccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台运营端账号/角色权限接口 + 工作台聚合（M0 + M2）。
 * 全部需 admin token；细粒度权限在 Service 层以 {@code AdminUserContext.requirePerm} 校验。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAccountController {

    private final AdminAccountService service;

    public AdminAccountController(AdminAccountService service) {
        this.service = service;
    }

    // ==================== M0：工作台 ====================

    @GetMapping("/workbench")
    public Result<AdminWorkbenchResp> workbench() {
        return Result.ok(service.workbench());
    }

    // ==================== M2：账号管理 ====================

    @GetMapping("/accounts")
    public Result<PageResp<AdminAccountResp>> listAccounts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listAccounts(keyword, page, pageSize));
    }

    @PostMapping("/accounts")
    public Result<AdminAccountResp> createAccount(@RequestBody @Valid AdminAccountUpsertReq req) {
        return Result.ok(service.createAccount(req));
    }

    @PutMapping("/accounts/{id}")
    public Result<AdminAccountResp> updateAccount(@PathVariable Long id,
                                                  @RequestBody @Valid AdminAccountUpsertReq req) {
        return Result.ok(service.updateAccount(id, req));
    }

    @PutMapping("/accounts/{id}/status")
    public Result<AdminAccountResp> changeStatus(@PathVariable Long id,
                                                 @RequestParam String status) {
        return Result.ok(service.changeStatus(id, status));
    }

    @PostMapping("/accounts/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @RequestBody @Valid AdminPasswordResetReq req) {
        service.resetPassword(id, req.getNewPassword());
        return Result.ok();
    }

    // ==================== M2：角色与权限矩阵 ====================

    @GetMapping("/roles")
    public Result<List<AdminRoleResp>> listRoles() {
        return Result.ok(service.listRoles());
    }

    @GetMapping("/roles/permissions")
    public Result<List<AdminPermissionRow>> permissionMatrix(@RequestParam String roleCode) {
        return Result.ok(service.permissionMatrix(roleCode));
    }
}
