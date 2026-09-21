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
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static cn.studykid.growthplanet.util.BusinessRequest.money;

@Service
@Transactional
public class WalletService {
    private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999.99");
    /** 看板周趋势横轴文案，索引与 ISO 星期序号对齐（周一=1，故取下标 i 对应周一+i）。 */
    private static final String[] WEEK_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    /** 额度进度三态阈值：达到即判为对应状态。 */
    private static final int PROGRESS_WARN = 75;
    private static final int PROGRESS_DANGER = 95;
    private final WalletMapper wallets;
    private final AllowanceRuleMapper rules;
    private final AllowanceLogMapper logs;
    private final FamilyMemberMapper members;
    private final ChildAuthorizationService authorization;
    private final AuditService audit;
    private final BusinessTime time;

    public WalletService(WalletMapper wallets, AllowanceRuleMapper rules, AllowanceLogMapper logs,
            FamilyMemberMapper members, ChildAuthorizationService authorization, AuditService audit,
            BusinessTime time) {
        this.wallets = wallets;
        this.rules = rules;
        this.logs = logs;
        this.members = members;
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

    public PageResp<AllowanceLogResp> logs(Long childId, LocalDate startDate, LocalDate endDate, String direction,
            String scene, int page, int pageSize) {
        BusinessRequest.page(page, pageSize);
        LocalDate end = endDate == null ? time.today() : endDate;
        LocalDate start = startDate == null ? end.minusDays(30) : startDate;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) > 30) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        FamilyMember member = authorize(childId);
        QueryWrapper<AllowanceLog> query = new QueryWrapper<AllowanceLog>().eq("child_id", childId)
                .eq("family_id", member.getFamilyId()).between("usage_date", start, end);
        // F-026 明细增强：按“收入/支出”方向与分类（scene）筛选，30 天窗口约束保持不变。
        if (direction != null && !direction.isBlank()) {
            query.eq("trans_type", transTypeOf(direction));
        }
        if (scene != null && !scene.isBlank()) {
            query.eq("scene", scene);
        }
        long total = logs.selectCount(query);
        var items = logs.selectList(query.orderByDesc("id").last(BusinessRequest.limit(page, pageSize)))
                .stream().map(log -> new AllowanceLogResp(log.getId().toString(), childId.toString(),
                        log.getTransType(), money(log.getAmount()), log.getScene(),
                        log.getRefId() == null ? null : log.getRefId().toString(),
                        money(log.getBalanceBefore()), money(log.getBalanceAfter()), log.getCreateTime())).toList();
        return PageResp.<AllowanceLogResp>builder().items(items).total(total).page(page).pageSize(pageSize).build();
    }

    /** F-024 儿童端零花钱主页：余额、今日/本周已用与上限、本周剩余额度、本月已用统计与额度进度三态。 */
    public WalletOverviewResp overview(Long childId) {
        FamilyMember member = authorize(childId);
        Wallet wallet = lockWallet(childId, member.getFamilyId());
        AllowanceRule rule = lockRule(childId);
        LocalDate today = time.today();
        BigDecimal monthUsed = logs.sumUsage(childId, today.withDayOfMonth(1), today);
        BigDecimal remaining = rule.getWeeklyLimit().subtract(rule.getWeeklyUsed());
        if (remaining.signum() < 0) {
            remaining = BigDecimal.ZERO;
        }
        int progress = percent(rule.getWeeklyUsed(), rule.getWeeklyLimit());
        String status = progress >= PROGRESS_DANGER ? "DANGER" : progress >= PROGRESS_WARN ? "WARN" : "NORMAL";
        return new WalletOverviewResp(childId.toString(), money(wallet.getBalance()),
                money(rule.getDailyUsed()), money(rule.getDailyLimit()), money(rule.getWeeklyUsed()),
                money(rule.getWeeklyLimit()), money(remaining), money(monthUsed), progress, status);
    }

    /** F-024 家长端看板：家庭虚拟总额、本周支出/发放，以及各子女余额与本周已用。 */
    public WalletBoardResp board() {
        Long familyId = UserContext.familyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        authorization.requireParent(familyId);
        List<FamilyMember> children = members.selectList(new QueryWrapper<FamilyMember>()
                .eq("family_id", familyId).eq("role", "CHILD").eq("bind_status", "BOUND")
                .eq("delete_at", 0L).orderByAsc("user_id"));
        LocalDate today = time.today();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal weekSpend = BigDecimal.ZERO;
        BigDecimal weekGrant = BigDecimal.ZERO;
        List<WalletBoardResp.ChildSummary> summaries = new ArrayList<>();
        for (FamilyMember child : children) {
            Wallet wallet = wallets.selectOne(new QueryWrapper<Wallet>().eq("child_id", child.getUserId()));
            BigDecimal balance = wallet == null ? BigDecimal.ZERO : wallet.getBalance();
            BigDecimal used = logs.sumUsage(child.getUserId(), monday, today);
            totalBalance = totalBalance.add(balance);
            weekSpend = weekSpend.add(used);
            weekGrant = weekGrant.add(logs.sumByType(child.getUserId(), "GRANT", monday, today));
            summaries.add(new WalletBoardResp.ChildSummary(child.getUserId().toString(), money(balance), money(used)));
        }
        return new WalletBoardResp(familyId.toString(), money(totalBalance), money(weekSpend),
                money(weekGrant), summaries);
    }

    /** F-025 消费预算可视化：趋势（WEEK 按日 / MONTH 按周分桶）与支出、收入两张分类占比。 */
    public WalletStatsResp stats(Long childId, String range) {
        String scope = range == null || range.isBlank() ? "WEEK" : range.toUpperCase(Locale.ROOT);
        if (!"WEEK".equals(scope) && !"MONTH".equals(scope)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "统计范围仅支持 WEEK 或 MONTH");
        }
        authorize(childId);
        LocalDate today = time.today();
        LocalDate from = "WEEK".equals(scope) ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : today.withDayOfMonth(1);
        LocalDate to = "WEEK".equals(scope) ? from.plusDays(6) : today.withDayOfMonth(today.lengthOfMonth());
        List<AllowanceLog> rows = logs.selectList(new QueryWrapper<AllowanceLog>()
                .select("usage_date", "amount", "trans_type", "scene").eq("child_id", childId)
                .between("usage_date", from, to).orderByAsc("usage_date"));
        // 单儿童单周期流水量级很小（百条内），应用侧聚合更易保证与额度口径一致。
        Map<LocalDate, BigDecimal> spendByDay = new LinkedHashMap<>();
        Map<String, BigDecimal> spendByScene = new LinkedHashMap<>();
        Map<String, BigDecimal> grantByScene = new LinkedHashMap<>();
        BigDecimal totalSpend = BigDecimal.ZERO;
        BigDecimal totalGrant = BigDecimal.ZERO;
        for (AllowanceLog row : rows) {
            if ("DEDUCT".equals(row.getTransType())) {
                spendByDay.merge(row.getUsageDate(), row.getAmount(), BigDecimal::add);
                spendByScene.merge(row.getScene(), row.getAmount(), BigDecimal::add);
                totalSpend = totalSpend.add(row.getAmount());
            } else if ("GRANT".equals(row.getTransType())) {
                grantByScene.merge(row.getScene(), row.getAmount(), BigDecimal::add);
                totalGrant = totalGrant.add(row.getAmount());
            }
        }
        return new WalletStatsResp(childId.toString(), scope, from, to, trend(scope, from, to, spendByDay),
                slices(spendByScene, totalSpend), slices(grantByScene, totalGrant),
                money(totalSpend), money(totalGrant));
    }

    private List<WalletStatsResp.TrendPoint> trend(String scope, LocalDate from, LocalDate to,
            Map<LocalDate, BigDecimal> spendByDay) {
        List<WalletStatsResp.TrendPoint> points = new ArrayList<>();
        if ("WEEK".equals(scope)) {
            for (int i = 0; i < WEEK_LABELS.length; i++) {
                LocalDate day = from.plusDays(i);
                points.add(new WalletStatsResp.TrendPoint(WEEK_LABELS[i], day,
                        money(spendByDay.getOrDefault(day, BigDecimal.ZERO))));
            }
            return points;
        }
        int buckets = (to.getDayOfMonth() - 1) / 7 + 1;
        for (int i = 0; i < buckets; i++) {
            LocalDate start = from.plusDays(i * 7L);
            LocalDate end = start.plusDays(6);
            if (end.isAfter(to)) {
                end = to;
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                sum = sum.add(spendByDay.getOrDefault(day, BigDecimal.ZERO));
            }
            points.add(new WalletStatsResp.TrendPoint("第" + (i + 1) + "周", start, money(sum)));
        }
        return points;
    }

    private List<WalletStatsResp.CategorySlice> slices(Map<String, BigDecimal> amounts, BigDecimal total) {
        if (total.signum() <= 0) {
            return List.of();
        }
        return amounts.entrySet().stream().filter(entry -> entry.getValue().signum() > 0)
                .sorted((left, right) -> right.getValue().compareTo(left.getValue()))
                .map(entry -> new WalletStatsResp.CategorySlice(entry.getKey(), money(entry.getValue()),
                        percent(entry.getValue(), total)))
                .toList();
    }

    /** 额度进度百分比：上限为 0 时按 0 处理，超额截断到 100，便于前端进度条直接使用。 */
    private int percent(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() <= 0) {
            return 0;
        }
        int value = part.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue();
        return Math.min(100, Math.max(0, value));
    }

    private String transTypeOf(String direction) {
        return switch (direction.toUpperCase(Locale.ROOT)) {
            case "INCOME" -> "GRANT";
            case "EXPENSE" -> "DEDUCT";
            default -> throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "流水方向仅支持 INCOME 或 EXPENSE");
        };
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

    /**
     * 程序化入账（奖励/任务完成等）：复用行锁与版本校验，trans_type=GRANT。
     * 仅计入余额与流水，不触碰 AllowanceRule 的支出额度，故奖励收入不会触发超额判定（对应 F-041：收入不计支出上限）。
     * 必须在已持有钱包行锁的事务内调用（MANDATORY）。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AllowanceLog credit(Wallet wallet, Long childId, Long familyId, BigDecimal amount, String scene,
            Long refId, LocalDate usageDate) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "发放金额必须为正");
        }
        BigDecimal after = wallet.getBalance().add(amount);
        if (after.compareTo(MAX_BALANCE) > 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "余额超过存储范围");
        }
        updateBalance(wallet, after);
        AllowanceLog log = ledger(wallet, "GRANT", scene, amount, after, refId, usageDate);
        logs.insert(log);
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
