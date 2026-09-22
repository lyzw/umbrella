package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.constant.AdminResource;
import cn.studykid.growthplanet.common.context.AdminUserContext;
import cn.studykid.growthplanet.common.context.RequestContext;
import cn.studykid.growthplanet.common.enums.AdminAction;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.AdminConfirmReviewReq;
import cn.studykid.growthplanet.dto.request.AdminMedalReissueReq;
import cn.studykid.growthplanet.dto.response.AdminAllowanceLogResp;
import cn.studykid.growthplanet.dto.response.AdminApprovalRowResp;
import cn.studykid.growthplanet.dto.response.AdminChoreInstanceResp;
import cn.studykid.growthplanet.dto.response.AdminChorePageResp;
import cn.studykid.growthplanet.dto.response.AdminCheckRecordResp;
import cn.studykid.growthplanet.dto.response.AdminConfirmDetailResp;
import cn.studykid.growthplanet.dto.response.AdminConfirmRowResp;
import cn.studykid.growthplanet.dto.response.AdminMedalAwardResp;
import cn.studykid.growthplanet.dto.response.AdminWantEatResp;
import cn.studykid.growthplanet.dto.response.AdminWalletResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.entity.AllowanceLog;
import cn.studykid.growthplanet.entity.AllowanceRule;
import cn.studykid.growthplanet.entity.CheckRecord;
import cn.studykid.growthplanet.entity.ChoreInstance;
import cn.studykid.growthplanet.entity.ConfirmApproval;
import cn.studykid.growthplanet.entity.Dish;
import cn.studykid.growthplanet.entity.FamilyDish;
import cn.studykid.growthplanet.entity.MedalAward;
import cn.studykid.growthplanet.entity.MedalDefinition;
import cn.studykid.growthplanet.entity.MenuConfirm;
import cn.studykid.growthplanet.entity.MenuItem;
import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.entity.Wallet;
import cn.studykid.growthplanet.entity.ChildWantEat;
import cn.studykid.growthplanet.mapper.AllowanceLogMapper;
import cn.studykid.growthplanet.mapper.AllowanceRuleMapper;
import cn.studykid.growthplanet.mapper.CheckRecordMapper;
import cn.studykid.growthplanet.mapper.ChoreInstanceMapper;
import cn.studykid.growthplanet.mapper.ConfirmApprovalMapper;
import cn.studykid.growthplanet.mapper.DishMapper;
import cn.studykid.growthplanet.mapper.FamilyDishMapper;
import cn.studykid.growthplanet.mapper.MedalAwardMapper;
import cn.studykid.growthplanet.mapper.MedalDefinitionMapper;
import cn.studykid.growthplanet.mapper.MenuConfirmMapper;
import cn.studykid.growthplanet.mapper.MenuItemMapper;
import cn.studykid.growthplanet.mapper.UserMapper;
import cn.studykid.growthplanet.mapper.WalletMapper;
import cn.studykid.growthplanet.mapper.ChildWantEatMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * M4 业务数据管理（里程碑 A 批次 3）：查询/导出为主，人工干预仅 2 项。
 *
 * <p>权限口径（详设 §3.4 M4 行）：想吃/确认单/审批记录/钱包 查看=SA,OP,DC,CP（SA 旁路）；
 * 导出=SA,OP,DC；家务健康/勋章发放 查看=SA,OP,DC；超额复核=确认单 approve；勋章补发=勋章发放 edit。</p>
 *
 * <p>两条人工干预红线：
 * <ul>
 *   <li>超额确认单复核（D3 两级简化）：结论仅落 sys_audit_log，<b>不写</b> life_confirm_approval
 *       （uk_approval_confirm 唯一键由 C 端家长审批独占）、不改状态、不动余额；</li>
 *   <li>勋章补发：走 {@code life_medal_award} 幂等键 (definition_id, child_id, ref_id)，ref_id=0
 *       为人工通道（每孩子每勋章一次），不触发通知、不碰钱包。</li>
 * </ul></p>
 *
 * <p>脱敏口径：孩子昵称默认脱敏（张*）；本批数据源无手机号明文，无需号码脱敏。</p>
 */
@Service
@Transactional(readOnly = true)
public class AdminBizDataService {

    private static final Set<String> MEALS = Set.of("BREAKFAST", "LUNCH", "DINNER");
    private static final Set<String> CONFIRM_STATUSES = Set.of("PENDING", "REJECTED", "CANCELLED", "COMPLETED");
    private static final Set<String> CHORE_STATUSES = Set.of("CLAIMED", "SUBMITTED", "CONFIRMED", "REJECTED");
    private static final Set<String> REVIEW_DECISIONS = Set.of("RESOLVED", "FOLLOW_UP");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_LIMIT = 10_000;

    private final ChildWantEatMapper wantEats;
    private final MenuConfirmMapper confirms;
    private final MenuItemMapper menuItems;
    private final ConfirmApprovalMapper approvals;
    private final WalletMapper wallets;
    private final AllowanceRuleMapper allowanceRules;
    private final AllowanceLogMapper allowanceLogs;
    private final ChoreInstanceMapper choreInstances;
    private final CheckRecordMapper checkRecords;
    private final MedalAwardMapper medalAwards;
    private final MedalDefinitionMapper medalDefs;
    private final DishMapper dishes;
    private final FamilyDishMapper familyDishes;
    private final UserMapper users;
    private final AuditService audit;

    public AdminBizDataService(ChildWantEatMapper wantEats, MenuConfirmMapper confirms,
            MenuItemMapper menuItems, ConfirmApprovalMapper approvals, WalletMapper wallets,
            AllowanceRuleMapper allowanceRules, AllowanceLogMapper allowanceLogs,
            ChoreInstanceMapper choreInstances, CheckRecordMapper checkRecords,
            MedalAwardMapper medalAwards, MedalDefinitionMapper medalDefs, DishMapper dishes,
            FamilyDishMapper familyDishes, UserMapper users, AuditService audit) {
        this.wantEats = wantEats;
        this.confirms = confirms;
        this.menuItems = menuItems;
        this.approvals = approvals;
        this.wallets = wallets;
        this.allowanceRules = allowanceRules;
        this.allowanceLogs = allowanceLogs;
        this.choreInstances = choreInstances;
        this.checkRecords = checkRecords;
        this.medalAwards = medalAwards;
        this.medalDefs = medalDefs;
        this.dishes = dishes;
        this.familyDishes = familyDishes;
        this.users = users;
        this.audit = audit;
    }

    // ==================== 每日想吃 ====================

    public PageResp<AdminWantEatResp> listWantEat(Long familyId, Long childId, String mealType,
            LocalDate from, LocalDate to, int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.WANT_EAT, AdminAction.VIEW.code());
        Page p = normalizePage(page, pageSize);
        validateRange(from, to);
        if (mealType != null && !MEALS.contains(mealType)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        QueryWrapper<ChildWantEat> query = new QueryWrapper<ChildWantEat>()
                .eq(familyId != null, "family_id", familyId)
                .eq(childId != null, "child_id", childId)
                .eq(mealType != null, "meal_type", mealType)
                .ge(from != null, "menu_date", from)
                .le(to != null, "menu_date", to);
        long total = wantEats.selectCount(query);
        List<ChildWantEat> rows = wantEats.selectList(query.orderByDesc("menu_date").orderByDesc("id")
                .last("LIMIT " + p.offset() + ", " + p.pageSize()));
        Map<Long, String> names = childNames(rows.stream().map(ChildWantEat::getChildId).toList());
        Map<String, String> dishNames = resolveDishNames(rows);
        List<AdminWantEatResp> items = rows.stream().map(w -> AdminWantEatResp.builder()
                .id(w.getId()).childId(w.getChildId())
                .childName(maskName(names.get(w.getChildId()), w.getChildId()))
                .familyId(w.getFamilyId()).menuDate(w.getMenuDate()).mealType(w.getMealType())
                .sourceType(w.getSourceType()).dishType(w.getDishType()).dishId(w.getDishId())
                .dishName(dishNames.get(dishKey(w.getDishType(), w.getDishId())))
                .status(w.getStatus()).wishId(w.getWishId()).createTime(w.getCreateTime()).build())
                .toList();
        return new PageResp<>(items, total, p.page(), p.pageSize());
    }

    // ==================== 确认单 ====================

    public PageResp<AdminConfirmRowResp> listConfirmations(Long familyId, Long childId, String status,
            Boolean overLimit, LocalDate from, LocalDate to, int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.CONFIRM, AdminAction.VIEW.code());
        Page p = normalizePage(page, pageSize);
        validateRange(from, to);
        if (status != null && !CONFIRM_STATUSES.contains(status)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        QueryWrapper<MenuConfirm> query = new QueryWrapper<MenuConfirm>()
                .eq(familyId != null, "family_id", familyId)
                .eq(childId != null, "child_id", childId)
                .eq(status != null, "status", status)
                .eq(overLimit != null, "is_over_limit", overLimit ? 1 : 0)
                .ge(from != null, "menu_date", from)
                .le(to != null, "menu_date", to);
        long total = confirms.selectCount(query);
        List<MenuConfirm> rows = confirms.selectList(query.orderByDesc("id")
                .last("LIMIT " + p.offset() + ", " + p.pageSize()));
        Map<Long, String> names = childNames(rows.stream().map(MenuConfirm::getChildId).toList());
        List<AdminConfirmRowResp> items = rows.stream().map(c -> AdminConfirmRowResp.builder()
                .id(c.getId()).confirmNo(c.getConfirmNo()).familyId(c.getFamilyId())
                .childId(c.getChildId())
                .childName(maskName(names.get(c.getChildId()), c.getChildId()))
                .menuDate(c.getMenuDate()).mealType(c.getMealType()).totalAmount(c.getTotalAmount())
                .status(c.getStatus()).isOverLimit(Boolean.TRUE.equals(c.getIsOverLimit()))
                .version(c.getVersion()).submitTime(c.getSubmitTime()).build())
                .toList();
        return new PageResp<>(items, total, p.page(), p.pageSize());
    }

    public AdminConfirmDetailResp getConfirmation(Long confirmId) {
        AdminUserContext.requirePerm(AdminResource.CONFIRM, AdminAction.VIEW.code());
        MenuConfirm confirm = confirms.selectById(confirmId);
        if (confirm == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        List<MenuItem> lines = menuItems.selectList(
                new QueryWrapper<MenuItem>().eq("confirm_id", confirmId).orderByAsc("id"));
        return detailResp(confirm, lines);
    }

    private AdminConfirmDetailResp detailResp(MenuConfirm confirm, List<MenuItem> lines) {
        String childName = childNames(List.of(confirm.getChildId()))
                .getOrDefault(confirm.getChildId(), "孩子#" + confirm.getChildId());
        return AdminConfirmDetailResp.builder()
                .id(confirm.getId()).confirmNo(confirm.getConfirmNo()).familyId(confirm.getFamilyId())
                .childId(confirm.getChildId()).childName(childName)
                .menuDate(confirm.getMenuDate()).mealType(confirm.getMealType())
                .totalAmount(confirm.getTotalAmount()).status(confirm.getStatus())
                .isOverLimit(Boolean.TRUE.equals(confirm.getIsOverLimit()))
                .version(confirm.getVersion()).estimatedBalance(confirm.getEstimatedBalance())
                .completedBalance(confirm.getCompletedBalance()).remark(confirm.getRemark())
                .submitTime(confirm.getSubmitTime())
                .items(lines.stream().map(i -> AdminConfirmDetailResp.Item.builder()
                        .dishId(i.getDishId()).sourceType(i.getSourceType() == null ? "PRESET" : i.getSourceType())
                        .dishName(i.getDishName()).quantity(i.getQuantity()).unitPrice(i.getUnitPrice())
                        .subtotal(i.getSubtotal()).note(i.getNote()).build()).toList())
                .build();
    }

    /**
     * 超额确认单人工复核（D3 两级简化）：结论仅落 sys_audit_log（action=CONFIRM_ADMIN_REVIEW）。
     * 仅 is_over_limit=1 的确认单可复核；不改状态、不写审批表、不动钱包。
     */
    @Transactional
    public AdminConfirmDetailResp reviewConfirmation(Long confirmId, AdminConfirmReviewReq req) {
        AdminUserContext.requirePerm(AdminResource.CONFIRM, AdminAction.APPROVE.code());
        if (!REVIEW_DECISIONS.contains(req.getDecision())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        MenuConfirm confirm = confirms.selectById(confirmId);
        if (confirm == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(confirm.getIsOverLimit())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "仅超额确认单需要人工复核");
        }
        audit.record("CONFIRM_ADMIN_REVIEW", AdminUserContext.adminId(), confirm.getFamilyId(),
                "CONFIRM", confirmId, RequestContext.ip(),
                "decision=" + req.getDecision() + ";note=" + req.getNote().trim()
                        + ";amount=" + confirm.getTotalAmount() + ";confirmNo=" + confirm.getConfirmNo());
        return getConfirmation(confirmId);
    }

    // ==================== 审批记录 ====================

    public PageResp<AdminApprovalRowResp> listConfirmApprovals(Long confirmId, String action,
            int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.APPROVAL, AdminAction.VIEW.code());
        Page p = normalizePage(page, pageSize);
        QueryWrapper<ConfirmApproval> query = new QueryWrapper<ConfirmApproval>()
                .eq(confirmId != null, "confirm_id", confirmId)
                .eq(action != null && !action.isBlank(), "action", action == null ? null : action.trim());
        long total = approvals.selectCount(query);
        List<ConfirmApproval> rows = approvals.selectList(query.orderByDesc("id")
                .last("LIMIT " + p.offset() + ", " + p.pageSize()));
        Map<Long, String> confirmNos = confirmNos(rows.stream().map(ConfirmApproval::getConfirmId).toList());
        List<AdminApprovalRowResp> items = rows.stream().map(a -> AdminApprovalRowResp.builder()
                .id(a.getId()).confirmId(a.getConfirmId())
                .confirmNo(confirmNos.get(a.getConfirmId())).parentId(a.getParentId())
                .action(a.getAction()).beforeStatus(a.getBeforeStatus()).afterStatus(a.getAfterStatus())
                .isOverLimit(Boolean.TRUE.equals(a.getIsOverLimit())).reason(a.getReason())
                .createTime(a.getCreateTime()).build())
                .toList();
        return new PageResp<>(items, total, p.page(), p.pageSize());
    }

    // ==================== 钱包与流水 ====================

    public PageResp<AdminWalletResp> listWallets(Long familyId, Long childId, int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.WALLET, AdminAction.VIEW.code());
        Page p = normalizePage(page, pageSize);
        QueryWrapper<Wallet> query = new QueryWrapper<Wallet>()
                .eq(familyId != null, "family_id", familyId)
                .eq(childId != null, "child_id", childId);
        long total = wallets.selectCount(query);
        List<Wallet> rows = wallets.selectList(query.orderByDesc("id")
                .last("LIMIT " + p.offset() + ", " + p.pageSize()));
        Map<Long, String> names = childNames(rows.stream().map(Wallet::getChildId).toList());
        // 额度规则按孩子批量取（每孩子一行最新规则；规则表无唯一键约束，取 id 最大行）。
        Set<Long> childIds = rows.stream().map(Wallet::getChildId).collect(Collectors.toCollection(HashSet::new));
        Map<Long, AllowanceRule> rules = childIds.isEmpty() ? Map.of()
                : allowanceRules.selectList(new QueryWrapper<AllowanceRule>().in("child_id", childIds)
                                .orderByAsc("id")).stream()
                        .collect(Collectors.toMap(AllowanceRule::getChildId, Function.identity(), (a, b) -> b));
        List<AdminWalletResp> items = rows.stream().map(w -> {
            AllowanceRule rule = rules.get(w.getChildId());
            return AdminWalletResp.builder().id(w.getId()).childId(w.getChildId())
                    .childName(maskName(names.get(w.getChildId()), w.getChildId()))
                    .familyId(w.getFamilyId()).balance(w.getBalance()).status(w.getStatus())
                    .singleLimit(rule == null ? null : rule.getSingleLimit())
                    .dailyLimit(rule == null ? null : rule.getDailyLimit())
                    .dailyUsed(rule == null ? null : rule.getDailyUsed())
                    .weeklyLimit(rule == null ? null : rule.getWeeklyLimit())
                    .weeklyUsed(rule == null ? null : rule.getWeeklyUsed())
                    .updateTime(w.getUpdateTime()).build();
        }).toList();
        return new PageResp<>(items, total, p.page(), p.pageSize());
    }

    public PageResp<AdminAllowanceLogResp> listAllowanceLogs(Long familyId, Long childId, String transType,
            LocalDate from, LocalDate to, int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.WALLET, AdminAction.VIEW.code());
        Page p = normalizePage(page, pageSize);
        validateRange(from, to);
        QueryWrapper<AllowanceLog> query = new QueryWrapper<AllowanceLog>()
                .eq(familyId != null, "family_id", familyId)
                .eq(childId != null, "child_id", childId)
                .eq(transType != null && !transType.isBlank(), "trans_type",
                        transType == null ? null : transType.trim())
                .ge(from != null, "usage_date", from)
                .le(to != null, "usage_date", to);
        long total = allowanceLogs.selectCount(query);
        List<AllowanceLog> rows = allowanceLogs.selectList(query.orderByDesc("id")
                .last("LIMIT " + p.offset() + ", " + p.pageSize()));
        Map<Long, String> names = childNames(rows.stream().map(AllowanceLog::getChildId).toList());
        List<AdminAllowanceLogResp> items = rows.stream().map(l -> AdminAllowanceLogResp.builder()
                .id(l.getId()).walletId(l.getWalletId()).childId(l.getChildId())
                .childName(maskName(names.get(l.getChildId()), l.getChildId()))
                .familyId(l.getFamilyId()).transType(l.getTransType()).scene(l.getScene())
                .refId(l.getRefId()).amount(l.getAmount()).balanceBefore(l.getBalanceBefore())
                .balanceAfter(l.getBalanceAfter()).usageDate(l.getUsageDate()).reason(l.getReason())
                .createTime(l.getCreateTime()).build())
                .toList();
        return new PageResp<>(items, total, p.page(), p.pageSize());
    }

    // ==================== 家务健康 ====================

    public AdminChorePageResp listChoreInstances(Long familyId, Long childId, String status,
            LocalDate from, LocalDate to, int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.CHORE_HEALTH, AdminAction.VIEW.code());
        Page p = normalizePage(page, pageSize);
        validateRange(from, to);
        if (status != null && !CHORE_STATUSES.contains(status)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        QueryWrapper<ChoreInstance> query = choreQuery(familyId, childId, status, from, to);
        long total = choreInstances.selectCount(query);
        List<ChoreInstance> rows = choreInstances.selectList(
                new QueryWrapper<ChoreInstance>().eq(familyId != null, "family_id", familyId)
                        .eq(childId != null, "child_id", childId)
                        .eq(status != null, "status", status)
                        .ge(from != null, "claim_date", from)
                        .le(to != null, "claim_date", to)
                        .orderByDesc("id").last("LIMIT " + p.offset() + ", " + p.pageSize()));
        Map<Long, String> names = childNames(rows.stream().map(ChoreInstance::getChildId).toList());
        List<AdminChoreInstanceResp> items = rows.stream().map(c -> AdminChoreInstanceResp.builder()
                .id(c.getId()).taskId(c.getTaskId()).familyId(c.getFamilyId()).childId(c.getChildId())
                .childName(maskName(names.get(c.getChildId()), c.getChildId()))
                .status(c.getStatus()).claimDate(c.getClaimDate()).submitTime(c.getSubmitTime())
                .confirmTime(c.getConfirmTime()).rewardAmount(c.getRewardAmount())
                .rewardGranted(c.getRewardGranted()).medalCode(c.getMedalCode())
                .rejectReason(c.getRejectReason()).build())
                .toList();
        // 汇总按全量过滤集（不受分页影响）：逐状态 COUNT。
        Map<String, Long> counts = new HashMap<>();
        long confirmed = 0;
        for (String s : CHORE_STATUSES) {
            long n = choreInstances.selectCount(
                    choreQuery(familyId, childId, s, from, to));
            if (n > 0) {
                counts.put(s, n);
            }
            if ("CONFIRMED".equals(s)) {
                confirmed = n;
            }
        }
        double rate = total == 0 ? 0.0
                : BigDecimal.valueOf(confirmed * 100L).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                        .doubleValue();
        return AdminChorePageResp.builder().items(items).total(total).page(p.page()).pageSize(p.pageSize())
                .statusCounts(counts).completionRate(rate).build();
    }

    private QueryWrapper<ChoreInstance> choreQuery(Long familyId, Long childId, String status,
            LocalDate from, LocalDate to) {
        return new QueryWrapper<ChoreInstance>()
                .eq(familyId != null, "family_id", familyId)
                .eq(childId != null, "child_id", childId)
                .eq(status != null, "status", status)
                .ge(from != null, "claim_date", from)
                .le(to != null, "claim_date", to);
    }

    public PageResp<AdminCheckRecordResp> listCheckRecords(Long familyId, Long childId, Long itemId,
            LocalDate from, LocalDate to, int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.CHORE_HEALTH, AdminAction.VIEW.code());
        Page p = normalizePage(page, pageSize);
        validateRange(from, to);
        QueryWrapper<CheckRecord> query = new QueryWrapper<CheckRecord>()
                .eq(familyId != null, "family_id", familyId)
                .eq(childId != null, "child_id", childId)
                .eq(itemId != null, "item_id", itemId)
                .ge(from != null, "check_date", from)
                .le(to != null, "check_date", to);
        long total = checkRecords.selectCount(query);
        List<CheckRecord> rows = checkRecords.selectList(query.orderByDesc("check_date").orderByDesc("id")
                .last("LIMIT " + p.offset() + ", " + p.pageSize()));
        Map<Long, String> names = childNames(rows.stream().map(CheckRecord::getChildId).toList());
        List<AdminCheckRecordResp> items = rows.stream().map(r -> AdminCheckRecordResp.builder()
                .id(r.getId()).familyId(r.getFamilyId()).childId(r.getChildId())
                .childName(maskName(names.get(r.getChildId()), r.getChildId()))
                .itemId(r.getItemId()).itemName(r.getItemName()).checkDate(r.getCheckDate())
                .checkTime(r.getCheckTime()).build())
                .toList();
        return new PageResp<>(items, total, p.page(), p.pageSize());
    }

    // ==================== 勋章发放 ====================

    public PageResp<AdminMedalAwardResp> listMedalAwards(Long definitionId, Long familyId, Long childId,
            int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.MEDAL_AWARD, AdminAction.VIEW.code());
        Page p = normalizePage(page, pageSize);
        QueryWrapper<MedalAward> query = new QueryWrapper<MedalAward>()
                .eq(definitionId != null, "definition_id", definitionId)
                .eq(familyId != null, "family_id", familyId)
                .eq(childId != null, "child_id", childId);
        long total = medalAwards.selectCount(query);
        List<MedalAward> rows = medalAwards.selectList(query.orderByDesc("id")
                .last("LIMIT " + p.offset() + ", " + p.pageSize()));
        Map<Long, MedalDefinition> defs = medalDefs.selectBatchIds(
                rows.stream().map(MedalAward::getDefinitionId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(MedalDefinition::getId, Function.identity()));
        Map<Long, String> names = childNames(rows.stream().map(MedalAward::getChildId).toList());
        List<AdminMedalAwardResp> items = rows.stream().map(a -> {
            MedalDefinition def = defs.get(a.getDefinitionId());
            return AdminMedalAwardResp.builder().id(a.getId()).definitionId(a.getDefinitionId())
                    .medalCode(def == null ? null : def.getCode())
                    .medalName(def == null ? null : def.getName())
                    .familyId(a.getFamilyId()).childId(a.getChildId())
                    .childName(maskName(names.get(a.getChildId()), a.getChildId()))
                    .awardedAt(a.getAwardedAt()).consecutiveCount(a.getConsecutiveCount())
                    .refId(a.getRefId()).createTime(a.getCreateTime()).build();
        }).toList();
        return new PageResp<>(items, total, p.page(), p.pageSize());
    }

    /**
     * 勋章人工补发（L4）：幂等键 (definition_id, child_id, ref_id)，ref_id 缺省 0 = 人工通道。
     * 不触发通知、不碰钱包；审计 action=MEDAL_REISSUE。
     */
    @Transactional
    public AdminMedalAwardResp reissueMedal(AdminMedalReissueReq req) {
        AdminUserContext.requirePerm(AdminResource.MEDAL_AWARD, AdminAction.EDIT.code());
        MedalDefinition def = medalDefs.selectOne(new QueryWrapper<MedalDefinition>()
                .eq("code", req.getCode()).last("FOR UPDATE"));
        if (def == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND, "勋章不存在");
        }
        User child = users.selectById(req.getChildId());
        if (child == null || !"CHILD".equals(child.getRole())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "补发对象必须是儿童账号");
        }
        long refId = req.getRefId() == null ? 0L : req.getRefId();
        MedalAward existing = medalAwards.selectOne(new QueryWrapper<MedalAward>()
                .eq("definition_id", def.getId()).eq("child_id", req.getChildId())
                .eq("ref_id", refId).eq("delete_at", 0L).last("FOR UPDATE"));
        if (existing != null) {
            return awardResp(existing, def, child);
        }
        MedalAward award = new MedalAward();
        award.setDefinitionId(def.getId());
        award.setFamilyId(childFamilyId(req.getChildId()));
        award.setChildId(req.getChildId());
        award.setAwardedAt(System.currentTimeMillis());
        award.setConsecutiveCount(0);
        award.setRefId(refId);
        medalAwards.insert(award);
        audit.record("MEDAL_REISSUE", AdminUserContext.adminId(), award.getFamilyId(),
                "MEDAL", award.getId(), RequestContext.ip(),
                "code=" + def.getCode() + ";childId=" + req.getChildId()
                        + ";refId=" + refId + ";reason=" + req.getReason().trim());
        return awardResp(award, def, child);
    }

    /** 孩子所属家庭：以钱包行归属为准（钱包随绑定建立，必然存在；缺失视为非法请求）。 */
    private Long childFamilyId(Long childId) {
        Wallet wallet = wallets.selectOne(new QueryWrapper<Wallet>().eq("child_id", childId).last("LIMIT 1"));
        if (wallet == null) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "该孩子尚无钱包（未完成家庭绑定）");
        }
        return wallet.getFamilyId();
    }

    private AdminMedalAwardResp awardResp(MedalAward award, MedalDefinition def, User child) {
        return AdminMedalAwardResp.builder().id(award.getId()).definitionId(award.getDefinitionId())
                .medalCode(def.getCode()).medalName(def.getName()).familyId(award.getFamilyId())
                .childId(award.getChildId())
                .childName(maskName(child.getNickname(), child.getId()))
                .awardedAt(award.getAwardedAt()).consecutiveCount(award.getConsecutiveCount())
                .refId(award.getRefId()).createTime(award.getCreateTime()).build();
    }

    // ==================== CSV 导出 ====================

    /**
     * 业务数据 CSV 导出（上限 1 万行，孩子昵称默认脱敏，导出行为落审计）。
     * domain ∈ want-eat | confirmations | approvals | wallets | allowance-logs
     *   | chore-instances | check-records | medal-awards。
     */
    @Transactional
    public String exportCsv(String domain, Long familyId, Long childId, String status, String mealType,
            LocalDate from, LocalDate to) {
        return switch (domain) {
            case "want-eat" -> {
                AdminUserContext.requirePerm(AdminResource.WANT_EAT, AdminAction.EXPORT.code());
                yield exportWantEat(familyId, childId, mealType, from, to);
            }
            case "confirmations" -> {
                AdminUserContext.requirePerm(AdminResource.CONFIRM, AdminAction.EXPORT.code());
                yield exportConfirmations(familyId, childId, status, from, to);
            }
            case "approvals" -> {
                AdminUserContext.requirePerm(AdminResource.APPROVAL, AdminAction.EXPORT.code());
                yield exportApprovals();
            }
            case "wallets" -> {
                AdminUserContext.requirePerm(AdminResource.WALLET, AdminAction.EXPORT.code());
                yield exportWallets(familyId, childId);
            }
            case "allowance-logs" -> {
                AdminUserContext.requirePerm(AdminResource.WALLET, AdminAction.EXPORT.code());
                yield exportAllowanceLogs(familyId, childId, from, to);
            }
            case "chore-instances", "check-records" -> {
                AdminUserContext.requirePerm(AdminResource.CHORE_HEALTH, AdminAction.EXPORT.code());
                yield "chore-instances".equals(domain)
                        ? exportChoreInstances(familyId, childId, status, from, to)
                        : exportCheckRecords(familyId, childId, from, to);
            }
            case "medal-awards" -> {
                AdminUserContext.requirePerm(AdminResource.MEDAL_AWARD, AdminAction.EXPORT.code());
                yield exportMedalAwards(familyId, childId);
            }
            default -> throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "未知导出域: " + domain);
        };
    }

    private String exportWantEat(Long familyId, Long childId, String mealType, LocalDate from, LocalDate to) {
        List<ChildWantEat> rows = wantEats.selectList(new QueryWrapper<ChildWantEat>()
                .eq(familyId != null, "family_id", familyId).eq(childId != null, "child_id", childId)
                .eq(mealType != null, "meal_type", mealType)
                .ge(from != null, "menu_date", from).le(to != null, "menu_date", to)
                .orderByDesc("id").last("LIMIT " + EXPORT_LIMIT));
        Map<Long, String> names = childNames(rows.stream().map(ChildWantEat::getChildId).toList());
        Map<String, String> dishNames = resolveDishNames(rows);
        StringBuilder sb = new StringBuilder("id,孩子,孩子ID,家庭ID,菜单日期,餐次,来源,菜品类型,菜品ID,菜品名,状态,标记时间\n");
        for (ChildWantEat w : rows) {
            sb.append(csv(w.getId())).append(',')
                    .append(csv(maskName(names.get(w.getChildId()), w.getChildId()))).append(',')
                    .append(csv(w.getChildId())).append(',')
                    .append(csv(w.getFamilyId())).append(',')
                    .append(csv(w.getMenuDate())).append(',')
                    .append(csv(w.getMealType())).append(',')
                    .append(csv(w.getSourceType())).append(',')
                    .append(csv(w.getDishType())).append(',')
                    .append(csv(w.getDishId())).append(',')
                    .append(csv(dishNames.get(dishKey(w.getDishType(), w.getDishId())))).append(',')
                    .append(csv(w.getStatus())).append(',')
                    .append(csv(w.getCreateTime())).append('\n');
        }
        audit.record("EXPORT", AdminUserContext.adminId(), null, "biz:want-eat", null,
                RequestContext.ip(), "rows=" + rows.size());
        return sb.toString();
    }

    private String exportConfirmations(Long familyId, Long childId, String status,
            LocalDate from, LocalDate to) {
        List<MenuConfirm> rows = confirms.selectList(new QueryWrapper<MenuConfirm>()
                .eq(familyId != null, "family_id", familyId).eq(childId != null, "child_id", childId)
                .eq(status != null, "status", status)
                .ge(from != null, "menu_date", from).le(to != null, "menu_date", to)
                .orderByDesc("id").last("LIMIT " + EXPORT_LIMIT));
        Map<Long, String> names = childNames(rows.stream().map(MenuConfirm::getChildId).toList());
        StringBuilder sb = new StringBuilder(
                "id,确认单号,孩子,孩子ID,家庭ID,菜单日期,餐次,金额,状态,超额,提交时间\n");
        for (MenuConfirm c : rows) {
            sb.append(csv(c.getId())).append(',')
                    .append(csv(c.getConfirmNo())).append(',')
                    .append(csv(maskName(names.get(c.getChildId()), c.getChildId()))).append(',')
                    .append(csv(c.getChildId())).append(',')
                    .append(csv(c.getFamilyId())).append(',')
                    .append(csv(c.getMenuDate())).append(',')
                    .append(csv(c.getMealType())).append(',')
                    .append(csv(c.getTotalAmount())).append(',')
                    .append(csv(c.getStatus())).append(',')
                    .append(csv(Boolean.TRUE.equals(c.getIsOverLimit()) ? "是" : "否")).append(',')
                    .append(csv(c.getSubmitTime())).append('\n');
        }
        audit.record("EXPORT", AdminUserContext.adminId(), null, "biz:confirmations", null,
                RequestContext.ip(), "rows=" + rows.size());
        return sb.toString();
    }

    private String exportApprovals() {
        List<ConfirmApproval> rows = approvals.selectList(new QueryWrapper<ConfirmApproval>()
                .orderByDesc("id").last("LIMIT " + EXPORT_LIMIT));
        Map<Long, String> confirmNos = confirmNos(rows.stream().map(ConfirmApproval::getConfirmId).toList());
        StringBuilder sb = new StringBuilder(
                "id,确认单ID,确认单号,家长ID,动作,前状态,后状态,超额,原因,时间\n");
        for (ConfirmApproval a : rows) {
            sb.append(csv(a.getId())).append(',')
                    .append(csv(a.getConfirmId())).append(',')
                    .append(csv(confirmNos.get(a.getConfirmId()))).append(',')
                    .append(csv(a.getParentId())).append(',')
                    .append(csv(a.getAction())).append(',')
                    .append(csv(a.getBeforeStatus())).append(',')
                    .append(csv(a.getAfterStatus())).append(',')
                    .append(csv(Boolean.TRUE.equals(a.getIsOverLimit()) ? "是" : "否")).append(',')
                    .append(csv(a.getReason())).append(',')
                    .append(csv(a.getCreateTime())).append('\n');
        }
        audit.record("EXPORT", AdminUserContext.adminId(), null, "biz:approvals", null,
                RequestContext.ip(), "rows=" + rows.size());
        return sb.toString();
    }

    private String exportWallets(Long familyId, Long childId) {
        List<Wallet> rows = wallets.selectList(new QueryWrapper<Wallet>()
                .eq(familyId != null, "family_id", familyId).eq(childId != null, "child_id", childId)
                .orderByDesc("id").last("LIMIT " + EXPORT_LIMIT));
        Map<Long, String> names = childNames(rows.stream().map(Wallet::getChildId).toList());
        Set<Long> childIds = rows.stream().map(Wallet::getChildId).collect(Collectors.toCollection(HashSet::new));
        Map<Long, AllowanceRule> rules = childIds.isEmpty() ? Map.of()
                : allowanceRules.selectList(new QueryWrapper<AllowanceRule>().in("child_id", childIds)
                                .orderByAsc("id")).stream()
                        .collect(Collectors.toMap(AllowanceRule::getChildId, Function.identity(), (a, b) -> b));
        StringBuilder sb = new StringBuilder(
                "id,孩子,孩子ID,家庭ID,余额,状态,单次限额,日限额,日已用,周限额,周已用\n");
        for (Wallet w : rows) {
            AllowanceRule rule = rules.get(w.getChildId());
            sb.append(csv(w.getId())).append(',')
                    .append(csv(maskName(names.get(w.getChildId()), w.getChildId()))).append(',')
                    .append(csv(w.getChildId())).append(',')
                    .append(csv(w.getFamilyId())).append(',')
                    .append(csv(w.getBalance())).append(',')
                    .append(csv(w.getStatus())).append(',')
                    .append(csv(rule == null ? "" : rule.getSingleLimit())).append(',')
                    .append(csv(rule == null ? "" : rule.getDailyLimit())).append(',')
                    .append(csv(rule == null ? "" : rule.getDailyUsed())).append(',')
                    .append(csv(rule == null ? "" : rule.getWeeklyLimit())).append(',')
                    .append(csv(rule == null ? "" : rule.getWeeklyUsed())).append('\n');
        }
        audit.record("EXPORT", AdminUserContext.adminId(), null, "biz:wallets", null,
                RequestContext.ip(), "rows=" + rows.size());
        return sb.toString();
    }

    private String exportAllowanceLogs(Long familyId, Long childId, LocalDate from, LocalDate to) {
        List<AllowanceLog> rows = allowanceLogs.selectList(new QueryWrapper<AllowanceLog>()
                .eq(familyId != null, "family_id", familyId).eq(childId != null, "child_id", childId)
                .ge(from != null, "usage_date", from).le(to != null, "usage_date", to)
                .orderByDesc("id").last("LIMIT " + EXPORT_LIMIT));
        Map<Long, String> names = childNames(rows.stream().map(AllowanceLog::getChildId).toList());
        StringBuilder sb = new StringBuilder(
                "id,孩子,孩子ID,家庭ID,交易类型,场景,金额,变动前,变动后,使用日期,原因,时间\n");
        for (AllowanceLog l : rows) {
            sb.append(csv(l.getId())).append(',')
                    .append(csv(maskName(names.get(l.getChildId()), l.getChildId()))).append(',')
                    .append(csv(l.getChildId())).append(',')
                    .append(csv(l.getFamilyId())).append(',')
                    .append(csv(l.getTransType())).append(',')
                    .append(csv(l.getScene())).append(',')
                    .append(csv(l.getAmount())).append(',')
                    .append(csv(l.getBalanceBefore())).append(',')
                    .append(csv(l.getBalanceAfter())).append(',')
                    .append(csv(l.getUsageDate())).append(',')
                    .append(csv(l.getReason())).append(',')
                    .append(csv(l.getCreateTime())).append('\n');
        }
        audit.record("EXPORT", AdminUserContext.adminId(), null, "biz:allowance-logs", null,
                RequestContext.ip(), "rows=" + rows.size());
        return sb.toString();
    }

    private String exportChoreInstances(Long familyId, Long childId, String status,
            LocalDate from, LocalDate to) {
        List<ChoreInstance> rows = choreInstances.selectList(choreQuery(familyId, childId, status, from, to)
                .orderByDesc("id").last("LIMIT " + EXPORT_LIMIT));
        Map<Long, String> names = childNames(rows.stream().map(ChoreInstance::getChildId).toList());
        StringBuilder sb = new StringBuilder(
                "id,任务ID,孩子,孩子ID,家庭ID,状态,领取日期,奖励金额,勋章,驳回原因\n");
        for (ChoreInstance c : rows) {
            sb.append(csv(c.getId())).append(',')
                    .append(csv(c.getTaskId())).append(',')
                    .append(csv(maskName(names.get(c.getChildId()), c.getChildId()))).append(',')
                    .append(csv(c.getChildId())).append(',')
                    .append(csv(c.getFamilyId())).append(',')
                    .append(csv(c.getStatus())).append(',')
                    .append(csv(c.getClaimDate())).append(',')
                    .append(csv(c.getRewardAmount())).append(',')
                    .append(csv(c.getMedalCode())).append(',')
                    .append(csv(c.getRejectReason())).append('\n');
        }
        audit.record("EXPORT", AdminUserContext.adminId(), null, "biz:chore-instances", null,
                RequestContext.ip(), "rows=" + rows.size());
        return sb.toString();
    }

    private String exportCheckRecords(Long familyId, Long childId, LocalDate from, LocalDate to) {
        List<CheckRecord> rows = checkRecords.selectList(new QueryWrapper<CheckRecord>()
                .eq(familyId != null, "family_id", familyId).eq(childId != null, "child_id", childId)
                .ge(from != null, "check_date", from).le(to != null, "check_date", to)
                .orderByDesc("id").last("LIMIT " + EXPORT_LIMIT));
        Map<Long, String> names = childNames(rows.stream().map(CheckRecord::getChildId).toList());
        StringBuilder sb = new StringBuilder("id,孩子,孩子ID,家庭ID,打卡项ID,打卡项,打卡日期,打卡时间\n");
        for (CheckRecord r : rows) {
            sb.append(csv(r.getId())).append(',')
                    .append(csv(maskName(names.get(r.getChildId()), r.getChildId()))).append(',')
                    .append(csv(r.getChildId())).append(',')
                    .append(csv(r.getFamilyId())).append(',')
                    .append(csv(r.getItemId())).append(',')
                    .append(csv(r.getItemName())).append(',')
                    .append(csv(r.getCheckDate())).append(',')
                    .append(csv(r.getCheckTime())).append('\n');
        }
        audit.record("EXPORT", AdminUserContext.adminId(), null, "biz:check-records", null,
                RequestContext.ip(), "rows=" + rows.size());
        return sb.toString();
    }

    private String exportMedalAwards(Long familyId, Long childId) {
        List<MedalAward> rows = medalAwards.selectList(new QueryWrapper<MedalAward>()
                .eq(familyId != null, "family_id", familyId).eq(childId != null, "child_id", childId)
                .orderByDesc("id").last("LIMIT " + EXPORT_LIMIT));
        Map<Long, String> names = childNames(rows.stream().map(MedalAward::getChildId).toList());
        Map<Long, MedalDefinition> defs = medalDefs.selectBatchIds(
                rows.stream().map(MedalAward::getDefinitionId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(MedalDefinition::getId, Function.identity()));
        StringBuilder sb = new StringBuilder("id,勋章编码,勋章名,孩子,孩子ID,家庭ID,发放时间,连续次数,幂等键\n");
        for (MedalAward a : rows) {
            MedalDefinition def = defs.get(a.getDefinitionId());
            sb.append(csv(a.getId())).append(',')
                    .append(csv(def == null ? "" : def.getCode())).append(',')
                    .append(csv(def == null ? "" : def.getName())).append(',')
                    .append(csv(maskName(names.get(a.getChildId()), a.getChildId()))).append(',')
                    .append(csv(a.getChildId())).append(',')
                    .append(csv(a.getFamilyId())).append(',')
                    .append(csv(a.getAwardedAt())).append(',')
                    .append(csv(a.getConsecutiveCount())).append(',')
                    .append(csv(a.getRefId())).append('\n');
        }
        audit.record("EXPORT", AdminUserContext.adminId(), null, "biz:medal-awards", null,
                RequestContext.ip(), "rows=" + rows.size());
        return sb.toString();
    }

    // ==================== 内部工具 ====================

    private Page normalizePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "分页参数非法");
        }
        return new Page(page, pageSize, (long) (page - 1) * pageSize);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
    }

    /** 批量解析孩子展示名（User.nickname，原始值；脱敏在输出侧做）。 */
    private Map<Long, String> childNames(List<Long> childIds) {
        Set<Long> ids = childIds.stream().filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getNickname() == null ? "" : u.getNickname()));
    }

    /** 昵称脱敏：保留首字符 + '*'（空/缺失回退 孩子#id）。 */
    private String maskName(String nickname, Long childId) {
        if (nickname == null || nickname.isBlank()) {
            return "孩子#" + childId;
        }
        String trimmed = nickname.trim();
        return trimmed.length() == 1 ? trimmed : trimmed.charAt(0) + "*";
    }

    private String dishKey(String dishType, Long dishId) {
        return dishType + ":" + dishId;
    }

    /** 批量解析想吃记录的菜名：PRESET → life_dish，FAMILY → life_family_dish。 */
    private Map<String, String> resolveDishNames(List<ChildWantEat> rows) {
        Set<Long> presetIds = rows.stream().filter(w -> "PRESET".equals(w.getDishType()))
                .map(ChildWantEat::getDishId).collect(Collectors.toCollection(HashSet::new));
        Set<Long> familyIds = rows.stream().filter(w -> "FAMILY".equals(w.getDishType()))
                .map(ChildWantEat::getDishId).collect(Collectors.toCollection(HashSet::new));
        Map<String, String> result = new HashMap<>();
        if (!presetIds.isEmpty()) {
            dishes.selectBatchIds(presetIds).forEach(d ->
                    result.put(dishKey("PRESET", d.getId()), d.getName()));
        }
        if (!familyIds.isEmpty()) {
            familyDishes.selectBatchIds(familyIds).forEach(d ->
                    result.put(dishKey("FAMILY", d.getId()), d.getName()));
        }
        return result;
    }

    private Map<Long, String> confirmNos(List<Long> confirmIds) {
        Set<Long> ids = confirmIds.stream().filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return confirms.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MenuConfirm::getId, MenuConfirm::getConfirmNo));
    }

    private static String csv(Object v) {
        if (v == null) {
            return "";
        }
        String s = v.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private record Page(int page, int pageSize, long offset) {
    }
}
