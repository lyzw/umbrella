package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.CheckItemReq;
import cn.studykid.growthplanet.dto.response.CheckItemResp;
import cn.studykid.growthplanet.service.CheckService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 家长打卡项配置（F-033）。familyId 后端派生；所有操作过同意门禁。 */
@RestController
@RequestMapping("/api/parent/check-item")
public class CheckItemController {
    private final CheckService service;

    public CheckItemController(CheckService service) {
        this.service = service;
    }

    @PostMapping
    @RequireRole(RoleEnum.PARENT)
    public Result<CheckItemResp> create(@RequestBody @Valid CheckItemReq req) {
        return Result.ok(service.createItem(req));
    }

    @GetMapping
    @RequireRole(RoleEnum.PARENT)
    public Result<List<CheckItemResp>> list() {
        return Result.ok(service.listItems());
    }

    @PutMapping("/{id}")
    @RequireRole(RoleEnum.PARENT)
    public Result<CheckItemResp> update(@PathVariable @Positive Long id, @RequestBody @Valid CheckItemReq req,
            @RequestParam Integer expectedVersion) {
        return Result.ok(service.updateItem(id, req, expectedVersion));
    }

    @DeleteMapping("/{id}")
    @RequireRole(RoleEnum.PARENT)
    public Result<Void> delete(@PathVariable @Positive Long id, @RequestParam Integer expectedVersion) {
        service.deleteItem(id, expectedVersion);
        return Result.ok();
    }
}
