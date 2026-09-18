package com.growthplanet.controller;

import com.growthplanet.common.annotation.RequireRole;
import com.growthplanet.common.enums.RoleEnum;
import com.growthplanet.common.result.Result;
import com.growthplanet.dto.request.BindApproveReq;
import com.growthplanet.dto.request.CreateFamilyReq;
import com.growthplanet.dto.request.JoinFamilyReq;
import com.growthplanet.dto.response.BindApproveResp;
import com.growthplanet.dto.response.CreateFamilyResp;
import com.growthplanet.dto.response.InviteCodeResp;
import com.growthplanet.dto.response.JoinFamilyResp;
import com.growthplanet.service.FamilyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FAMILY 组接口：create / invite-code / join / bind-approve。
 */
@RestController
@RequestMapping("/api")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @PostMapping("/family/create")
    @RequireRole(RoleEnum.PARENT)
    public Result<CreateFamilyResp> createFamily(@RequestBody @Valid CreateFamilyReq req) {
        return Result.ok(familyService.createFamily(req));
    }

    @GetMapping("/family/invite-code")
    @RequireRole(RoleEnum.PARENT)
    public Result<InviteCodeResp> getInviteCode() {
        return Result.ok(familyService.getInviteCode());
    }

    @PostMapping("/family/join")
    @RequireRole(RoleEnum.CHILD)
    public Result<JoinFamilyResp> joinFamily(@RequestBody @Valid JoinFamilyReq req) {
        return Result.ok(familyService.joinFamily(req));
    }

    @PostMapping("/family/bind-approve")
    @RequireRole(RoleEnum.PARENT)
    public Result<BindApproveResp> bindApprove(@RequestBody @Valid BindApproveReq req) {
        return Result.ok(familyService.bindApprove(req));
    }
}
