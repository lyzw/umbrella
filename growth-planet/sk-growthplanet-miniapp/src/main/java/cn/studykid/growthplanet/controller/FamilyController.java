package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.BindApproveReq;
import cn.studykid.growthplanet.dto.request.CreateFamilyReq;
import cn.studykid.growthplanet.dto.request.GenerateInviteCodeReq;
import cn.studykid.growthplanet.dto.request.JoinFamilyReq;
import cn.studykid.growthplanet.dto.request.RemoveMemberReq;
import cn.studykid.growthplanet.dto.request.RevokeInviteCodeReq;
import cn.studykid.growthplanet.dto.request.TransferOwnerReq;
import cn.studykid.growthplanet.dto.response.BindApproveResp;
import cn.studykid.growthplanet.dto.response.CreateFamilyResp;
import cn.studykid.growthplanet.dto.response.FamilyChildResp;
import cn.studykid.growthplanet.dto.response.FamilyInviteResp;
import cn.studykid.growthplanet.dto.response.InviteCodeResp;
import cn.studykid.growthplanet.dto.response.JoinFamilyResp;
import cn.studykid.growthplanet.dto.response.MyFamilyResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.service.FamilyService;
import java.util.List;
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
    @Deprecated
    public Result<InviteCodeResp> getInviteCode() {
        return Result.ok(familyService.getInviteCode());
    }

    @PostMapping("/family/invite-code")
    @RequireRole(RoleEnum.PARENT)
    public Result<FamilyInviteResp> generateInviteCode(@RequestBody @Valid GenerateInviteCodeReq req) {
        return Result.ok(familyService.generateInviteCode(req));
    }

    @GetMapping("/family/invite-codes")
    @RequireRole(RoleEnum.PARENT)
    public Result<List<FamilyInviteResp>> listInviteCodes() {
        return Result.ok(familyService.listInviteCodes());
    }

    @PostMapping("/family/invite-code/revoke")
    @RequireRole(RoleEnum.PARENT)
    public Result<Void> revokeInviteCode(@RequestBody @Valid RevokeInviteCodeReq req) {
        familyService.revokeInviteCode(req);
        return Result.ok();
    }

    @PostMapping("/family/join")
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

    @GetMapping("/family/my")
    public Result<List<MyFamilyResp>> myFamilies() {
        return Result.ok(familyService.myFamilies());
    }

    @PostMapping("/family/leave")
    public Result<Void> leaveFamily() {
        familyService.leaveFamily();
        return Result.ok();
    }

    @PostMapping("/family/remove-member")
    @RequireRole(RoleEnum.PARENT)
    public Result<Void> removeMember(@RequestBody @Valid RemoveMemberReq req) {
        familyService.removeMember(req);
        return Result.ok();
    }

    @PostMapping("/family/transfer-owner")
    @RequireRole(RoleEnum.PARENT)
    public Result<Void> transferOwner(@RequestBody @Valid TransferOwnerReq req) {
        familyService.transferOwner(req);
        return Result.ok();
    }
}
