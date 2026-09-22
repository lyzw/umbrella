package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.LoginUser;
import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.dto.request.WishMarkReq;
import cn.studykid.growthplanet.dto.request.WishSettingReq;
import cn.studykid.growthplanet.dto.request.WishSubmitReq;
import cn.studykid.growthplanet.dto.request.WishWithdrawReq;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.dto.response.WishDishResp;
import cn.studykid.growthplanet.dto.response.WishMenuListResp;
import cn.studykid.growthplanet.dto.response.WishMenuResp;
import cn.studykid.growthplanet.dto.response.WishSettingResp;
import cn.studykid.growthplanet.entity.ChildProfile;
import cn.studykid.growthplanet.entity.ChildWantEat;
import cn.studykid.growthplanet.entity.Dish;
import cn.studykid.growthplanet.entity.DishCategory;
import cn.studykid.growthplanet.entity.DishRef;
import cn.studykid.growthplanet.entity.Family;
import cn.studykid.growthplanet.entity.FamilyDish;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.entity.FamilySetting;
import cn.studykid.growthplanet.entity.WishMenu;
import cn.studykid.growthplanet.mapper.ChildProfileMapper;
import cn.studykid.growthplanet.mapper.ChildWantEatMapper;
import cn.studykid.growthplanet.mapper.DishCategoryMapper;
import cn.studykid.growthplanet.mapper.DishMapper;
import cn.studykid.growthplanet.mapper.FamilyDishMapper;
import cn.studykid.growthplanet.mapper.FamilyMapper;
import cn.studykid.growthplanet.mapper.FamilySettingMapper;
import cn.studykid.growthplanet.mapper.WishMenuMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.validation.Validator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 儿童心愿菜单（P3）：家长配上限 / 儿童选菜提交 / 家长收通知。
 * <p>
 * 数据口径（决策 D1「数据复用」）：
 * <ul>
 *   <li>明细复用 {@code usr_child_want_eat}（候选池 = 该日全部想吃标记，按 (type,id) 去重）；</li>
 *   <li>提交状态放独立头表 {@code life_wish_menu}（当天一份）；</li>
 *   <li>提交时把命中的明细行 {@code wish_id} 指向头表，撤回后保留以便回显。</li>
 * </ul>
 * 可选菜谱（决策 D2）= 家庭在售私有菜 ∪ 平台在售预置菜（SCHOOL 排除，不依赖当天菜单是否发布）。
 * 锁与并发：读路径全程不加行锁（R6）；写路径走 {@code lockBoundChild + requireConsent}；
 * 头表/设置用手写乐观锁（项目未装配乐观锁插件）。
 */
@Service
@Transactional
public class WishMenuService {
    private static final Set<String> TYPES = Set.of("PRESET", "FAMILY");
    /** 心愿目录标记使用的占位餐次：心愿菜单不分餐次。 */
    private static final String MEAL_ALL = "ALL";
    /** 心愿目录标记使用的来源：无"某个菜单"上下文。 */
    private static final String SOURCE_WISH = "WISH";
    private static final String STATUS_MARKED = "MARKED";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_WITHDRAWN = "WITHDRAWN";
    private static final String STATUS_NONE = "NONE";
    private static final int DEFAULT_MAX_DISHES = 5;
    private static final int MIN_MAX_DISHES = 1;
    private static final int MAX_MAX_DISHES = 10;
    /** 可编辑窗口：今天起 7 天（今天 + 未来 6 天）；过去日期只读回看。 */
    private static final int WINDOW_DAYS = 6;
    private static final int LIST_MAX_DAYS = 31;
    private static final int MAX_PAGE_SIZE = 100;

    private final WishMenuMapper wishMenus;
    private final FamilySettingMapper settings;
    private final ChildWantEatMapper wantEats;
    private final DishMapper dishes;
    private final FamilyDishMapper familyDishes;
    private final DishCategoryMapper categories;
    private final FamilyMapper families;
    private final ChildProfileMapper profiles;
    private final ChildAuthorizationService authorization;
    private final NoticeService notices;
    private final AuditService audit;
    private final Validator validator;
    private final BusinessTime time;
    private final ComplianceProperties policy;

    public WishMenuService(WishMenuMapper wishMenus, FamilySettingMapper settings, ChildWantEatMapper wantEats,
            DishMapper dishes, FamilyDishMapper familyDishes, DishCategoryMapper categories, FamilyMapper families,
            ChildProfileMapper profiles, ChildAuthorizationService authorization, NoticeService notices,
            AuditService audit, Validator validator, BusinessTime time, ComplianceProperties policy) {
        this.wishMenus = wishMenus;
        this.settings = settings;
        this.wantEats = wantEats;
        this.dishes = dishes;
        this.familyDishes = familyDishes;
        this.categories = categories;
        this.families = families;
        this.profiles = profiles;
        this.authorization = authorization;
        this.notices = notices;
        this.audit = audit;
        this.validator = validator;
        this.time = time;
        this.policy = policy;
    }

    // ==================== 家长：设置 ====================

    /** 读取家庭心愿菜单设置（无行 → 默认 5 / 开启 / version 0）。 */
    public WishSettingResp setting() {
        requireRole("PARENT");
        Long familyId = currentFamilyId();
        authorization.requireParent(familyId);
        return settingResponse(effectiveSetting(familyId, false));
    }

    /** 更新家庭心愿菜单设置（上限 1~10 + 开关；手写乐观锁，版本不符 → E007）。 */
    public WishSettingResp updateSetting(WishSettingReq req) {
        requireRole("PARENT");
        validate(req);
        if (req.getMaxDishes() < MIN_MAX_DISHES || req.getMaxDishes() > MAX_MAX_DISHES) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "心愿菜单上限需在 1~10 之间");
        }
        Long familyId = currentFamilyId();
        authorization.requireParent(familyId);
        int enabled = Boolean.TRUE.equals(req.getEnabled()) ? 1 : 0;
        FamilySetting existing = selectSetting(familyId, true);
        if (existing == null) {
            // 尚未配置过：expectedVersion 必须为 0，否则说明客户端基于过期视图提交。
            if (req.getExpectedVersion() != 0) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
            FamilySetting row = new FamilySetting();
            row.setFamilyId(familyId);
            row.setWishMenuMaxDishes(req.getMaxDishes());
            row.setWishMenuEnabled(enabled);
            // 版本语义：version = 该家庭设置已成功写入的次数；0 专指"尚无行（默认值视图）"。
            // 若首次写入落 0，"无行"与"已保存一次"会同版本，客户端拿着默认值视图(0)重提交就不会被拦 → 首写失去保护。
            row.setVersion(1);
            try {
                if (settings.insert(row) != 1) {
                    throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
                }
            } catch (DuplicateKeyException ex) {
                // 并发首次配置：另一请求已插入，要求重查后再改。
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
            audit.record("WISH_SETTING_UPDATE", UserContext.userId(), familyId, "FAMILY_SETTING", row.getId(), null,
                    "created;maxDishes=" + req.getMaxDishes() + ";enabled=" + enabled);
            return settingResponse(effectiveSetting(familyId, false));
        }
        if (existing.getVersion() == null || !existing.getVersion().equals(req.getExpectedVersion())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        int before = existing.getVersion();
        FamilySetting update = new FamilySetting();
        update.setWishMenuMaxDishes(req.getMaxDishes());
        update.setWishMenuEnabled(enabled);
        update.setVersion(Math.incrementExact(before));
        if (settings.update(update, new UpdateWrapper<FamilySetting>().eq("id", existing.getId())
                .eq("version", before)) != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        audit.record("WISH_SETTING_UPDATE", UserContext.userId(), familyId, "FAMILY_SETTING", existing.getId(), null,
                "maxDishes=" + req.getMaxDishes() + ";enabled=" + enabled + ";version=" + before);
        return settingResponse(effectiveSetting(familyId, false));
    }

    // ==================== 儿童：可选菜谱目录 ====================

    /**
     * 心愿菜单可选菜谱目录：FAMILY = 本家庭在售私有菜；PRESET = 平台在售预置菜。
     * 两表各自分页（不跨表合并），SCHOOL 学校菜品不在范围内。
     */
    public PageResp<WishDishResp> catalog(LocalDate menuDate, String sourceType, Long categoryId, String keyword,
            int page, int pageSize) {
        LoginUser ctx = requireRole("CHILD");
        requireBrowsableDate(menuDate);
        if (sourceType == null || !TYPES.contains(sourceType)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        Long childId = ctx.getUserId();
        // 纯读：只校验授权，不加行锁（R6）。
        FamilyMember member = authorization.boundChild(childId);
        authorization.requireConsentReadOnly(member);
        ChildProfile profile = requireProfile(member, false);
        long offset = (long) (page - 1) * pageSize;
        String name = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Set<Long> marked = markedDishIds(childId, menuDate, sourceType);

        List<WishDishResp> items = new ArrayList<>();
        long total;
        if ("FAMILY".equals(sourceType)) {
            QueryWrapper<FamilyDish> countQuery = familyDishQuery(member.getFamilyId(), categoryId, name);
            total = familyDishes.selectCount(countQuery);
            List<FamilyDish> rows = familyDishes.selectList(familyDishQuery(member.getFamilyId(), categoryId, name)
                    .orderByAsc("id").last("LIMIT " + offset + ", " + pageSize));
            Map<Long, String> categoryNames = categoryNameMap(rows.stream().map(FamilyDish::getCategoryId).toList());
            for (FamilyDish fd : rows) {
                Dish dish = toDish(fd);
                items.add(dishResponse(dish, "FAMILY", categoryNames, profile, marked.contains(fd.getId())));
            }
        } else {
            QueryWrapper<Dish> countQuery = presetQuery(categoryId, name);
            total = dishes.selectCount(countQuery);
            List<Dish> rows = dishes.selectList(presetQuery(categoryId, name).orderByAsc("id")
                    .last("LIMIT " + offset + ", " + pageSize));
            Map<Long, String> categoryNames = categoryNameMap(rows.stream().map(Dish::getCategoryId).toList());
            for (Dish dish : rows) {
                items.add(dishResponse(dish, "PRESET", categoryNames, profile, marked.contains(dish.getId())));
            }
        }
        audit.record("WISH_CATALOG_QUERY", ctx.getUserId(), member.getFamilyId(), "CHILD", childId, null,
                "sourceType=" + sourceType + ";menuDate=" + menuDate);
        return new PageResp<>(items, total, page, pageSize);
    }

    // ==================== 儿童：心愿菜单读写 ====================

    /** 儿童查看自己某日的心愿菜单（含候选池与目录状态）。 */
    public WishMenuResp childMenu(LocalDate menuDate) {
        LoginUser ctx = requireRole("CHILD");
        FamilyMember member = authorization.boundChild(ctx.getUserId());
        authorization.requireConsentReadOnly(member);
        ChildProfile profile = requireProfile(member, false);
        return detail(member, profile, ctx.getUserId(), menuDate);
    }

    /** 心愿菜单候选池标记 / 移除（可来自目录中任意"家庭+预置"在售菜，不限当天菜单内）。 */
    public WishMenuResp mark(WishMarkReq req) {
        LoginUser ctx = requireRole("CHILD");
        validate(req);
        Long childId = ctx.getUserId();
        FamilyMember member = authorization.lockBoundChild(childId);
        var consent = authorization.requireConsent(member);
        ChildProfile profile = requireProfile(member, true);
        requireWritable(req.getMenuDate(), member.getFamilyId());
        requireUnlocked(childId, req.getMenuDate());
        if (req.getType() == null || !TYPES.contains(req.getType())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        Map<String, Dish> visible = loadDishes(List.of(ref(req.getType(), req.getId())), member.getFamilyId());
        Dish dish = visible.get(req.getType() + ":" + req.getId());
        boolean selected = Boolean.TRUE.equals(req.getSelected());
        if (selected) {
            // 加入需菜品在售且安全信息完整；移除不要求菜品存在（下架/删除后仍可移除）。
            if (dish == null || !"ON_SALE".equals(dish.getStatus())) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "该菜品当前不可加入心愿菜单");
            }
            if (!"DECLARED".equals(safetyStatus(dish, profile))) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "过敏信息不完整或含过敏原，不能加入心愿菜单");
            }
            ChildWantEat existing = wantEats.selectOne(new QueryWrapper<ChildWantEat>()
                    .eq("child_id", childId).eq("menu_date", req.getMenuDate())
                    .eq("meal_type", MEAL_ALL).eq("dish_type", req.getType()).eq("dish_id", req.getId()));
            if (existing == null) {
                ChildWantEat row = new ChildWantEat();
                row.setChildId(childId);
                row.setFamilyId(member.getFamilyId());
                row.setMenuDate(req.getMenuDate());
                row.setMealType(MEAL_ALL);
                row.setSourceType(SOURCE_WISH);
                row.setDishType(req.getType());
                row.setDishId(req.getId());
                row.setStatus(STATUS_MARKED);
                row.setWishId(0L);
                if (wantEats.insert(row) != 1) {
                    throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
                }
            }
        } else {
            // 不按 dish_type 过滤：与 markFavorite 取消路径同款，可覆盖 PRESET/FAMILY 同号的两行。
            List<ChildWantEat> existing = wantEats.selectList(new QueryWrapper<ChildWantEat>()
                    .eq("child_id", childId).eq("menu_date", req.getMenuDate())
                    .eq("meal_type", MEAL_ALL).eq("dish_id", req.getId()));
            for (ChildWantEat row : existing) {
                if (wantEats.deleteById(row.getId()) != 1) {
                    throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
                }
            }
        }
        audit.record("WISH_MARK", ctx.getUserId(), member.getFamilyId(), "CHILD", childId, null,
                "consentId=" + consent.getId() + ";menuDate=" + req.getMenuDate() + ";type=" + req.getType()
                        + ";id=" + req.getId() + ";selected=" + selected);
        return detail(member, profile, childId, req.getMenuDate());
    }

    /**
     * 提交心愿菜单：按家长上限校验勾选道数 → 头处置为 SUBMITTED → 收编明细 wish_id →
     * 同事务写入家长站内/订阅通知。
     */
    public WishMenuResp submit(WishSubmitReq req) {
        LoginUser ctx = requireRole("CHILD");
        validate(req);
        Long childId = ctx.getUserId();
        FamilyMember member = authorization.lockBoundChild(childId);
        var consent = authorization.requireConsent(member);
        ChildProfile profile = requireProfile(member, true);
        SettingView setting = requireWritable(req.getMenuDate(), member.getFamilyId());
        List<WishSubmitReq.Ref> refs = dedupRefs(req.getRefs());
        if (refs.isEmpty()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "请至少选择一道菜");
        }
        if (refs.size() > setting.maxDishes()) {
            // 全局异常处理器只回错误码默认文案（不透出自定义 detail），故超限必须有独立错误码，
            // 否则客户端无法与其它 E-400 区分、只能显示"参数不合法"。
            throw new BizException(ResultCode.E014_WISH_LIMIT_REACHED,
                    "已选 " + refs.size() + " 道，超过家长设置的上限 " + setting.maxDishes() + " 道");
        }
        List<ChildWantEat> rows = wantEatRows(childId, req.getMenuDate());
        Set<String> available = new LinkedHashSet<>();
        for (ChildWantEat row : rows) {
            available.add(row.getDishType() + ":" + row.getDishId());
        }
        for (WishSubmitReq.Ref ref : refs) {
            if (!available.contains(ref.getType() + ":" + ref.getId())) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "心愿菜单只能提交已加入候选池的菜品");
            }
        }
        // 标记 → 提交之间菜品可能下架或补录过敏原（R5-c 只挡住"上架时就未声明"）。
        // 提交是给家长看的最终口径，此处按与目录同一套安全判定再挡一次（fail closed）。
        Map<String, Dish> dishesByRef = loadDishes(
                refs.stream().map(row -> ref(row.getType(), row.getId())).toList(), member.getFamilyId());
        for (WishSubmitReq.Ref ref : refs) {
            Dish dish = dishesByRef.get(ref.getType() + ":" + ref.getId());
            if (dish == null || !"DECLARED".equals(safetyStatus(dish, profile))) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT,
                        "心愿菜单中有菜品已下架或过敏信息待确认，请先移除后再提交");
            }
        }
        WishMenu menu = lockMenu(childId, req.getMenuDate());
        if (menu == null) {
            if (req.getExpectedVersion() != 0) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
            menu = new WishMenu();
            menu.setChildId(childId);
            menu.setFamilyId(member.getFamilyId());
            menu.setMenuDate(req.getMenuDate());
            menu.setStatus(STATUS_SUBMITTED);
            menu.setMaxDishes(setting.maxDishes());
            menu.setDishCount(refs.size());
            menu.setSubmitVersion(1);
            menu.setSubmitTime(time.now());
            // 版本语义同设置表：version = 写入次数，首次落 1（0 专指"当天还没有心愿单"）。
            menu.setVersion(1);
            try {
                if (wishMenus.insert(menu) != 1) {
                    throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
                }
            } catch (DuplicateKeyException ex) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
        } else {
            // 判定顺序：先乐观锁（陈旧视图 → E007），再状态机（已提交 → E400 提示先撤回）。
            // 反过来的话，陈旧客户端重复提交会被"已提交"的 E400 抢走，掩盖真正的版本冲突。
            if (menu.getVersion() == null || !menu.getVersion().equals(req.getExpectedVersion())
                    || menu.getSubmitVersion() == null) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
            if (STATUS_SUBMITTED.equals(menu.getStatus())) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "心愿菜单已提交，请先撤回再调整");
            }
            int before = menu.getVersion();
            WishMenu update = new WishMenu();
            update.setStatus(STATUS_SUBMITTED);
            update.setMaxDishes(setting.maxDishes());
            update.setDishCount(refs.size());
            update.setSubmitVersion(Math.incrementExact(menu.getSubmitVersion()));
            update.setSubmitTime(time.now());
            update.setVersion(Math.incrementExact(before));
            if (wishMenus.update(update, new UpdateWrapper<WishMenu>().eq("id", menu.getId())
                    .eq("version", before)) != 1) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
            menu.setSubmitVersion(update.getSubmitVersion());
            menu.setVersion(update.getVersion());
        }
        int collected = collect(member, req.getMenuDate(), menu.getId(), refs);
        if (collected != refs.size()) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        Family family = families.selectById(member.getFamilyId());
        Long receiver = family == null ? null : family.getOwnerUserId();
        // 通知与提交同事务：提交回滚则两通道行一并回滚。eventKey 含提交版本 → 每次有效提交一条新事件。
        notices.recordEvent("wish:" + menu.getId() + ":" + menu.getSubmitVersion(), "WISH_SUBMIT",
                member.getFamilyId(), childId, receiver);
        audit.record("WISH_SUBMIT", ctx.getUserId(), member.getFamilyId(), "WISH_MENU", menu.getId(), null,
                "consentId=" + consent.getId() + ";menuDate=" + req.getMenuDate() + ";dishCount=" + refs.size()
                        + ";maxDishes=" + setting.maxDishes() + ";submitVersion=" + menu.getSubmitVersion());
        return detail(member, profile, childId, req.getMenuDate());
    }

    /** 撤回心愿菜单：SUBMITTED → WITHDRAWN，解除当日锁定；已撤回时幂等返回。 */
    public WishMenuResp withdraw(WishWithdrawReq req) {
        LoginUser ctx = requireRole("CHILD");
        validate(req);
        Long childId = ctx.getUserId();
        FamilyMember member = authorization.lockBoundChild(childId);
        var consent = authorization.requireConsent(member);
        ChildProfile profile = requireProfile(member, true);
        requireMenuDate(req.getMenuDate());
        WishMenu menu = lockMenu(childId, req.getMenuDate());
        if (menu == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        if (STATUS_WITHDRAWN.equals(menu.getStatus())) {
            return detail(member, profile, childId, req.getMenuDate());
        }
        if (menu.getVersion() == null || !menu.getVersion().equals(req.getExpectedVersion())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        int before = menu.getVersion();
        WishMenu update = new WishMenu();
        update.setStatus(STATUS_WITHDRAWN);
        update.setVersion(Math.incrementExact(before));
        if (wishMenus.update(update, new UpdateWrapper<WishMenu>().eq("id", menu.getId())
                .eq("version", before)) != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        audit.record("WISH_WITHDRAW", ctx.getUserId(), member.getFamilyId(), "WISH_MENU", menu.getId(), null,
                "consentId=" + consent.getId() + ";menuDate=" + req.getMenuDate());
        return detail(member, profile, childId, req.getMenuDate());
    }

    // ==================== 家长：查看 ====================

    /** 家长查看某个孩子某日的心愿菜单（通知点击后的落地视图）。 */
    public WishMenuResp parentMenu(Long childId, LocalDate menuDate) {
        requireRole("PARENT");
        positive(childId);
        FamilyMember member = authorization.boundChild(childId);
        authorization.requireParent(member.getFamilyId());
        authorization.requireConsentReadOnly(member);
        ChildProfile profile = requireProfile(member, false);
        return detail(member, profile, childId, menuDate);
    }

    /** 家长按区间查看孩子的心愿菜单提交记录（≤31 天，过期照常返回并标 expired）。 */
    public WishMenuListResp parentList(Long childId, LocalDate from, LocalDate to) {
        requireRole("PARENT");
        positive(childId);
        if (from == null || to == null || from.isAfter(to)
                || ChronoUnit.DAYS.between(from, to) + 1 > LIST_MAX_DAYS) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        FamilyMember member = authorization.boundChild(childId);
        authorization.requireParent(member.getFamilyId());
        authorization.requireConsentReadOnly(member);
        ChildProfile profile = requireProfile(member, false);
        LocalDate today = time.today();
        SettingView setting = effectiveSetting(member.getFamilyId(), false);
        List<WishMenu> menus = wishMenus.selectList(new QueryWrapper<WishMenu>().eq("child_id", childId)
                .between("menu_date", from, to).orderByDesc("menu_date"));
        Map<LocalDate, List<ChildWantEat>> rowsByDate = new HashMap<>();
        for (ChildWantEat row : wantEatRows(childId, from, to)) {
            rowsByDate.computeIfAbsent(row.getMenuDate(), date -> new ArrayList<>()).add(row);
        }
        List<WishMenuListResp.Entry> items = new ArrayList<>();
        for (WishMenu menu : menus) {
            List<ChildWantEat> rows = rowsByDate.getOrDefault(menu.getMenuDate(), List.of());
            Map<String, Dish> dishByRef = loadDishes(refsOf(rows), member.getFamilyId());
            Map<Long, String> categoryNames = categoryNamesOf(dishByRef.values());
            List<WishMenuResp.Item> submitted = items(rows, dishByRef, categoryNames, profile, menu.getId()).stream()
                    .filter(WishMenuResp.Item::isSubmitted).toList();
            items.add(WishMenuListResp.Entry.builder().menuId(menu.getId()).menuDate(menu.getMenuDate())
                    .status(menu.getStatus()).dishCount(menu.getDishCount() == null ? 0 : menu.getDishCount())
                    .maxDishes(menu.getMaxDishes() == null ? setting.maxDishes() : menu.getMaxDishes())
                    .submitTime(menu.getSubmitTime()).expired(menu.getMenuDate().isBefore(today))
                    .items(submitted).build());
        }
        audit.record("WISH_MENU_LIST_QUERY", UserContext.userId(), member.getFamilyId(), "CHILD", childId, null,
                "from=" + from + ";to=" + to);
        return WishMenuListResp.builder().childId(childId).from(from).to(to).today(today).items(items).build();
    }

    // ==================== 内部：详情组装 ====================

    private WishMenuResp detail(FamilyMember member, ChildProfile profile, Long childId, LocalDate menuDate) {
        requireMenuDate(menuDate);
        LocalDate today = time.today();
        SettingView setting = effectiveSetting(member.getFamilyId(), false);
        WishMenu menu = selectMenu(childId, menuDate, false);
        boolean locked = menu != null && STATUS_SUBMITTED.equals(menu.getStatus());
        List<ChildWantEat> rows = wantEatRows(childId, menuDate);
        Map<String, Dish> dishByRef = loadDishes(refsOf(rows), member.getFamilyId());
        Map<Long, String> categoryNames = categoryNamesOf(dishByRef.values());
        List<WishMenuResp.Item> items = items(rows, dishByRef, categoryNames, profile,
                menu == null ? 0L : menu.getId());
        int submittedCount = (int) items.stream().filter(WishMenuResp.Item::isSubmitted).count();
        boolean inWindow = !menuDate.isBefore(today) && !menuDate.isAfter(today.plusDays(WINDOW_DAYS));
        boolean canEdit = setting.enabled() && inWindow && !locked;
        return WishMenuResp.builder().menuId(menu == null ? null : menu.getId()).childId(childId)
                .menuDate(menuDate).today(today).status(menu == null ? STATUS_NONE : menu.getStatus())
                .enabled(setting.enabled()).maxDishes(setting.maxDishes()).dishCount(items.size())
                .submittedCount(submittedCount).canEdit(canEdit).canSubmit(canEdit && !items.isEmpty())
                .locked(locked).version(menu == null || menu.getVersion() == null ? 0 : menu.getVersion())
                .submitTime(menu == null ? null : menu.getSubmitTime()).items(items).build();
    }

    /** 候选池：按 (type,id) 去重，合并该菜当天出现的餐次；submitted 依据 wish_id 是否指向该心愿单。 */
    private List<WishMenuResp.Item> items(List<ChildWantEat> rows, Map<String, Dish> dishByRef,
            Map<Long, String> categoryNames, ChildProfile profile, Long wishId) {
        Map<String, WishMenuResp.Item> dedup = new LinkedHashMap<>();
        Map<String, Set<String>> mealTypes = new LinkedHashMap<>();
        for (ChildWantEat row : rows) {
            String key = row.getDishType() + ":" + row.getDishId();
            mealTypes.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(row.getMealType());
            Dish dish = dishByRef.get(key);
            WishMenuResp.Item.ItemBuilder builder = WishMenuResp.Item.builder()
                    .type(row.getDishType()).id(row.getDishId())
                    .submitted(wishId != null && wishId > 0
                            && row.getWishId() != null && wishId.equals(row.getWishId()))
                    .missing(dish == null)
                    .name(dish == null ? null : dish.getName())
                    .imageUrl(dish == null ? null : dish.getImageUrl())
                    .categoryId(dish == null ? null : dish.getCategoryId())
                    .categoryName(dish == null ? null : categoryNames.get(dish.getCategoryId()))
                    .spiceLevel(dish == null ? null : dish.getSpiceLevel())
                    .status(dish == null ? null : dish.getStatus())
                    .safetyStatus(dish == null ? null : safetyStatus(dish, profile))
                    .allergyConflict(dish != null && allergyConflict(dish, profile))
                    .disliked(dish != null && isDisliked(dish, profile))
                    .selectable(dish != null && "DECLARED".equals(safetyStatus(dish, profile)));
            WishMenuResp.Item existing = dedup.get(key);
            if (existing == null) {
                dedup.put(key, builder.build());
            } else if (!existing.isSubmitted()) {
                dedup.put(key, builder.build());
            }
        }
        List<WishMenuResp.Item> items = new ArrayList<>();
        for (Map.Entry<String, WishMenuResp.Item> entry : dedup.entrySet()) {
            WishMenuResp.Item item = entry.getValue();
            item.setMealTypes(new ArrayList<>(mealTypes.getOrDefault(entry.getKey(), Set.of())));
            items.add(item);
        }
        return items;
    }

    // ==================== 内部：读写与校验 ====================

    /** 写入前置校验：功能开关 + 可编辑窗口；返回生效设置（锁定判定见 {@link #requireUnlocked}）。 */
    private SettingView requireWritable(LocalDate menuDate, Long familyId) {
        requireMenuDate(menuDate);
        SettingView setting = effectiveSetting(familyId, false);
        if (!setting.enabled()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "家庭已关闭心愿菜单");
        }
        LocalDate today = time.today();
        if (menuDate.isBefore(today) || menuDate.isAfter(today.plusDays(WINDOW_DAYS))) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "只能为今天起 7 天内的日期创建心愿菜单");
        }
        return setting;
    }

    /**
     * 当日已提交（锁定）时拒绝任何候选池调整。
     * 仅用于无版本号的标记接口；带 {@code expectedVersion} 的提交/撤回在锁内按"版本 → 状态"顺序判定。
     */
    private void requireUnlocked(Long childId, LocalDate menuDate) {
        if (countSubmitted(childId, menuDate) > 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "心愿菜单已提交，请先撤回再调整");
        }
    }

    /** 当日是否已提交（家长/儿童读接口与 markFavorite 锁定判定共用同一口径）。 */
    private long countSubmitted(Long childId, LocalDate menuDate) {
        return wishMenus.selectCount(new QueryWrapper<WishMenu>().eq("child_id", childId)
                .eq("menu_date", menuDate).eq("status", STATUS_SUBMITTED));
    }

    private void requireMenuDate(LocalDate date) {
        if (date == null || date.getYear() < 1000 || date.getYear() > 9999) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
    }

    /** 目录浏览允许过去日期（只读回看），但不允许超出可编辑窗口之外的未来日期。 */
    private void requireBrowsableDate(LocalDate date) {
        requireMenuDate(date);
        if (date.isAfter(time.today().plusDays(WINDOW_DAYS))) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
    }

    private SettingView effectiveSetting(Long familyId, boolean lock) {
        FamilySetting row = selectSetting(familyId, lock);
        if (row == null || row.getWishMenuMaxDishes() == null || row.getWishMenuEnabled() == null) {
            return new SettingView(DEFAULT_MAX_DISHES, true);
        }
        return new SettingView(row.getWishMenuMaxDishes(), row.getWishMenuEnabled() == 1);
    }

    private FamilySetting selectSetting(Long familyId, boolean lock) {
        QueryWrapper<FamilySetting> query = new QueryWrapper<FamilySetting>().eq("family_id", familyId);
        return settings.selectOne(lock ? query.last("FOR UPDATE") : query);
    }

    private WishSettingResp settingResponse(SettingView setting) {
        FamilySetting row = selectSetting(currentFamilyId(), false);
        return WishSettingResp.builder().maxDishes(setting.maxDishes()).enabled(setting.enabled())
                .version(row == null || row.getVersion() == null ? 0 : row.getVersion()).build();
    }

    private WishMenu selectMenu(Long childId, LocalDate menuDate, boolean lock) {
        QueryWrapper<WishMenu> query = new QueryWrapper<WishMenu>().eq("child_id", childId)
                .eq("menu_date", menuDate);
        return wishMenus.selectOne(lock ? query.last("FOR UPDATE") : query);
    }

    private WishMenu lockMenu(Long childId, LocalDate menuDate) {
        return selectMenu(childId, menuDate, true);
    }

    /** 收编：先把该日全部明细解除收编，再把勾选中的行指向本心愿单；返回实际收编行数（去重后）。 */
    private int collect(FamilyMember member, LocalDate menuDate, Long wishId, List<WishSubmitReq.Ref> refs) {
        wantEats.update(null, new UpdateWrapper<ChildWantEat>().eq("child_id", member.getUserId())
                .eq("menu_date", menuDate).eq("delete_at", 0L).set("wish_id", 0L));
        int collected = 0;
        for (WishSubmitReq.Ref ref : refs) {
            int updated = wantEats.update(null, new UpdateWrapper<ChildWantEat>()
                    .eq("child_id", member.getUserId()).eq("menu_date", menuDate)
                    .eq("delete_at", 0L).eq("dish_type", ref.getType()).eq("dish_id", ref.getId())
                    .set("wish_id", wishId));
            if (updated > 0) {
                collected++;
            }
        }
        return collected;
    }

    private List<WishSubmitReq.Ref> dedupRefs(List<WishSubmitReq.Ref> refs) {
        Map<String, WishSubmitReq.Ref> dedup = new LinkedHashMap<>();
        for (WishSubmitReq.Ref ref : refs) {
            if (ref == null || ref.getType() == null || !TYPES.contains(ref.getType()) || ref.getId() == null
                    || ref.getId() <= 0) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
            }
            dedup.putIfAbsent(ref.getType() + ":" + ref.getId(), ref);
        }
        return new ArrayList<>(dedup.values());
    }

    private List<ChildWantEat> wantEatRows(Long childId, LocalDate menuDate) {
        return wantEats.selectList(new QueryWrapper<ChildWantEat>().eq("child_id", childId)
                .eq("menu_date", menuDate).orderByAsc("id"));
    }

    private List<ChildWantEat> wantEatRows(Long childId, LocalDate from, LocalDate to) {
        return wantEats.selectList(new QueryWrapper<ChildWantEat>().eq("child_id", childId)
                .between("menu_date", from, to).orderByAsc("id"));
    }

    private Set<Long> markedDishIds(Long childId, LocalDate menuDate, String type) {
        Set<Long> ids = new HashSet<>();
        for (ChildWantEat row : wantEats.selectList(new QueryWrapper<ChildWantEat>()
                .eq("child_id", childId).eq("menu_date", menuDate).eq("dish_type", type))) {
            ids.add(row.getDishId());
        }
        return ids;
    }

    private List<DishRef> refsOf(Collection<ChildWantEat> rows) {
        List<DishRef> refs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ChildWantEat row : rows) {
            String key = row.getDishType() + ":" + row.getDishId();
            if (!seen.add(key)) {
                continue;
            }
            refs.add(ref(row.getDishType(), row.getDishId()));
        }
        return refs;
    }

    private DishRef ref(String type, Long id) {
        DishRef ref = new DishRef();
        ref.setType(type);
        ref.setId(id);
        return ref;
    }

    /**
     * 按 DishRef 批量取菜品（含已下架，用于 missing/置灰展示）。FAMILY 强制 family_id 隔离。
     * 与 CatalogService.visibleDishes 同款语义，但目录/候选池需要一次批量取，避免逐条查询。
     */
    private Map<String, Dish> loadDishes(Collection<DishRef> refs, Long familyId) {
        Map<String, Dish> visible = new HashMap<>();
        List<Long> presetIds = refs.stream().filter(r -> "PRESET".equals(r.getType()))
                .map(DishRef::getId).distinct().sorted().toList();
        if (!presetIds.isEmpty()) {
            for (Dish dish : dishes.selectBatchIds(presetIds)) {
                visible.put("PRESET:" + dish.getId(), dish);
            }
        }
        List<Long> familyIds = refs.stream().filter(r -> "FAMILY".equals(r.getType()))
                .map(DishRef::getId).distinct().sorted().toList();
        if (!familyIds.isEmpty()) {
            List<FamilyDish> rows = familyDishes.selectList(new QueryWrapper<FamilyDish>()
                    .in("id", familyIds).eq(familyId != null, "family_id", familyId));
            for (FamilyDish fd : rows) {
                visible.put("FAMILY:" + fd.getId(), toDish(fd));
            }
        }
        return visible;
    }

    private QueryWrapper<FamilyDish> familyDishQuery(Long familyId, Long categoryId, String keyword) {
        QueryWrapper<FamilyDish> query = new QueryWrapper<FamilyDish>().eq("family_id", familyId)
                .eq("status", "ON_SALE");
        if (categoryId != null) {
            query.eq("category_id", categoryId);
        }
        if (keyword != null) {
            query.like("name", keyword);
        }
        return query;
    }

    private QueryWrapper<Dish> presetQuery(Long categoryId, String keyword) {
        QueryWrapper<Dish> query = new QueryWrapper<Dish>().eq("status", "ON_SALE");
        if (categoryId != null) {
            query.eq("category_id", categoryId);
        }
        if (keyword != null) {
            query.like("name", keyword);
        }
        return query;
    }

    private WishDishResp dishResponse(Dish dish, String type, Map<Long, String> categoryNames,
            ChildProfile profile, boolean marked) {
        String safety = safetyStatus(dish, profile);
        return WishDishResp.builder().dishId(dish.getId()).type(type).categoryId(dish.getCategoryId())
                .categoryName(categoryNames.get(dish.getCategoryId())).name(dish.getName())
                .imageUrl(dish.getImageUrl())
                .virtualPrice(dish.getVirtualPrice() == null ? null : dish.getVirtualPrice().setScale(2).toPlainString())
                .spiceLevel(dish.getSpiceLevel()).allergenStatus(dish.getAllergenStatus()).status(dish.getStatus())
                .safetyStatus(safety).allergyConflict(allergyConflict(dish, profile)).disliked(isDisliked(dish, profile))
                .selectable("DECLARED".equals(safety)).marked(marked).build();
    }

    /** 家庭私有菜品 → Dish（复制字段，统一安全校验口径，不持久化）。 */
    private Dish toDish(FamilyDish fd) {
        Dish dish = new Dish();
        dish.setId(fd.getId());
        dish.setCategoryId(fd.getCategoryId());
        dish.setName(fd.getName());
        dish.setImageUrl(fd.getImageUrl());
        dish.setVirtualPrice(fd.getVirtualPrice());
        dish.setCalories(fd.getCalories());
        dish.setTags(fd.getTags());
        dish.setAllergens(fd.getAllergens());
        dish.setAllergenStatus(fd.getAllergenStatus());
        dish.setSpiceLevel(fd.getSpiceLevel());
        dish.setStatus(fd.getStatus());
        return dish;
    }

    /**
     * 安全判定：与 {@code CatalogService.safetyStatus} 完全同口径（fail closed）。
     * 此处保留同款实现而非跨类调用私有方法，避免改动已通过回归的 CatalogService 可见性。
     */
    private String safetyStatus(Dish dish, ChildProfile profile) {
        if (!"ON_SALE".equals(dish.getStatus())) {
            return "OFF_SALE";
        }
        if (!"DECLARED".equals(dish.getAllergenStatus()) || !validAllergens(dish.getAllergens())
                || !validAllergens(profile.getAllergies())) {
            return "UNKNOWN";
        }
        return allergyConflict(dish, profile) ? "ALLERGY_CONFLICT" : "DECLARED";
    }

    private boolean allergyConflict(Dish dish, ChildProfile profile) {
        return dish.getAllergens() != null && profile.getAllergies() != null
                && dish.getAllergens().stream().anyMatch(profile.getAllergies()::contains);
    }

    private boolean isDisliked(Dish dish, ChildProfile profile) {
        return profile.getDislikes() != null && profile.getDislikes().stream()
                .filter(Objects::nonNull).filter(value -> !value.isBlank())
                .anyMatch(value -> dish.getName().contains(value)
                        || dish.getTags() != null && dish.getTags().contains(value));
    }

    private boolean validAllergens(List<String> allergens) {
        return policy.getCatalogReference() != null && !policy.getCatalogReference().isBlank()
                && allergens != null && allergens.size() <= 20
                && allergens.stream().allMatch(value -> value != null && !value.isBlank()
                && value.length() <= 64 && policy.getAllergens().contains(value))
                && new HashSet<>(allergens).size() == allergens.size();
    }

    private Map<Long, String> categoryNameMap(Collection<Long> categoryIds) {
        List<Long> ids = categoryIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (DishCategory category : categories.selectBatchIds(ids)) {
            names.put(category.getId(), category.getName());
        }
        return names;
    }

    private Map<Long, String> categoryNamesOf(Collection<Dish> dishList) {
        return categoryNameMap(dishList.stream().map(Dish::getCategoryId).toList());
    }

    /** 儿童档案必须已补全（与 CatalogService.completeProfile 同口径）。 */
    private ChildProfile requireProfile(FamilyMember member, boolean lock) {
        QueryWrapper<ChildProfile> query = new QueryWrapper<ChildProfile>()
                .eq("family_id", member.getFamilyId()).eq("user_id", member.getUserId());
        ChildProfile profile = profiles.selectOne(lock ? query.last("FOR UPDATE") : query);
        if (profile == null || !"COMPLETE".equals(profile.getProfileStatus())) {
            throw new BizException(ResultCode.E002_PROFILE_INCOMPLETE);
        }
        return profile;
    }

    private LoginUser requireRole(String... roles) {
        LoginUser ctx = UserContext.get();
        if (ctx == null || ctx.getRole() == null || !Set.of(roles).contains(ctx.getRole())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        return ctx;
    }

    private Long currentFamilyId() {
        Long familyId = UserContext.get() == null ? null : UserContext.get().firstFamilyId();
        if (familyId == null || familyId <= 0) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        return familyId;
    }

    private void validate(Object value) {
        var violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
    }

    private void positive(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
    }

    /** 生效设置视图：maxDishes + enabled（无行即默认值）。 */
    private record SettingView(int maxDishes, boolean enabled) {
    }
}
