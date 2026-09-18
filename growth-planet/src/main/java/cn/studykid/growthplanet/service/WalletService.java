package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.AllowanceRuleReq;
import cn.studykid.growthplanet.dto.request.WalletGrantReq;
import cn.studykid.growthplanet.dto.response.*;
import cn.studykid.growthplanet.entity.*;
import cn.studykid.growthplanet.mapper.*;
import cn.studykid.growthplanet.util.BusinessRequest;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static cn.studykid.growthplanet.util.BusinessRequest.money;

@Service
@Transactional
public class WalletService {
    private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999.99");
    private final WalletMapper wallets;
    private final AllowanceRuleMapper rules;
    private final AllowanceLogMapper logs;
    private final ChildAuthorizationService authorization;
    private final AuditService audit;
    private final BusinessTime time;

    public WalletService(WalletMapper wallets, AllowanceRuleMapper rules, AllowanceLogMapper logs,
            ChildAuthorizationService authorization, AuditService audit, BusinessTime time) {
        this.wallets = wallets;
        this.rules = rules;
        this.logs = logs;
        this.authorization = authorization;
        this.audit = audit;
        this.time = time;
    }

    public WalletResp balance(Long childId) {
        FamilyMember member = authorize(childId);
        Wallet wallet = lockWallet(childId, member.getFamilyId());
        return new WalletResp(childId.toString(), money(wallet.getBalance()), wallet.getVersion(), null);
    }

    public WalletResp grant(WalletGrantReq req, String key) {
        requireParent();
        BusinessRequest.requireKey(key);
        FamilyMember member = authorize(req.getChildId());
        Wallet wallet = lockWallet(req.getChildId(), member.getFamilyId());
        String digest = BusinessRequest.hash(List.of(req.getChildId().toString(),
                money(req.getAmount()), req.getReason()));
        AllowanceLog existing = logs.findGrant(UserContext.userId(), key);
        if (existing != null) {
            if (!digest.equals(existing.getRequestHash()) || existing.getDeleteAt() != 0L) {
                throw new BizException(ResultCode.E012_IDEMPOTENCY_CONFLICT);
            }
            return grantResponse(existing);
        }
        BigDecimal after = wallet.getBalance().add(req.getAmount());
        if (after.compareTo(MAX_BALANCE) > 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "余额超过存储范围");
        }
        updateBalance(wallet, after);
        AllowanceLog log = ledger(wallet, "GRANT", "MANUAL", req.getAmount(), after, null, time.today());
        log.setRequestKey(key);
        log.setRequestHash(digest);
        log.setReason(req.getReason());
        logs.insert(log);
        audit.record("WALLET_GRANT", UserContext.userId(), member.getFamilyId(), "CHILD", req.getChildId(),
                null, "logId=" + log.getId() + ";balanceBefore=" + log.getBalanceBefore()
                        + ";balanceAfter=" + after + ";walletVersion=" + log.getWalletVersion());
        return grantResponse(log);
    }

    public AllowanceRuleResp rule(Long childId) {
        FamilyMember member = authorize(childId);
        lockWallet(childId, member.getFamilyId());
        return ruleResponse(lockRule(childId));
    }

    public AllowanceRuleResp updateRule(AllowanceRuleReq req) {
        requireParent();
        if (req.getSingleLimit().compareTo(req.getDailyLimit()) > 0
                || req.getDailyLimit().compareTo(req.getWeeklyLimit()) > 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        FamilyMember member = authorize(req.getChildId());
        lockWallet(req.getChildId(), member.getFamilyId());
        AllowanceRule rule = lockRule(req.getChildId());
        if (!rule.getVersion().equals(req.getExpectedVersion())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        rule.setSingleLimit(req.getSingleLimit());
        rule.setDailyLimit(req.getDailyLimit());
        rule.setWeeklyLimit(req.getWeeklyLimit());
        rule.setVersion(Math.incrementExact(rule.getVersion()));
        if (rules.updateById(rule) != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        audit.record("ALLOWANCE_RULE", UserContext.userId(), member.getFamilyId(), "CHILD", req.getChildId(),
                null, "ruleVersion=" + rule.getVersion());
        return ruleResponse(rule);
    }

    public PageResp<AllowanceLogResp> logs(Long childId, LocalDate startDate, LocalDate endDate,
            int page, int pageSize) {
        BusinessRequest.page(page, pageSize);
        LocalDate end = endDate == null ? time.today() : endDate;
        LocalDate start = startDate == null ? end.minusDays(30) : startDate;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) > 30) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        FamilyMember member = authorize(childId);
        QueryWrapper<AllowanceLog> query = new QueryWrapper<AllowanceLog>().eq("child_id", childId)
                .eq("family_id", member.getFamilyId()).between("usage_date", start, end);
        long total = logs.selectCount(query);
        var items = logs.selectList(query.orderByDesc("id").last(BusinessRequest.limit(page, pageSize)))
                .stream().map(log -> new AllowanceLogResp(log.getId().toString(), childId.toString(),
                        log.getTransType(), money(log.getAmount()), log.getScene(),
                        log.getRefId() == null ? null : log.getRefId().toString(),
                        money(log.getBalanceBefore()), money(log.getBalanceAfter()), log.getCreateTime())).toList();
        return PageResp.<AllowanceLogResp>builder().items(items).total(total).page(page).pageSize(pageSize).build();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Wallet lockWallet(Long childId, Long familyId) {
        Wallet wallet = wallets.selectOne(new QueryWrapper<Wallet>().eq("child_id", childId).last("FOR UPDATE"));
        if (wallet == null) {
            wallet = new Wallet();
            wallet.setChildId(childId);
            wallet.setFamilyId(familyId);
            wallets.insert(wallet);
        }
        if (!familyId.equals(wallet.getFamilyId()) || !"NORMAL".equals(wallet.getStatus())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        return wallet;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AllowanceRule lockRule(Long childId) {
        return lockRule(childId, time.today());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AllowanceRule lockRule(Long childId, LocalDate today) {
        AllowanceRule rule = rules.selectOne(new QueryWrapper<AllowanceRule>()
                .eq("child_id", childId).last("FOR UPDATE"));
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (rule == null) {
            Wallet wallet = wallets.selectOne(new QueryWrapper<Wallet>().eq("child_id", childId));
            if (wallet == null) {
                throw new IllegalStateException("Wallet must be locked before allowance rule");
            }
            rule = new AllowanceRule();
            rule.setChildId(childId);
            rule.setFamilyId(wallet.getFamilyId());
            rule.setDailyPeriod(today);
            rule.setWeeklyPeriod(monday);
            rule.setDailyUsed(logs.sumUsage(childId, today, today));
            rule.setWeeklyUsed(logs.sumUsage(childId, monday, today));
            rules.insert(rule);
        } else if (!today.equals(rule.getDailyPeriod()) || !monday.equals(rule.getWeeklyPeriod())) {
            // 两个周期独立核对成功流水；人工标记隐藏的流水不能重获额度。
            if (!today.equals(rule.getDailyPeriod())) {
                rule.setDailyPeriod(today);
                rule.setDailyUsed(logs.sumUsage(childId, today, today));
            }
            if (!monday.equals(rule.getWeeklyPeriod())) {
                rule.setWeeklyPeriod(monday);
                rule.setWeeklyUsed(logs.sumUsage(childId, monday, today));
            }
            rule.setVersion(Math.incrementExact(rule.getVersion()));
            rules.updateById(rule);
        }
        return rule;
    }

    public boolean overLimit(AllowanceRule rule, BigDecimal amount) {
        return amount.compareTo(rule.getSingleLimit()) > 0
                || rule.getDailyUsed().add(amount).compareTo(rule.getDailyLimit()) > 0
                || rule.getWeeklyUsed().add(amount).compareTo(rule.getWeeklyLimit()) > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AllowanceLog debit(Wallet wallet, AllowanceRule rule, Long confirmId, BigDecimal amount,
            LocalDate usageDate) {
        // 锁等待跨日时回滚；额度周期和流水始终使用同一次结算捕获的日期。
        if (!time.today().equals(usageDate) || !usageDate.equals(rule.getDailyPeriod())
                || !usageDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .equals(rule.getWeeklyPeriod())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "结算日期已变化，请重新确认");
        }
        if (amount.signum() < 0 || wallet.getBalance().compareTo(amount) < 0) {
            throw new BizException(ResultCode.E006_BALANCE_INSUFFICIENT);
        }
        BigDecimal after = wallet.getBalance().subtract(amount);
        updateBalance(wallet, after);
        AllowanceLog log = ledger(wallet, "DEDUCT", "MENU_CONFIRM", amount, after, confirmId, usageDate);
        logs.insert(log);
        rule.setDailyUsed(rule.getDailyUsed().add(amount));
        rule.setWeeklyUsed(rule.getWeeklyUsed().add(amount));
        rule.setVersion(Math.incrementExact(rule.getVersion()));
        if (rules.updateById(rule) != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        return log;
    }

    private void updateBalance(Wallet wallet, BigDecimal after) {
        int updated = wallets.update(null, new UpdateWrapper<Wallet>().eq("id", wallet.getId())
                .eq("version", wallet.getVersion()).eq("status", "NORMAL")
                .eq("balance", wallet.getBalance()).set("balance", after)
                .set("version", Math.incrementExact(wallet.getVersion())));
        if (updated != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
    }

    private AllowanceLog ledger(Wallet wallet, String type, String scene, BigDecimal amount,
            BigDecimal after, Long refId, LocalDate usageDate) {
        AllowanceLog log = new AllowanceLog();
        log.setWalletId(wallet.getId());
        log.setChildId(wallet.getChildId());
        log.setFamilyId(wallet.getFamilyId());
        log.setOperatorId(UserContext.userId());
        log.setTransType(type);
        log.setScene(scene);
        log.setRefId(refId);
        log.setAmount(amount);
        log.setBalanceBefore(wallet.getBalance());
        log.setBalanceAfter(after);
        log.setWalletVersion(Math.incrementExact(wallet.getVersion()));
        log.setUsageDate(usageDate);
        log.setCreateTime(time.now());
        return log;
    }

    private FamilyMember authorize(Long childId) {
        FamilyMember member = authorization.lockBoundChild(childId);
        authorization.requireConsent(member);
        return member;
    }

    private void requireParent() {
        if (!"PARENT".equals(UserContext.role())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
    }

    private WalletResp grantResponse(AllowanceLog log) {
        return new WalletResp(log.getChildId().toString(), money(log.getBalanceAfter()),
                log.getWalletVersion(), log.getId().toString());
    }

    private AllowanceRuleResp ruleResponse(AllowanceRule rule) {
        return new AllowanceRuleResp(rule.getChildId().toString(), money(rule.getSingleLimit()),
                money(rule.getDailyLimit()), money(rule.getWeeklyLimit()), money(rule.getDailyUsed()),
                money(rule.getWeeklyUsed()), rule.getDailyPeriod(), rule.getWeeklyPeriod(), rule.getVersion());
    }
}
