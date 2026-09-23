package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.constant.AdminResource;
import cn.studykid.growthplanet.common.context.AdminUserContext;
import cn.studykid.growthplanet.common.enums.AdminAction;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.response.AdminDashboardAllowanceResp;
import cn.studykid.growthplanet.dto.response.AdminDashboardChoresResp;
import cn.studykid.growthplanet.dto.response.AdminDashboardMedalsResp;
import cn.studykid.growthplanet.dto.response.AdminDashboardOverviewResp;
import cn.studykid.growthplanet.dto.response.AdminDashboardMealsResp;
import cn.studykid.growthplanet.entity.ChildProfile;
import cn.studykid.growthplanet.entity.ChoreInstance;
import cn.studykid.growthplanet.entity.Family;
import cn.studykid.growthplanet.entity.FamilyDish;
import cn.studykid.growthplanet.entity.MedalAward;
import cn.studykid.growthplanet.entity.MenuConfirm;
import cn.studykid.growthplanet.entity.PrivacyRequest;
import cn.studykid.growthplanet.mapper.AdminStatsMapper;
import cn.studykid.growthplanet.mapper.ChildProfileMapper;
import cn.studykid.growthplanet.mapper.ChoreInstanceMapper;
import cn.studykid.growthplanet.mapper.FamilyDishMapper;
import cn.studykid.growthplanet.mapper.FamilyMapper;
import cn.studykid.growthplanet.mapper.MedalAwardMapper;
import cn.studykid.growthplanet.mapper.MenuConfirmMapper;
import cn.studykid.growthplanet.mapper.PrivacyRequestMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运营看板与报表（里程碑 A · M1）。
 * 全部指标实时聚合（无汇总表）；口径严格按《运营后台_里程碑A实施规划》M1。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final FamilyMapper familyMapper;
    private final ChildProfileMapper childProfileMapper;
    private final MenuConfirmMapper menuConfirmMapper;
    private final FamilyDishMapper familyDishMapper;
    private final PrivacyRequestMapper privacyRequestMapper;
    private final ChoreInstanceMapper choreInstanceMapper;
    private final MedalAwardMapper medalAwardMapper;
    private final AdminStatsMapper stats;
    private final AuditService audit;

    private static final int NEW_RANGE_DAYS = 30;
    private static final int ACTIVE_RANGE_DAYS = 7;

    // ===================== 运营总览 =====================

    public AdminDashboardOverviewResp overview() {
        LocalDateTime newSince = LocalDateTime.now().minusDays(NEW_RANGE_DAYS);
        LocalDateTime activeSince = LocalDateTime.now().minusDays(ACTIVE_RANGE_DAYS);

        long familiesTotal = familyMapper.selectCount(fq());
        long familiesNew30d = familyMapper.selectCount(fq().ge("create_time", newSince));
        long childrenTotal = childProfileMapper.selectCount(cq());
        long childrenNew30d = childProfileMapper.selectCount(cq().ge("create_time", newSince));

        long confirmPending = menuConfirmMapper.selectCount(mq().eq("status", "PENDING"));
        long confirmPassed = menuConfirmMapper.selectCount(mq().eq("status", "COMPLETED"));
        long confirmOverLimit = menuConfirmMapper.selectCount(mq().eq("is_over_limit", 1));
        long ugcPending = familyDishMapper.selectCount(dq().eq("review_status", "PENDING"));
        long privacyBacklog = privacyRequestMapper.selectCount(pq().eq("status", "RECEIVED"));

        List<AdminDashboardOverviewResp.MiniStat> highlights = new java.util.ArrayList<>();
        highlights.add(new AdminDashboardOverviewResp.MiniStat("家庭总数", familiesTotal, "累计"));
        highlights.add(new AdminDashboardOverviewResp.MiniStat("近30天新家庭", familiesNew30d, "新增"));
        highlights.add(new AdminDashboardOverviewResp.MiniStat("孩子总数", childrenTotal, "累计"));
        highlights.add(new AdminDashboardOverviewResp.MiniStat("近7天活跃孩子", stats.activeChildren(activeSince), "活跃口径：确认单/家务/打卡任一"));
        highlights.add(new AdminDashboardOverviewResp.MiniStat("确认单待审", confirmPending, "PENDING"));
        highlights.add(new AdminDashboardOverviewResp.MiniStat("确认单超额", confirmOverLimit, "is_over_limit=1"));
        highlights.add(new AdminDashboardOverviewResp.MiniStat("UGC待审", ugcPending, "review_status=PENDING"));
        highlights.add(new AdminDashboardOverviewResp.MiniStat("隐私工单积压", privacyBacklog, "RECEIVED"));

        return new AdminDashboardOverviewResp(familiesTotal, familiesNew30d, childrenTotal, childrenNew30d,
                stats.activeFamilies(activeSince), stats.activeChildren(activeSince),
                confirmPending, confirmPassed, confirmOverLimit, ugcPending, privacyBacklog, highlights);
    }

    // ===================== 餐食看板 =====================

    public AdminDashboardMealsResp meals() {
        LocalDateTime since = LocalDateTime.now().minusDays(NEW_RANGE_DAYS);
        List<Map<String, Object>> top = stats.wantEatTop(since);
        Map<Long, String> familyDishNames = loadFamilyDishNames(top);
        List<AdminDashboardMealsResp.WantEatTop> wantEatTop = top.stream().map(r -> {
            String dishType = String.valueOf(r.get("dish_type"));
            long dishId = ((Number) r.get("dish_id")).longValue();
            long cnt = ((Number) r.get("cnt")).longValue();
            String name = "FAMILY".equals(dishType)
                    ? familyDishNames.getOrDefault(dishId, "家庭菜#" + dishId)
                    : "预设菜品#" + dishId;
            return new AdminDashboardMealsResp.WantEatTop(dishType, dishId, name, cnt);
        }).toList();

        long confirmTotal = menuConfirmMapper.selectCount(mq());
        long overLimit = menuConfirmMapper.selectCount(mq().eq("is_over_limit", 1));
        double overLimitRate = rate(overLimit, confirmTotal);

        List<Map<String, Object>> ratio = stats.confirmSourceRatio();
        long ratioSum = ratio.stream().mapToLong(r -> ((Number) r.get("cnt")).longValue()).sum();
        List<AdminDashboardMealsResp.SourceRatio> sourceRatio = ratio.stream().map(r -> {
            long cnt = ((Number) r.get("cnt")).longValue();
            return new AdminDashboardMealsResp.SourceRatio(
                    String.valueOf(r.get("source_type")), cnt, ratioSum == 0 ? 0 : round2(cnt * 100.0 / ratioSum));
        }).toList();

        return new AdminDashboardMealsResp(wantEatTop, confirmTotal, overLimitRate, sourceRatio);
    }

    // ===================== 零花钱看板 =====================

    public AdminDashboardAllowanceResp allowance() {
        LocalDateTime since = LocalDateTime.now().minusDays(NEW_RANGE_DAYS);
        List<Map<String, Object>> sums = stats.allowanceSumByType(since);
        long granted = 0, consumed = 0;
        for (Map<String, Object> m : sums) {
            String type = String.valueOf(m.get("trans_type"));
            long total = ((Number) m.get("total")).longValue();
            if ("GRANT".equals(type)) {
                granted = total;
            } else if ("CONSUME".equals(type)) {
                consumed = Math.abs(total);
            }
        }
        long balanceTotal = stats.walletBalanceTotal();
        double ratio = granted == 0 ? 0 : round2(consumed * 100.0 / granted);
        return new AdminDashboardAllowanceResp(granted, consumed, balanceTotal, ratio);
    }

    // ===================== 家务与健康看板 =====================

    public AdminDashboardChoresResp chores() {
        long total = choreInstanceMapper.selectCount(chq());
        long confirmed = choreInstanceMapper.selectCount(chq().eq("status", "CONFIRMED"));
        double taskCompletionRate = rate(confirmed, total);

        LocalDateTime since = LocalDateTime.now().minusDays(NEW_RANGE_DAYS);
        long covered = stats.checkCoveredChildren(since);
        long childrenTotal = childProfileMapper.selectCount(cq());
        double checkCoverageRate = rate(covered, childrenTotal);

        List<Map<String, Object>> dist = stats.choreStreakDistribution();
        List<AdminDashboardChoresResp.StreakBucket> streakDistribution = dist.stream()
                .map(r -> new AdminDashboardChoresResp.StreakBucket(
                        String.valueOf(r.get("bucket")), ((Number) r.get("cnt")).longValue()))
                .toList();

        return new AdminDashboardChoresResp(taskCompletionRate, checkCoverageRate, streakDistribution);
    }

    // ===================== 勋章看板 =====================

    public AdminDashboardMedalsResp medals() {
        long awardTotal = medalAwardMapper.selectCount(maq());
        long awardedChildren = stats.medalAwardDistinctChildren();
        long childrenTotal = childProfileMapper.selectCount(cq());
        double obtainRate = rate(awardedChildren, childrenTotal);

        List<Map<String, Object>> top = stats.medalTop();
        List<AdminDashboardMedalsResp.MedalTop> topMedals = top.stream().map(r -> {
            long defId = ((Number) r.get("definition_id")).longValue();
            String name = r.get("name") == null ? "勋章#" + defId : String.valueOf(r.get("name"));
            long cnt = ((Number) r.get("cnt")).longValue();
            return new AdminDashboardMedalsResp.MedalTop(defId, name, cnt);
        }).toList();

        return new AdminDashboardMedalsResp(awardTotal, obtainRate, topMedals);
    }

    // ===================== 报表导出 =====================

    @Transactional
    public String exportCsv(String domain) {
        AdminUserContext.requirePerm(AdminResource.REPORT_EXPORT, AdminAction.EXPORT.code());
        StringBuilder sb = new StringBuilder();
        sb.append('﻿'); // BOM，Excel 中文不乱码
        switch (domain) {
            case "overview" -> buildOverviewCsv(sb);
            case "meals" -> buildMealsCsv(sb);
            case "allowance" -> buildAllowanceCsv(sb);
            case "chores" -> buildChoresCsv(sb);
            case "medals" -> buildMedalsCsv(sb);
            default -> throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "未知报表域: " + domain);
        }
        audit.record("EXPORT", AdminUserContext.adminId(), null, "REPORT", null, null,
                "运营报表导出 domain=" + domain);
        return sb.toString();
    }

    private void buildOverviewCsv(StringBuilder sb) {
        AdminDashboardOverviewResp o = overview();
        sb.append("指标,数值\n");
        sb.append("家庭总数,").append(o.familiesTotal()).append('\n');
        sb.append("近30天新家庭,").append(o.familiesNew30d()).append('\n');
        sb.append("孩子总数,").append(o.childrenTotal()).append('\n');
        sb.append("近30天新孩子,").append(o.childrenNew30d()).append('\n');
        sb.append("近7天活跃家庭,").append(o.activeFamilies7d()).append('\n');
        sb.append("近7天活跃孩子,").append(o.activeChildren7d()).append('\n');
        sb.append("确认单待审,").append(o.confirmPending()).append('\n');
        sb.append("确认单通过,").append(o.confirmPassed()).append('\n');
        sb.append("确认单超额,").append(o.confirmOverLimit()).append('\n');
        sb.append("UGC待审,").append(o.ugcPending()).append('\n');
        sb.append("隐私工单积压,").append(o.privacyBacklog()).append('\n');
    }

    private void buildMealsCsv(StringBuilder sb) {
        AdminDashboardMealsResp m = meals();
        sb.append("想吃热度Top(菜品,类型,次数)\n");
        for (AdminDashboardMealsResp.WantEatTop t : m.wantEatTop()) {
            sb.append(t.dishName()).append(',').append(t.dishType()).append(',').append(t.count()).append('\n');
        }
        sb.append("确认单总量,").append(m.confirmTotal()).append('\n');
        sb.append("超额率%,").append(m.overLimitRate()).append('\n');
        sb.append("来源占比(类型,数量,占比%)\n");
        for (AdminDashboardMealsResp.SourceRatio r : m.sourceRatio()) {
            sb.append(r.sourceType()).append(',').append(r.count()).append(',').append(r.ratio()).append('\n');
        }
    }

    private void buildAllowanceCsv(StringBuilder sb) {
        AdminDashboardAllowanceResp a = allowance();
        sb.append("指标,数值(分)\n");
        sb.append("累计发放,").append(a.grantedTotal()).append('\n');
        sb.append("累计消耗,").append(a.consumedTotal()).append('\n');
        sb.append("钱包沉淀,").append(a.balanceTotal()).append('\n');
        sb.append("消耗/发放%,").append(a.grantConsumeRatio()).append('\n');
    }

    private void buildChoresCsv(StringBuilder sb) {
        AdminDashboardChoresResp c = chores();
        sb.append("指标,数值%\n");
        sb.append("任务完成率,").append(c.taskCompletionRate()).append('\n');
        sb.append("打卡覆盖率,").append(c.checkCoverageRate()).append('\n');
        sb.append("连续天数分布(桶,数量)\n");
        for (AdminDashboardChoresResp.StreakBucket b : c.streakDistribution()) {
            sb.append(b.bucket()).append(',').append(b.count()).append('\n');
        }
    }

    private void buildMedalsCsv(StringBuilder sb) {
        AdminDashboardMedalsResp m = medals();
        sb.append("指标,数值\n");
        sb.append("勋章发放总量,").append(m.awardTotal()).append('\n');
        sb.append("获得率%,").append(m.obtainRate()).append('\n');
        sb.append("热门勋章(名称,数量)\n");
        for (AdminDashboardMedalsResp.MedalTop t : m.topMedals()) {
            sb.append(t.name()).append(',').append(t.count()).append('\n');
        }
    }

    // ===================== 工具 =====================

    private Map<Long, String> loadFamilyDishNames(List<Map<String, Object>> top) {
        List<Long> familyIds = top.stream()
                .filter(r -> "FAMILY".equals(String.valueOf(r.get("dish_type"))))
                .map(r -> ((Number) r.get("dish_id")).longValue())
                .distinct().toList();
        if (familyIds.isEmpty()) {
            return Map.of();
        }
        List<FamilyDish> dishes = familyDishMapper.selectBatchIds(familyIds);
        Map<Long, String> names = new LinkedHashMap<>();
        for (FamilyDish d : dishes) {
            names.put(d.getId(), d.getName());
        }
        return names;
    }

    private QueryWrapper<Family> fq() {
        return new QueryWrapper<Family>().eq("delete_at", 0);
    }

    private QueryWrapper<ChildProfile> cq() {
        return new QueryWrapper<ChildProfile>().eq("delete_at", 0);
    }

    private QueryWrapper<MenuConfirm> mq() {
        return new QueryWrapper<MenuConfirm>().eq("delete_at", 0);
    }

    private QueryWrapper<FamilyDish> dq() {
        return new QueryWrapper<FamilyDish>().eq("delete_at", 0);
    }

    private QueryWrapper<ChoreInstance> chq() {
        return new QueryWrapper<ChoreInstance>().eq("delete_at", 0);
    }

    private QueryWrapper<MedalAward> maq() {
        return new QueryWrapper<MedalAward>().eq("delete_at", 0);
    }

    private QueryWrapper<PrivacyRequest> pq() {
        return new QueryWrapper<PrivacyRequest>().eq("delete_at", 0);
    }

    private static double rate(long part, long total) {
        return total == 0 ? 0 : round2(part * 100.0 / total);
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
