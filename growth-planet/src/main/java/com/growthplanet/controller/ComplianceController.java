package com.growthplanet.controller;

import com.growthplanet.common.annotation.RequireRole;
import com.growthplanet.common.enums.RoleEnum;
import com.growthplanet.common.result.Result;
import com.growthplanet.dto.request.ConsentReq;
import com.growthplanet.dto.request.DataExportReq;
import com.growthplanet.dto.request.RevokeConsentReq;
import com.growthplanet.dto.response.ConsentResp;
import com.growthplanet.dto.response.DataExportResp;
import com.growthplanet.service.ComplianceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * COMPLIANCE 组接口：consent(GET/POST) / consent/revoke / data-export。
 * revoke 成功返回 HTTP 200；撤回后的授权写入返回 E-010 / HTTP 409。
 */
@RestController
@RequestMapping("/api")
public class ComplianceController {

    private final ComplianceService complianceService;

    public ComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    @GetMapping("/compliance/consent")
    @RequireRole(RoleEnum.PARENT)
    public Result<ConsentResp> getConsent(@RequestParam @Positive Long childId, @RequestParam String consentType) {
        return Result.ok(complianceService.getConsent(childId, consentType));
    }

    @PostMapping("/compliance/consent")
    @RequireRole(RoleEnum.PARENT)
    public Result<ConsentResp> submitConsent(@RequestBody @Valid ConsentReq req) {
        return Result.ok(complianceService.submitConsent(req));
    }

    @PostMapping("/compliance/consent/revoke")
    @RequireRole(RoleEnum.PARENT)
    public Result<Map<String, String>> revokeConsent(@RequestBody @Valid RevokeConsentReq req) {
        String status = complianceService.revokeConsent(req);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("status", status);
        return Result.ok(data);
    }

    @PostMapping("/compliance/data-export")
    @RequireRole(RoleEnum.PARENT)
    public Result<DataExportResp> dataExport(@RequestBody @Valid DataExportReq req,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(complianceService.dataExport(req, key));
    }

    @GetMapping("/compliance/requests/{id}")
    @RequireRole(RoleEnum.PARENT)
    public Result<DataExportResp> getRequest(@PathVariable @Positive Long id) {
        return Result.ok(complianceService.getRequest(id));
    }
}
