package cn.studykid.growthplanet.controller;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.dto.request.WishMarkReq;
import cn.studykid.growthplanet.dto.request.WishSettingReq;
import cn.studykid.growthplanet.dto.request.WishSubmitReq;
import cn.studykid.growthplanet.dto.request.WishWithdrawReq;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.dto.response.WishDishResp;
import cn.studykid.growthplanet.dto.response.WishMenuListResp;
import cn.studykid.growthplanet.dto.response.WishMenuResp;
import cn.studykid.growthplanet.dto.response.WishSettingResp;
import cn.studykid.growthplanet.service.WishMenuService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 心愿菜单接口（P3）：家长配上限 / 儿童选菜提交 / 家长查看。
 * <p>
 * 儿童侧 childId 一律取登录上下文；家长侧由 childId 经 boundChild + requireParent 派生家庭。
 * familyId / ownerKey / sourceType（菜单上下文）严禁客户端传入。
 */
@RestController
@RequestMapping("/api")
public class WishMenuController {
    private final WishMenuService wishes;

    public WishMenuController(WishMenuService wishes) {
        this.wishes = wishes;
    }

    // ==================== 儿童 ====================

    /** 可选菜谱目录（FAMILY = 本家庭在售私有菜；PRESET = 平台在售预置菜）。 */
    @GetMapping("/child/wish-catalog")
    @RequireRole(RoleEnum.CHILD)
    public Result<PageResp<WishDishResp>> catalog(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate menuDate,
            @RequestParam String sourceType, @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(wishes.catalog(menuDate, sourceType, categoryId, keyword, page, pageSize));
    }

    /** 某日心愿菜单详情（候选池 + 状态 + 上限）。 */
    @GetMapping("/child/wish-menu")
    @RequireRole(RoleEnum.CHILD)
    public Result<WishMenuResp> childMenu(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate menuDate) {
        return Result.ok(wishes.childMenu(menuDate));
    }

    /** 候选池加入 / 移除（不依赖当天菜单是否发布）。 */
    @PostMapping("/child/wish-mark")
    @RequireRole(RoleEnum.CHILD)
    public Result<WishMenuResp> mark(@RequestBody @Valid WishMarkReq req) {
        return Result.ok(wishes.mark(req));
    }

    /** 提交心愿菜单（按家长上限校验 → 通知家长）。 */
    @PostMapping("/child/wish-menu/submit")
    @RequireRole(RoleEnum.CHILD)
    public Result<WishMenuResp> submit(@RequestBody @Valid WishSubmitReq req) {
        return Result.ok(wishes.submit(req));
    }

    /** 撤回心愿菜单（解除当日锁定）。 */
    @PostMapping("/child/wish-menu/withdraw")
    @RequireRole(RoleEnum.CHILD)
    public Result<WishMenuResp> withdraw(@RequestBody @Valid WishWithdrawReq req) {
        return Result.ok(wishes.withdraw(req));
    }

    // ==================== 家长 ====================

    /** 查看某个孩子某日的心愿菜单。 */
    @GetMapping("/parent/wish-menu")
    @RequireRole(RoleEnum.PARENT)
    public Result<WishMenuResp> parentMenu(@RequestParam @Positive Long childId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate menuDate) {
        return Result.ok(wishes.parentMenu(childId, menuDate));
    }

    /** 按区间查看孩子的心愿菜单提交记录（≤31 天）。 */
    @GetMapping("/parent/wish-menu/list")
    @RequireRole(RoleEnum.PARENT)
    public Result<WishMenuListResp> parentList(@RequestParam @Positive Long childId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(wishes.parentList(childId, from, to));
    }

    /** 读取家庭心愿菜单设置（上限 + 开关）。 */
    @GetMapping("/parent/wish-setting")
    @RequireRole(RoleEnum.PARENT)
    public Result<WishSettingResp> setting() {
        return Result.ok(wishes.setting());
    }

    /** 更新家庭心愿菜单设置（乐观锁）。 */
    @PutMapping("/parent/wish-setting")
    @RequireRole(RoleEnum.PARENT)
    public Result<WishSettingResp> updateSetting(@RequestBody @Valid WishSettingReq req) {
        return Result.ok(wishes.updateSetting(req));
    }
}
