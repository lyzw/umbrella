package cn.studykid.growthplanet.controller.console;

import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.AdminComplianceCheckReq;
import cn.studykid.growthplanet.dto.request.AdminPrivacyRejectReq;
import cn.studykid.growthplanet.dto.request.AdminPrivacyVerifyReq;
import cn.studykid.growthplanet.dto.response.AdminComplianceItemResp;
import cn.studykid.growthplanet.dto.response.AdminConsentResp;
import cn.studykid.growthplanet.dto.response.AdminPrivacyReqResp;
import cn.studykid.growthplanet.dto.response.AdminVerificationResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.service.AdminPrivacyService;
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
 * M5 合规与隐私中心接口（同意留痕/隐私工单/核验记录/合规清单）。
 * 全部需 admin token；权限在 Service 层经 AdminUserContext.requirePerm 校验（详设 §3.4）。
 * 隐私域（同意留痕/隐私工单/核验记录）仅 SA/CP/RA 可见；合规清单 view=SA/CP/RA，config=SA/CP。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminPrivacyController {

    private final AdminPrivacyService service;

    public AdminPrivacyController(AdminPrivacyService service) {
        this.service = service;
    }

    // ==================== 同意留痕 ====================

    @GetMapping("/consents")
    public Result<PageResp<AdminConsentResp>> consents(
            @RequestParam(required = false) Long childId,
            @RequestParam(required = false) String consentType,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listConsents(childId, consentType, action, page, pageSize));
    }

    // ==================== 隐私工单 ====================

    @GetMapping("/privacy-requests")
    public Result<PageResp<AdminPrivacyReqResp>> privacyRequests(
            @RequestParam(required = false) Long childId,
            @RequestParam(required = false) String requestType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listPrivacyRequests(childId, requestType, status, page, pageSize));
    }

    /** CP 核验闭环：RECEIVED → PROCESSING，写入核验哈希。 */
    @PostMapping("/privacy-requests/{id}/verify")
    public Result<AdminPrivacyReqResp> verify(
            @PathVariable Long id, @Valid @RequestBody AdminPrivacyVerifyReq req) {
        return Result.ok(service.verify(id, req));
    }

    /** CP 驳回：RECEIVED/PROCESSING → REJECTED，原因落 error_code + 审计。 */
    @PostMapping("/privacy-requests/{id}/reject")
    public Result<AdminPrivacyReqResp> reject(
            @PathVariable Long id, @Valid @RequestBody AdminPrivacyRejectReq req) {
        return Result.ok(service.reject(id, req));
    }

    @GetMapping("/privacy-verifications")
    public Result<PageResp<AdminVerificationResp>> verifications(
            @RequestParam(required = false) Long requestId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.listVerifications(requestId, page, pageSize));
    }

    // ==================== 合规清单 ====================

    @GetMapping("/compliance-checklist")
    public Result<List<AdminComplianceItemResp>> compliance() {
        return Result.ok(service.listCompliance());
    }

    @PutMapping("/compliance-checklist")
    public Result<AdminComplianceItemResp> checkCompliance(
            @Valid @RequestBody AdminComplianceCheckReq req) {
        return Result.ok(service.checkCompliance(req));
    }
}
