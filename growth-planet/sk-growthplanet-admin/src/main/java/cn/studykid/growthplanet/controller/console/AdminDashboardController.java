package cn.studykid.growthplanet.controller.console;

import cn.studykid.growthplanet.common.constant.AdminResource;
import cn.studykid.growthplanet.common.context.AdminUserContext;
import cn.studykid.growthplanet.common.enums.AdminAction;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.response.AdminDashboardAllowanceResp;
import cn.studykid.growthplanet.dto.response.AdminDashboardChoresResp;
import cn.studykid.growthplanet.dto.response.AdminDashboardMedalsResp;
import cn.studykid.growthplanet.dto.response.AdminDashboardMealsResp;
import cn.studykid.growthplanet.dto.response.AdminDashboardOverviewResp;
import cn.studykid.growthplanet.service.AdminDashboardService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营看板与报表（里程碑 A · M1）。
 * 全部接口置于 {@code /api/admin/**} 运营端前缀下。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard/overview")
    public Result<AdminDashboardOverviewResp> overview() {
        AdminUserContext.requirePerm(AdminResource.DASHBOARD, AdminAction.VIEW.code());
        return Result.ok(dashboardService.overview());
    }

    @GetMapping("/dashboard/meals")
    public Result<AdminDashboardMealsResp> meals() {
        AdminUserContext.requirePerm(AdminResource.DASHBOARD, AdminAction.VIEW.code());
        return Result.ok(dashboardService.meals());
    }

    @GetMapping("/dashboard/allowance")
    public Result<AdminDashboardAllowanceResp> allowance() {
        AdminUserContext.requirePerm(AdminResource.DASHBOARD, AdminAction.VIEW.code());
        return Result.ok(dashboardService.allowance());
    }

    @GetMapping("/dashboard/chores")
    public Result<AdminDashboardChoresResp> chores() {
        AdminUserContext.requirePerm(AdminResource.DASHBOARD, AdminAction.VIEW.code());
        return Result.ok(dashboardService.chores());
    }

    @GetMapping("/dashboard/medals")
    public Result<AdminDashboardMedalsResp> medals() {
        AdminUserContext.requirePerm(AdminResource.DASHBOARD, AdminAction.VIEW.code());
        return Result.ok(dashboardService.medals());
    }

    @PostMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(@RequestParam String domain) {
        AdminUserContext.requirePerm(AdminResource.REPORT_EXPORT, AdminAction.EXPORT.code());
        String csv = dashboardService.exportCsv(domain);
        String filename = "report-" + domain + "-" + System.currentTimeMillis() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
