package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.ScheduleCreateReq;
import cn.studykid.growthplanet.dto.response.ScheduleOccurrenceResp;
import cn.studykid.growthplanet.dto.response.ScheduleResp;
import cn.studykid.growthplanet.service.ScheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {
    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @PostMapping
    @RequireRole(RoleEnum.PARENT)
    public Result<ScheduleResp> create(@RequestBody @Valid ScheduleCreateReq req) {
        return Result.ok(service.create(req));
    }

    @GetMapping("/list")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<List<ScheduleResp>> list(@RequestParam @Positive Long childId) {
        return Result.ok(service.list(childId));
    }

    @GetMapping("/occurrences")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<List<ScheduleOccurrenceResp>> occurrences(@RequestParam @Positive Long childId,
            @RequestParam String from, @RequestParam String to) {
        return Result.ok(service.occurrences(childId, from, to));
    }

    @GetMapping("/{id}")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<ScheduleResp> detail(@PathVariable @Positive Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping("/{id}/cancel")
    @RequireRole(RoleEnum.PARENT)
    public Result<ScheduleResp> cancel(@PathVariable @Positive Long id) {
        return Result.ok(service.cancel(id));
    }
}
