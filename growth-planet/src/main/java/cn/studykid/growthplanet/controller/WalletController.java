package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.*;
import cn.studykid.growthplanet.dto.response.*;
import cn.studykid.growthplanet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    @GetMapping("/balance")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<WalletResp> balance(@RequestParam @Positive Long childId) {
        return Result.ok(service.balance(childId));
    }

    @PostMapping("/grant")
    @RequireRole(RoleEnum.PARENT)
    public Result<WalletResp> grant(@RequestBody @Valid WalletGrantReq req,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(service.grant(req, key));
    }

    @GetMapping("/allowance-rule")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<AllowanceRuleResp> rule(@RequestParam @Positive Long childId) {
        return Result.ok(service.rule(childId));
    }

    @PutMapping("/allowance-rule")
    @RequireRole(RoleEnum.PARENT)
    public Result<AllowanceRuleResp> updateRule(@RequestBody @Valid AllowanceRuleReq req) {
        return Result.ok(service.updateRule(req));
    }

    @GetMapping("/allowance-log")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<PageResp<AllowanceLogResp>> logs(@RequestParam @Positive Long childId,
            @RequestParam(required = false) LocalDate startDate, @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.logs(childId, startDate, endDate, page, pageSize));
    }
}
