package cn.studykid.growthplanet.controller.console;

import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.AdminConfirmReviewReq;
import cn.studykid.growthplanet.dto.request.AdminMedalReissueReq;
import cn.studykid.growthplanet.dto.response.AdminAllowanceLogResp;
import cn.studykid.growthplanet.dto.response.AdminApprovalRowResp;
import cn.studykid.growthplanet.dto.response.AdminChorePageResp;
import cn.studykid.growthplanet.dto.response.AdminCheckRecordResp;
import cn.studykid.growthplanet.dto.response.AdminConfirmDetailResp;
import cn.studykid.growthplanet.dto.response.AdminConfirmRowResp;
import cn.studykid.growthplanet.dto.response.AdminMedalAwardResp;
import cn.studykid.growthplanet.dto.response.AdminWantEatResp;
import cn.studykid.growthplanet.dto.response.AdminWalletResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.service.AdminBizDataService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * M4 业务数据管理接口（想吃/确认单/审批记录/钱包流水/家务健康/勋章发放 + CSV 导出）。
 * 全部需 admin token；权限在 Service 层经 AdminUserContext.requirePerm 校验（详设 §3.4）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminBizDataController {

    private final AdminBizDataService service;

    public AdminBizDataController(AdminBizDataService service) {
        this.service = service;
    }

    // ==================== 每日想吃 ====================

    @GetMapping("/want-eat")
    public Result<PageResp<AdminWantEatResp>> wantEat(
            @RequestParam(required = false) Long familyId,
            @RequestParam(required = false) Long childId,
            @RequestParam(required = false) String mealType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listWantEat(familyId, childId, mealType, from, to, page, pageSize));
    }

    // ==================== 确认单 ====================

    @GetMapping("/confirmations")
    public Result<PageResp<AdminConfirmRowResp>> confirmations(
            @RequestParam(required = false) Long familyId,
            @RequestParam(required = false) Long childId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean overLimit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listConfirmations(familyId, childId, status, overLimit, from, to,
                page, pageSize));
    }

    @GetMapping("/confirmations/{id}")
    public Result<AdminConfirmDetailResp> confirmationDetail(@PathVariable Long id) {
        return Result.ok(service.getConfirmation(id));
    }

    /** 超额确认单人工复核（L4，前端二次确认；结论落审计，不改业务状态）。 */
    @PostMapping("/confirmations/{id}/review")
    public Result<AdminConfirmDetailResp> reviewConfirmation(@PathVariable Long id,
            @Valid @RequestBody AdminConfirmReviewReq req) {
        return Result.ok(service.reviewConfirmation(id, req));
    }

    // ==================== 审批记录 ====================

    @GetMapping("/confirm-approvals")
    public Result<PageResp<AdminApprovalRowResp>> confirmApprovals(
            @RequestParam(required = false) Long confirmId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listConfirmApprovals(confirmId, action, page, pageSize));
    }

    // ==================== 钱包与流水 ====================

    @GetMapping("/wallets")
    public Result<PageResp<AdminWalletResp>> wallets(
            @RequestParam(required = false) Long familyId,
            @RequestParam(required = false) Long childId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listWallets(familyId, childId, page, pageSize));
    }

    @GetMapping("/allowance-logs")
    public Result<PageResp<AdminAllowanceLogResp>> allowanceLogs(
            @RequestParam(required = false) Long familyId,
            @RequestParam(required = false) Long childId,
            @RequestParam(required = false) String transType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listAllowanceLogs(familyId, childId, transType, from, to, page, pageSize));
    }

    // ==================== 家务健康 ====================

    @GetMapping("/chore-instances")
    public Result<AdminChorePageResp> choreInstances(
            @RequestParam(required = false) Long familyId,
            @RequestParam(required = false) Long childId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listChoreInstances(familyId, childId, status, from, to, page, pageSize));
    }

    @GetMapping("/check-records")
    public Result<PageResp<AdminCheckRecordResp>> checkRecords(
            @RequestParam(required = false) Long familyId,
            @RequestParam(required = false) Long childId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listCheckRecords(familyId, childId, itemId, from, to, page, pageSize));
    }

    // ==================== 勋章发放 ====================

    @GetMapping("/medal-awards")
    public Result<PageResp<AdminMedalAwardResp>> medalAwards(
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) Long familyId,
            @RequestParam(required = false) Long childId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listMedalAwards(definitionId, familyId, childId, page, pageSize));
    }

    /** 勋章人工补发（L4，前端二次确认；幂等键 ref_id 缺省 0 = 人工通道）。 */
    @PostMapping("/medal-awards")
    public Result<AdminMedalAwardResp> reissueMedal(@Valid @RequestBody AdminMedalReissueReq req) {
        return Result.ok(service.reissueMedal(req));
    }

    // ==================== CSV 导出 ====================

    /**
     * 业务数据 CSV 导出。domain ∈ want-eat | confirmations | approvals | wallets |
     * allowance-logs | chore-instances | check-records | medal-awards（上限 1 万行，默认脱敏）。
     */
    @PostMapping("/export/{domain}")
    public ResponseEntity<byte[]> export(@PathVariable String domain,
            @RequestBody(required = false) ExportFilter filter) {
        ExportFilter f = filter == null ? new ExportFilter() : filter;
        String csv = service.exportCsv(domain, f.familyId, f.childId, f.status, f.mealType, f.from, f.to);
        // UTF-8 BOM：保证 Excel 直接打开中文不乱码
        byte[] body = new StringBuilder().append('\uFEFF').append(csv).toString()
                .getBytes(StandardCharsets.UTF_8);
        String filename = "biz-" + domain + "-"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    /** 导出过滤参数（全可选，按域取用）。 */
    public static class ExportFilter {
        public Long familyId;
        public Long childId;
        public String status;
        public String mealType;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        public LocalDate from;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        public LocalDate to;
    }
}
