package com.growthplanet.controller;

import com.growthplanet.common.annotation.RequireRole;
import com.growthplanet.common.context.UserContext;
import com.growthplanet.common.enums.RoleEnum;
import com.growthplanet.common.result.Result;
import com.growthplanet.dto.request.ChildProfileReq;
import com.growthplanet.dto.request.SelectRoleReq;
import com.growthplanet.dto.request.WxLoginReq;
import com.growthplanet.dto.response.ChildProfileResp;
import com.growthplanet.dto.response.SelectRoleResp;
import com.growthplanet.dto.response.WxLoginResp;
import com.growthplanet.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AUTH 组接口：wx-login / select-role / child-profile。
 */
@RestController
@RequestMapping("/api")
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

    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }
}
