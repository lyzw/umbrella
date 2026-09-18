package com.growthplanet.controller;

import com.growthplanet.common.annotation.RequireRole;
import com.growthplanet.common.enums.RoleEnum;
import com.growthplanet.common.result.Result;
import com.growthplanet.common.result.ResultCode;
import com.growthplanet.dto.request.ConsentReq;
import com.growthplanet.dto.request.DataExportReq;
import com.growthplanet.dto.request.RevokeConsentReq;
import com.growthplanet.dto.response.ConsentResp;
import com.growthplanet.dto.response.DataExportResp;
import com.growthplanet.service.ComplianceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * COMPLIANCE 组接口：consent(GET/POST) / consent/revoke / data-export。
 * 注意：revoke 业务上返回 E-010 业务码 + HTTP 409，并携带 {status:"REVOKED"}。
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
    public Result<ConsentResp> getConsent(@RequestParam(required = false) Long childId) {
        return Result.ok(complianceService.getConsent(childId));
    }

    @PostMapping("/compliance/consent")
    @RequireRole(RoleEnum.PARENT)
    public Result<ConsentResp> submitConsent(@RequestBody @Valid ConsentReq req) {
        return Result.ok(complianceService.submitConsent(req));
    }

    @PostMapping("/compliance/consent/revoke")
    @RequireRole(RoleEnum.PARENT)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Map<String, String>> revokeConsent(@RequestBody @Valid RevokeConsentReq req) {
        String status = complianceService.revokeConsent(req);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("status", status);
        return Result.of(ResultCode.E010_CONSENT_REVOKED, data);
    }

    @PostMapping("/compliance/data-export")
    @RequireRole(RoleEnum.PARENT)
    public Result<DataExportResp> dataExport(@RequestBody @Valid DataExportReq req) {
        return Result.ok(complianceService.dataExport(req));
    }
}
