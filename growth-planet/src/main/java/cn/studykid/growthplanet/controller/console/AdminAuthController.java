package cn.studykid.growthplanet.controller.console;

import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.AdminLoginReq;
import cn.studykid.growthplanet.dto.response.AdminLoginResp;
import cn.studykid.growthplanet.dto.response.AdminMeResp;
import cn.studykid.growthplanet.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台运营端认证接口（前缀 {@code /api/console}，与 C 端 {@code /api/auth} 完全隔离）。
 * <ul>
 *   <li>{@code POST /api/console/auth/login} —— 放行（无需 token）；</li>
 *   <li>{@code POST /api/console/auth/logout} / {@code GET /api/console/auth/me} —— 需 admin token。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/console/auth")
public class AdminAuthController {

    private final AdminAuthService authService;

    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<AdminLoginResp> login(@RequestBody @Valid AdminLoginReq req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<AdminMeResp> me() {
        return Result.ok(authService.me());
    }
}
