package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.AllowanceConfirmationException;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.*;
import cn.studykid.growthplanet.dto.response.*;
import cn.studykid.growthplanet.entity.*;
import cn.studykid.growthplanet.mapper.*;
import cn.studykid.growthplanet.util.BusinessRequest;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.studykid.growthplanet.util.BusinessRequest.money;

@Service
@Transactional
public class ConfirmService {
    private final MenuConfirmMapper confirms;
    private final MenuItemMapper items;
    private final ConfirmApprovalMapper approvals;
    private final FamilyMapper families;
    private final ChildAuthorizationService authorization;
    private final WalletService walletService;
    private final CatalogService catalog;
    private final NoticeService notices;
    private final AuditService audit;
    private final BusinessTime time;

    public ConfirmService(MenuConfirmMapper confirms, MenuItemMapper items, ConfirmApprovalMapper approvals,
            FamilyMapper families, ChildAuthorizationService authorization, WalletService walletService,
            CatalogService catalog, NoticeService notices, AuditService audit, BusinessTime time) {
        this.confirms = confirms;
        this.items = items;
        this.approvals = approvals;
        this.families = families;
        this.authorization = authorization;
        this.walletService = walletService;
        this.catalog = catalog;
        this.notices = notices;
        this.audit = audit;
        this.time = time;
    }

    public ConfirmResp submit(ConfirmSubmitReq req, String key) {
        requireRole("CHILD");
        BusinessRequest.requireKey(key);
        FamilyMember member = authorization.lockBoundChild(UserContext.userId());
        authorization.requireConsent(member);
        List<OrderLineReq> lines = req.getItems().stream().sorted(Comparator.comparing(OrderLineReq::getDishId)).toList();
        String digest = BusinessRequest.hash(List.of(req.getMenuId().toString(),
                lines.stream().map(line -> List.of(line.getDishId().toString(), line.getQuantity(),
                        Objects.toString(line.getNote(), ""))).toList(),
                Objects.toString(req.getRemark(), ""), Objects.toString(req.getPreviousConfirmId(), "")));
        MenuConfirm existing = confirms.findRequest(member.getUserId(), key);
        if (existing != null) {
            if (!digest.equals(existing.getRequestHash()) || existing.getDeleteAt() != 0L) {
                throw new BizException(ResultCode.E012_IDEMPOTENCY_CONFLICT);
            }
            return response(existing);
        }
        if (req.getPreviousConfirmId() != null) {
            MenuConfirm previous = confirms.selectOne(new QueryWrapper<MenuConfirm>()
                    .eq("id", req.getPreviousConfirmId()).eq("child_id", member.getUserId())
                    .eq("family_id", member.getFamilyId()).last("FOR UPDATE"));
            if (previous == null) throw new BizException(ResultCode.E404_NOT_FOUND);
            if (!Set.of("REJECTED", "CANCELLED").contains(previous.getStatus())) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
        }
        LocalDate usageDate = time.today();
        var validated = catalog.validateOrder(req.getMenuId(), member, lines, usageDate);
        Wallet wallet = walletService.lockWallet(member.getUserId(), member.getFamilyId());
        AllowanceRule rule = walletService.lockRule(member.getUserId(), usageDate);
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < lines.size(); i++) {
            total = total.add(validated.dishes().get(i).getVirtualPrice()
                    .multiply(BigDecimal.valueOf(lines.get(i).getQuantity())));
        }
        if (total.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        MenuConfirm confirm = new MenuConfirm();
        confirm.setConfirmNo(UUID.randomUUID().toString().replace("-", ""));
        confirm.setChildId(member.getUserId());
        confirm.setFamilyId(member.getFamilyId());
        confirm.setMenuId(req.getMenuId());
        confirm.setMenuDate(validated.menu().getMenuDate());
        confirm.setMealType(validated.menu().getMealType());
        confirm.setTotalAmount(total);
        confirm.setEstimatedBalance(wallet.getBalance().subtract(total));
        confirm.setIsOverLimit(walletService.overLimit(rule, total));
        confirm.setStatus("PENDING");
        confirm.setPreviousConfirmId(req.getPreviousConfirmId());
        confirm.setRequestKey(key);
        confirm.setRequestHash(digest);
        confirm.setRemark(req.getRemark());
        confirm.setSubmitTime(time.now());
        confirms.insert(confirm);
        for (int i = 0; i < lines.size(); i++) {
            OrderLineReq line = lines.get(i);
            Dish dish = validated.dishes().get(i);
            MenuItem item = new MenuItem();
            item.setConfirmId(confirm.getId());
            item.setDishId(dish.getId());
            item.setDishName(dish.getName());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(dish.getVirtualPrice());
            item.setSubtotal(dish.getVirtualPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
            item.setNote(line.getNote());
            items.insert(item);
        }
        recordEvent(confirm, "SUBMIT", families.selectById(member.getFamilyId()).getOwnerUserId());
        audit.record("CONFIRM_SUBMIT", UserContext.userId(), member.getFamilyId(), "CONFIRM", confirm.getId(),
                null, "status=PENDING;amount=" + money(total) + ";ruleVersion=" + rule.getVersion());
        return response(confirm);
    }

    public ConfirmResp approve(Long id, ConfirmApproveReq req) {
        requireRole("PARENT");
        LockedConfirm locked = lockConfirm(id);
        MenuConfirm confirm = locked.confirm();
        String commandHash = BusinessRequest.hash(List.of(req.getExpectedVersion(),
                Boolean.TRUE.equals(req.getExplicitConfirm()), Objects.toString(req.getWalletVersion(), ""),
                Objects.toString(req.getRuleVersion(), ""), Objects.toString(req.getConfirmVersion(), ""),
                Objects.toString(req.getUsageDate(), "")));
        if ("COMPLETED".equals(confirm.getStatus())) {
            if (!UserContext.userId().equals(confirm.getParentId())
                    || !commandHash.equals(confirm.getApprovalRequestHash())) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
            }
            return response(confirm);
        }
        requirePending(confirm, req.getExpectedVersion());
        LocalDate usageDate = time.today();
        Wallet wallet = walletService.lockWallet(confirm.getChildId(), confirm.getFamilyId());
        AllowanceRule rule = walletService.lockRule(confirm.getChildId(), usageDate);
        // 重新校验可用性与过敏，但结算始终使用已保存的单价快照。
        catalog.validateOrder(confirm.getMenuId(), locked.member(), orderLines(confirm.getId()), usageDate);
        if (!usageDate.equals(confirm.getMenuDate())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "菜单日期已过期");
        }
        boolean over = walletService.overLimit(rule, confirm.getTotalAmount());
        if (over && (!Boolean.TRUE.equals(req.getExplicitConfirm())
                || !wallet.getVersion().equals(req.getWalletVersion())
                || !rule.getVersion().equals(req.getRuleVersion())
                || !confirm.getVersion().equals(req.getConfirmVersion())
                || !usageDate.equals(req.getUsageDate()))) {
            throw new AllowanceConfirmationException(new AllowancePreviewResp(id.toString(), confirm.getVersion(),
                    wallet.getVersion(), rule.getVersion(), usageDate, money(confirm.getTotalAmount()),
                    money(wallet.getBalance()), money(rule.getSingleLimit()), money(rule.getDailyUsed()),
                    money(rule.getDailyLimit()), money(rule.getWeeklyUsed()), money(rule.getWeeklyLimit()), true));
        }
        AllowanceLog log = walletService.debit(wallet, rule, id, confirm.getTotalAmount(), usageDate);
        confirm.setIsOverLimit(over);
        confirm.setParentId(UserContext.userId());
        confirm.setApprovalRequestHash(commandHash);
        confirm.setCompletedBalance(log.getBalanceAfter());
        confirm.setCompletedWalletVersion(log.getWalletVersion());
        transition(confirm, "COMPLETED");
        saveApproval(confirm, "APPROVE", null, null);
        recordEvent(confirm, "COMPLETED", confirm.getChildId());
        audit.record("CONFIRM_APPROVE", UserContext.userId(), confirm.getFamilyId(), "CONFIRM", id, null,
                "balanceBefore=" + money(log.getBalanceBefore()) + ";balanceAfter=" + money(log.getBalanceAfter())
                        + ";ruleVersion=" + rule.getVersion() + ";walletVersion=" + log.getWalletVersion());
        return response(confirm);
    }

    public ConfirmResp reject(Long id, ConfirmRejectReq req) {
        requireRole("PARENT");
        MenuConfirm confirm = lockConfirm(id).confirm();
        requirePending(confirm, req.getExpectedVersion());
        confirm.setParentId(UserContext.userId());
        transition(confirm, "REJECTED");
        saveApproval(confirm, "REJECT", req.getReason(), null);
        recordEvent(confirm, "REJECTED", confirm.getChildId());
        recordTransitionAudit(confirm, "CONFIRM_REJECT");
        return response(confirm);
    }

    public ConfirmResp modify(Long id, ConfirmModifyReq req) {
        requireRole("PARENT");
        LockedConfirm locked = lockConfirm(id);
        MenuConfirm confirm = locked.confirm();
        requirePending(confirm, req.getExpectedVersion());
        catalog.validateOrder(confirm.getMenuId(), locked.member(), req.getItems());
        confirm.setParentId(UserContext.userId());
        transition(confirm, "REJECTED");
        saveApproval(confirm, "MODIFY", req.getReason(), req.getItems());
        recordEvent(confirm, "MODIFIED", confirm.getChildId());
        recordTransitionAudit(confirm, "CONFIRM_MODIFY");
        return response(confirm);
    }

    public ConfirmResp withdraw(Long id, ConfirmVersionReq req) {
        requireRole("CHILD");
        MenuConfirm confirm = lockConfirm(id).confirm();
        if ("CANCELLED".equals(confirm.getStatus())) return response(confirm);
        requirePending(confirm, req.getExpectedVersion());
        transition(confirm, "CANCELLED");
        recordEvent(confirm, "CANCELLED", families.selectById(confirm.getFamilyId()).getOwnerUserId());
        recordTransitionAudit(confirm, "CONFIRM_WITHDRAW");
        return response(confirm);
    }

    public ConfirmResp detail(Long id) {
        return response(lockConfirm(id).confirm());
    }

    public ConfirmStatusResp status(Long id) {
        requireRole("CHILD");
        MenuConfirm confirm = lockConfirm(id).confirm();
        return new ConfirmStatusResp(confirm.getId().toString(), confirm.getStatus(), confirm.getVersion());
    }

    public PageResp<ConfirmResp> list(Long childId, String status, int page, int pageSize) {
        BusinessRequest.page(page, pageSize);
        if (status != null && !Set.of("PENDING", "COMPLETED", "CANCELLED", "REJECTED").contains(status)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        FamilyMember member = authorization.lockBoundChild(childId);
        authorization.requireConsent(member);
        QueryWrapper<MenuConfirm> query = new QueryWrapper<MenuConfirm>().eq("child_id", childId)
                .eq("family_id", member.getFamilyId()).eq(status != null, "status", status);
        long total = confirms.selectCount(query);
        var rows = confirms.selectList(query.orderByDesc("id").last(BusinessRequest.limit(page, pageSize)))
                .stream().map(this::response).toList();
        return PageResp.<ConfirmResp>builder().items(rows).total(total).page(page).pageSize(pageSize).build();
    }

    private LockedConfirm lockConfirm(Long id) {
        MenuConfirm observed = confirms.selectById(id);
        if (observed == null || "CHILD".equals(UserContext.role())
                && !observed.getChildId().equals(UserContext.userId())
                || "PARENT".equals(UserContext.role()) && !observed.getFamilyId().equals(UserContext.familyId())) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        FamilyMember member = authorization.lockBoundChild(observed.getChildId());
        authorization.requireConsent(member);
        MenuConfirm confirm = confirms.selectOne(new QueryWrapper<MenuConfirm>().eq("id", id)
                .eq("child_id", member.getUserId()).eq("family_id", member.getFamilyId()).last("FOR UPDATE"));
        if (confirm == null) throw new BizException(ResultCode.E404_NOT_FOUND);
        return new LockedConfirm(member, confirm);
    }

    private void requirePending(MenuConfirm confirm, Integer expectedVersion) {
        if (!"PENDING".equals(confirm.getStatus()) || !confirm.getVersion().equals(expectedVersion)) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
    }

    private void transition(MenuConfirm confirm, String status) {
        int beforeVersion = confirm.getVersion();
        confirm.setStatus(status);
        confirm.setVersion(Math.incrementExact(beforeVersion));
        if (confirms.update(confirm, new UpdateWrapper<MenuConfirm>().eq("id", confirm.getId())
                .eq("status", "PENDING").eq("version", beforeVersion)) != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
    }

    private void saveApproval(MenuConfirm confirm, String action, String reason, List<OrderLineReq> suggestions) {
        ConfirmApproval approval = new ConfirmApproval();
        approval.setConfirmId(confirm.getId());
        approval.setParentId(UserContext.userId());
        approval.setAction(action);
        approval.setBeforeStatus("PENDING");
        approval.setAfterStatus(confirm.getStatus());
        approval.setIsOverLimit(confirm.getIsOverLimit());
        approval.setReason(reason);
        approval.setSuggestedItems(suggestions);
        approvals.insert(approval);
    }

    private List<OrderLineReq> orderLines(Long confirmId) {
        return storedItems(confirmId).stream().map(item -> {
            OrderLineReq line = new OrderLineReq();
            line.setDishId(item.getDishId());
            line.setQuantity(item.getQuantity());
            line.setNote(item.getNote());
            return line;
        }).toList();
    }

    private List<MenuItem> storedItems(Long confirmId) {
        return items.selectList(new QueryWrapper<MenuItem>().eq("confirm_id", confirmId).orderByAsc("id"));
    }

    private ConfirmResp response(MenuConfirm confirm) {
        var lines = storedItems(confirm.getId()).stream().map(item -> new ConfirmResp.Item(
                item.getDishId().toString(), item.getDishName(), item.getQuantity(), money(item.getUnitPrice()),
                money(item.getSubtotal()), item.getNote())).toList();
        ConfirmApproval approval = approvals.selectOne(new QueryWrapper<ConfirmApproval>()
                .eq("confirm_id", confirm.getId()));
        List<ConfirmResp.Suggestion> suggestions = approval == null || approval.getSuggestedItems() == null
                ? List.of() : approval.getSuggestedItems().stream().map(line ->
                new ConfirmResp.Suggestion(line.getDishId().toString(), line.getQuantity(), line.getNote())).toList();
        String note = switch (confirm.getStatus()) {
            case "PENDING" -> "等待家长确认";
            case "COMPLETED" -> "已确认";
            case "REJECTED" -> approval == null || approval.getReason() == null || approval.getReason().isBlank()
                    ? "和家长商量后可以重新选择" : approval.getReason();
            default -> "已撤回，可以重新选择";
        };
        return new ConfirmResp(confirm.getId().toString(), confirm.getChildId().toString(),
                confirm.getMenuId().toString(), confirm.getPreviousConfirmId() == null ? null
                : confirm.getPreviousConfirmId().toString(), confirm.getStatus(), confirm.getVersion(),
                confirm.getMenuDate(), confirm.getMealType(), Boolean.TRUE.equals(confirm.getIsOverLimit()),
                money(confirm.getTotalAmount()), money(confirm.getEstimatedBalance()),
                confirm.getCompletedBalance() == null ? null : money(confirm.getCompletedBalance()),
                confirm.getCompletedWalletVersion(), lines, note, suggestions);
    }

    private void recordEvent(MenuConfirm confirm, String event, Long receiver) {
        notices.recordEvent("confirm:" + confirm.getId() + ":" + confirm.getVersion(), "CONFIRM_" + event,
                confirm.getFamilyId(), confirm.getChildId(), receiver);
    }

    private void recordTransitionAudit(MenuConfirm confirm, String action) {
        audit.record(action, UserContext.userId(), confirm.getFamilyId(), "CONFIRM", confirm.getId(),
                null, "status=" + confirm.getStatus() + ";version=" + confirm.getVersion());
    }

    private void requireRole(String role) {
        if (!role.equals(UserContext.role())) throw new BizException(ResultCode.E009_FORBIDDEN);
    }

    private record LockedConfirm(FamilyMember member, MenuConfirm confirm) {
    }
}
