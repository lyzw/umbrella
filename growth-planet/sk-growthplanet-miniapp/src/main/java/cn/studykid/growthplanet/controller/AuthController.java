package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.ChildProfileReq;
import cn.studykid.growthplanet.dto.request.ChildPreferencesReq;
import cn.studykid.growthplanet.dto.response.ChildPreferencesResp;
import cn.studykid.growthplanet.dto.response.ChildProfileDetailResp;
import cn.studykid.growthplanet.dto.request.SelectRoleReq;
import cn.studykid.growthplanet.dto.request.WxLoginReq;
import cn.studykid.growthplanet.dto.response.ChildProfileResp;
import cn.studykid.growthplanet.dto.response.SelectRoleResp;
import cn.studykid.growthplanet.dto.response.WxLoginResp;
import cn.studykid.growthplanet.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AUTH 组接口：wx-login / select-role / child-profile。
 */
@RestController
@RequestMapping("/api/mini")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/wx-login")
    public Result<WxLoginResp> wxLogin(@RequestBody @Valid WxLoginReq req) {
        return Result.ok(authService.wxLogin(req));
    }

    @PostMapping("/auth/select-role")
    public Result<SelectRoleResp> selectRole(@RequestBody @Valid SelectRoleReq req) {
        return Result.ok(authService.selectRole(req));
    }

    @PostMapping("/child/profile")
    @RequireRole(RoleEnum.PARENT)
    public Result<ChildProfileResp> childProfile(@RequestBody @Valid ChildProfileReq req) {
        return Result.ok(authService.saveChildProfile(req));
    }

    @GetMapping("/child/profile")
    @RequireRole(RoleEnum.PARENT)
    public Result<ChildProfileDetailResp> getChildProfile(@RequestParam @Positive Long childId) {
        return Result.ok(authService.getChildProfile(childId));
    }

    @GetMapping("/child/preferences")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<ChildPreferencesResp> getChildPreferences(@RequestParam @Positive Long childId) {
        return Result.ok(authService.getChildPreferences(childId));
    }

    @PutMapping("/child/preferences")
    @RequireRole(RoleEnum.CHILD)
    public Result<ChildPreferencesResp> saveChildPreferences(@RequestBody @Valid ChildPreferencesReq req) {
        return Result.ok(authService.saveChildPreferences(req));
    }

    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }
}
