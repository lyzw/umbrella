package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.response.CheckCalendarResp;
import cn.studykid.growthplanet.dto.response.CheckItemResp;
import cn.studykid.growthplanet.dto.response.CheckItemTodayResp;
import cn.studykid.growthplanet.dto.response.CheckRecordResp;
import cn.studykid.growthplanet.service.CheckService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 儿童打卡（F-034/F-035）。所有操作过同意门禁；撤回同意后停采并隐藏。 */
@RestController
@RequestMapping("/api/child/check-in")
public class CheckInController {
    private final CheckService service;

    public CheckInController(CheckService service) {
        this.service = service;
    }

    @GetMapping("/items")
    @RequireRole(RoleEnum.CHILD)
    public Result<List<CheckItemResp>> items() {
        return Result.ok(service.listAvailableItems());
    }

    @PostMapping
    @RequireRole(RoleEnum.CHILD)
    public Result<CheckRecordResp> checkIn(@RequestParam @Positive Long itemId) {
        return Result.ok(service.checkIn(itemId));
    }

    @GetMapping("/today")
    @RequireRole(RoleEnum.CHILD)
    public Result<List<CheckItemTodayResp>> today() {
        return Result.ok(service.todayCounts());
    }

    @GetMapping("/calendar")
    @RequireRole(RoleEnum.CHILD)
    public Result<CheckCalendarResp> calendar(@RequestParam(required = false) String month) {
        return Result.ok(service.calendar(month));
    }
}
