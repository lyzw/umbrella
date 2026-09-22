package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.BindApproveReq;
import cn.studykid.growthplanet.dto.request.CreateFamilyReq;
import cn.studykid.growthplanet.dto.request.JoinFamilyReq;
import cn.studykid.growthplanet.dto.response.BindApproveResp;
import cn.studykid.growthplanet.dto.response.CreateFamilyResp;
import cn.studykid.growthplanet.dto.response.InviteCodeResp;
import cn.studykid.growthplanet.dto.response.JoinFamilyResp;
import cn.studykid.growthplanet.dto.response.FamilyChildResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.service.FamilyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * FAMILY 组接口：create / invite-code / join / bind-approve。
 */
@RestController
@RequestMapping("/api/mini")
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

    @GetMapping("/family/children")
    @RequireRole(RoleEnum.PARENT)
    public Result<PageResp<FamilyChildResp>> getChildren(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String bindStatus) {
        return Result.ok(familyService.getChildren(page, pageSize, bindStatus));
    }

    @GetMapping("/family/binding")
    @RequireRole(RoleEnum.CHILD)
    public Result<FamilyChildResp> getBinding() {
        return Result.ok(familyService.getBinding());
    }
}
