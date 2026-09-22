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
            @RequestParam(required = false) String direction, @RequestParam(required = false) String scene,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.logs(childId, startDate, endDate, direction, scene, page, pageSize));
    }

    /** F-024 儿童端零花钱主页：余额、今日/本周已用与上限、本周剩余额度、本月已用与进度。 */
    @GetMapping("/overview")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<WalletOverviewResp> overview(@RequestParam @Positive Long childId) {
        return Result.ok(service.overview(childId));
    }

    /** F-024 家长端零花钱看板：家庭虚拟总额、本周支出/发放与各子女概览。 */
    @GetMapping("/board")
    @RequireRole(RoleEnum.PARENT)
    public Result<WalletBoardResp> board() {
        return Result.ok(service.board());
    }

    /** F-025 消费预算可视化：趋势序列与支出/收入分类占比，range 取 WEEK 或 MONTH。 */
    @GetMapping("/stats")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<WalletStatsResp> stats(@RequestParam @Positive Long childId,
            @RequestParam(defaultValue = "WEEK") String range) {
        return Result.ok(service.stats(childId, range));
    }
}
