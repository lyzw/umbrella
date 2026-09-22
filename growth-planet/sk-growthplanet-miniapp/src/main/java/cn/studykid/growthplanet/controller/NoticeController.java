package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.NoticeSubscriptionReq;
import cn.studykid.growthplanet.dto.response.NoticeResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.service.NoticeCenterService;
import cn.studykid.growthplanet.service.NoticeSubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {
    private final NoticeCenterService center;
    private final NoticeSubscriptionService subscriptions;

    public NoticeController(NoticeCenterService center, NoticeSubscriptionService subscriptions) {
        this.center = center;
        this.subscriptions = subscriptions;
    }

    @GetMapping
    @RequireRole({RoleEnum.PARENT, RoleEnum.CHILD})
    public Result<PageResp<NoticeResp>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Boolean unreadOnly) {
        return Result.ok(center.list(page, pageSize, unreadOnly));
    }

    @GetMapping("/unread-count")
    @RequireRole({RoleEnum.PARENT, RoleEnum.CHILD})
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(Map.of("unreadCount", center.unreadCount()));
    }

    @PostMapping("/{id}/read")
    @RequireRole({RoleEnum.PARENT, RoleEnum.CHILD})
    public Result<Map<String, Boolean>> read(@PathVariable @Positive Long id) {
        center.markRead(id);
        return Result.ok(Map.of("read", true));
    }

    @PostMapping("/subscription")
    @RequireRole({RoleEnum.PARENT, RoleEnum.CHILD})
    public Result<Map<String, String>> subscribe(@RequestBody @Valid NoticeSubscriptionReq req) {
        return Result.ok(Map.of("status", subscriptions.authorize(req)));
    }
}
