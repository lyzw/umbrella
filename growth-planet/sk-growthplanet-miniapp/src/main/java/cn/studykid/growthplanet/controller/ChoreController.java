package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.ChoreClaimReq;
import cn.studykid.growthplanet.dto.request.ChoreConfirmReq;
import cn.studykid.growthplanet.dto.request.ChoreRejectReq;
import cn.studykid.growthplanet.dto.request.ChoreSubmitReq;
import cn.studykid.growthplanet.dto.request.ChoreTaskReq;
import cn.studykid.growthplanet.dto.response.ChoreInstanceResp;
import cn.studykid.growthplanet.dto.response.ChoreTaskResp;
import cn.studykid.growthplanet.service.ChoreService;
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
@RequestMapping("/api/mini/chore")
public class ChoreController {
    private final ChoreService service;

    public ChoreController(ChoreService service) {
        this.service = service;
    }

    @PostMapping("/task")
    @RequireRole(RoleEnum.PARENT)
    public Result<ChoreTaskResp> createTask(@RequestBody @Valid ChoreTaskReq req) {
        return Result.ok(service.createTask(req));
    }

    @GetMapping("/tasks")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<List<ChoreTaskResp>> listTasks() {
        return Result.ok(service.listTasks());
    }

    @PostMapping("/claim")
    @RequireRole(RoleEnum.CHILD)
    public Result<ChoreInstanceResp> claim(@RequestBody @Valid ChoreClaimReq req) {
        return Result.ok(service.claim(req));
    }

    @PostMapping("/submit")
    @RequireRole(RoleEnum.CHILD)
    public Result<ChoreInstanceResp> submit(@RequestBody @Valid ChoreSubmitReq req) {
        return Result.ok(service.submit(req));
    }

    @PostMapping("/confirm")
    @RequireRole(RoleEnum.PARENT)
    public Result<ChoreInstanceResp> confirm(@RequestBody @Valid ChoreConfirmReq req) {
        return Result.ok(service.confirm(req));
    }

    @PostMapping("/reject")
    @RequireRole(RoleEnum.PARENT)
    public Result<ChoreInstanceResp> reject(@RequestBody @Valid ChoreRejectReq req) {
        return Result.ok(service.reject(req));
    }

    @GetMapping("/instances")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<List<ChoreInstanceResp>> listInstances(@RequestParam @Positive Long childId,
            @RequestParam(required = false) String status) {
        return Result.ok(service.listInstances(childId, status));
    }

    @GetMapping("/instance/{id}")
    @RequireRole({RoleEnum.CHILD, RoleEnum.PARENT})
    public Result<ChoreInstanceResp> detail(@PathVariable @Positive Long id) {
        return Result.ok(service.detail(id));
    }
}
