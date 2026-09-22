package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.QuickBindParentReq;
import cn.studykid.growthplanet.dto.response.QuickBindParentResp;
import cn.studykid.growthplanet.service.impl.DevBindingService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发/测试专用接口，仅 dev / test profile 加载。
 */
@RestController
@RequestMapping("/api/dev")
@Profile({"dev", "test"})
public class DevController {

    private final DevBindingService devBindingService;

    public DevController(DevBindingService devBindingService) {
        this.devBindingService = devBindingService;
    }

    @PostMapping("/quick-bind-parent")
    @RequireRole(RoleEnum.CHILD)
    public Result<QuickBindParentResp> quickBindParent(@RequestBody @Valid QuickBindParentReq req) {
        return Result.ok(devBindingService.quickBindParent(UserContext.get(), req));
    }
}
