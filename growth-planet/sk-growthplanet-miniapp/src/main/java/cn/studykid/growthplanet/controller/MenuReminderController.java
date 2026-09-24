package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.response.MenuReminderResp;
import cn.studykid.growthplanet.service.MenuReminderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 儿童首页的家庭餐单提醒入口。
 */
@RestController
@RequestMapping("/api/mini/child")
public class MenuReminderController {
    private final MenuReminderService service;

    public MenuReminderController(MenuReminderService service) {
        this.service = service;
    }

    @PostMapping("/menu-reminder")
    @RequireRole(RoleEnum.CHILD)
    public Result<MenuReminderResp> remindParent() {
        return Result.ok(service.remindParent());
    }
}
