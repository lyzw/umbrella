package cn.studykid.growthplanet.controller.console;

import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.response.AdminAuditLogResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.service.AdminAuditService;
import cn.studykid.growthplanet.service.AdminAuditService.AuditFilter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * M6 操作日志与审计接口（只读 + 受控导出）。
 * 全部需 admin token；权限在 Service 层经 AdminUserContext.requirePerm 校验
 * （操作日志/C 端关键操作 查看=全部角色；导出=SA,RA）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAuditController {

    private final AdminAuditService service;

    public AdminAuditController(AdminAuditService service) {
        this.service = service;
    }

    // ==================== 操作日志 ====================

    @GetMapping("/audit-logs")
    public Result<PageResp<AdminAuditLogResp>> auditLogs(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuditFilter filter = new AuditFilter(actorUserId, action, targetType, result, begin, end);
        return Result.ok(service.listAuditLogs(filter, page, pageSize));
    }

    @GetMapping("/audit-logs/{id}")
    public Result<AdminAuditLogResp> auditLogDetail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    /** 审计日志 CSV 导出（上限 1 万行；导出行为本身落审计）。 */
    @GetMapping("/audit-logs/export")
    public ResponseEntity<byte[]> exportAuditLogs(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        AuditFilter filter = new AuditFilter(actorUserId, action, targetType, result, begin, end);
        String csv = service.exportCsv(filter);
        // UTF-8 BOM：保证 Excel 直接打开中文不乱码
        byte[] body = new StringBuilder().append('\uFEFF').append(csv).toString()
                .getBytes(StandardCharsets.UTF_8);
        String filename = "audit-logs-"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    // ==================== C 端关键操作 ====================

    @GetMapping("/c-audit-logs")
    public Result<PageResp<AdminAuditLogResp>> cAuditLogs(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuditFilter filter = new AuditFilter(actorUserId, action, targetType, result, begin, end);
        return Result.ok(service.listCAuditLogs(filter, page, pageSize));
    }
}
