package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.ChoreClaimReq;
import cn.studykid.growthplanet.dto.request.ChoreConfirmReq;
import cn.studykid.growthplanet.dto.request.ChoreRejectReq;
import cn.studykid.growthplanet.dto.request.ChoreSubmitReq;
import cn.studykid.growthplanet.dto.request.ChoreTaskReq;
import cn.studykid.growthplanet.dto.response.ChoreInstanceResp;
import cn.studykid.growthplanet.dto.response.ChoreTaskResp;
import cn.studykid.growthplanet.entity.ChoreInstance;
import cn.studykid.growthplanet.entity.ChoreStreak;
import cn.studykid.growthplanet.entity.ChoreTask;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.entity.MedalDefinition;
import cn.studykid.growthplanet.mapper.ChoreInstanceMapper;
import cn.studykid.growthplanet.mapper.ChoreStreakMapper;
import cn.studykid.growthplanet.mapper.ChoreTaskMapper;
import cn.studykid.growthplanet.mapper.MedalDefinitionMapper;
import cn.studykid.growthplanet.entity.Wallet;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.studykid.growthplanet.util.BusinessRequest.money;

@Service
@Transactional
public class ChoreService {
    private static final Set<String> STATUSES = Set.of("CLAIMED", "SUBMITTED", "CONFIRMED", "REJECTED");

    private final ChoreTaskMapper tasks;
    private final ChoreInstanceMapper instances;
    private final ChoreStreakMapper streaks;
    private final MedalDefinitionMapper medalDefs;
    private final ChildAuthorizationService authorization;
    private final WalletService walletService;
    private final MedalService medalService;
    private final AuditService audit;
    private final BusinessTime time;

    public ChoreService(ChoreTaskMapper tasks, ChoreInstanceMapper instances, ChoreStreakMapper streaks,
            MedalDefinitionMapper medalDefs, ChildAuthorizationService authorization, WalletService walletService,
            MedalService medalService, AuditService audit, BusinessTime time) {
        this.tasks = tasks;
        this.instances = instances;
        this.streaks = streaks;
        this.medalDefs = medalDefs;
        this.authorization = authorization;
        this.walletService = walletService;
        this.medalService = medalService;
        this.audit = audit;
        this.time = time;
    }

    /** 家长创建家务任务模板。 */
    public ChoreTaskResp createTask(ChoreTaskReq req) {
        requireParent();
        Long familyId = UserContext.familyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        ChoreTask task = new ChoreTask();
        task.setFamilyId(familyId);
        task.setTitle(req.getTitle());
        task.setDescription(req.getDescription() == null ? "" : req.getDescription());
        task.setIcon(req.getIcon() == null ? "" : req.getIcon());
        task.setEstimatedMinutes(req.getEstimatedMinutes() == null ? 0 : req.getEstimatedMinutes());
        task.setRewardAmount(req.getRewardAmount());
        task.setCycle(req.getCycle());
        task.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        task.setStatus("NORMAL");
        tasks.insert(task);
        audit.record("CHORE_TASK_CREATE", UserContext.userId(), familyId, "CHORE_TASK", task.getId(),
                null, "title=" + req.getTitle() + ";reward=" + money(req.getRewardAmount()));
        return toTask(task);
    }

    /** 家庭任务库（儿童与家长均可查看）。 */
    public List<ChoreTaskResp> listTasks() {
        Long familyId = UserContext.familyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        return tasks.selectList(new QueryWrapper<ChoreTask>().eq("family_id", familyId).eq("delete_at", 0L)
                .eq("status", "NORMAL").orderByAsc("sort_order")).stream().map(this::toTask).toList();
    }

    /** 儿童认领任务，生成一条进行中的实例。 */
    public ChoreInstanceResp claim(ChoreClaimReq req) {
        requireRole("CHILD");
        ChoreTask task = tasks.selectOne(new QueryWrapper<ChoreTask>().eq("id", req.getTaskId())
                .eq("delete_at", 0L).last("FOR UPDATE"));
        if (task == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        FamilyMember member = authorization.lockBoundChild(UserContext.userId());
        if (!member.getFamilyId().equals(task.getFamilyId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        long active = instances.selectCount(new QueryWrapper<ChoreInstance>().eq("task_id", task.getId())
                .eq("child_id", member.getUserId()).in("status", "CLAIMED", "SUBMITTED").eq("delete_at", 0L));
        if (active > 0) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "该任务已有进行中的认领");
        }
        ChoreInstance instance = new ChoreInstance();
        instance.setTaskId(task.getId());
        instance.setFamilyId(task.getFamilyId());
        instance.setChildId(member.getUserId());
        instance.setStatus("CLAIMED");
        instance.setVersion(0);
        instance.setClaimDate(time.today());
        instance.setRewardAmount(task.getRewardAmount());
        instances.insert(instance);
        audit.record("CHORE_CLAIM", UserContext.userId(), member.getFamilyId(), "CHORE", instance.getId(),
                null, "taskId=" + task.getId());
        return response(instance, task.getTitle());
    }

    /** 儿童提交完成，状态 CLAIMED→SUBMITTED。 */
    public ChoreInstanceResp submit(ChoreSubmitReq req) {
        requireRole("CHILD");
        ChoreInstance instance = lockInstance(req.getInstanceId());
        if (!instance.getChildId().equals(UserContext.userId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        if (!"CLAIMED".equals(instance.getStatus())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "仅可提交待完成的家务");
        }
        instance.setSubmitTime(time.now());
        transition(instance, "CLAIMED", "SUBMITTED");
        audit.record("CHORE_SUBMIT", UserContext.userId(), instance.getFamilyId(), "CHORE", instance.getId(),
                null, "status=SUBMITTED;version=" + instance.getVersion());
        return response(instance, taskTitle(instance.getTaskId()));
    }

    /** 家长确认完成：状态 SUBMITTED→CONFIRMED，同事务发放虚拟币 + 连续记录 + 勋章。 */
    public ChoreInstanceResp confirm(ChoreConfirmReq req) {
        requireParent();
        ChoreInstance instance = lockInstance(req.getId());
        requireStatus(instance, "SUBMITTED", req.getExpectedVersion());
        FamilyMember member = authorization.lockBoundChild(instance.getChildId());
        if (!member.getFamilyId().equals(instance.getFamilyId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        LocalDate today = time.today();
        // 先取钱包行锁，再在同一事务内发放奖励；避免重复加锁与丢弃返回值的死调用。
        Wallet wallet = walletService.lockWallet(instance.getChildId(), instance.getFamilyId());
        walletService.credit(wallet, instance.getChildId(), instance.getFamilyId(), instance.getRewardAmount(),
                "CHORE_REWARD", instance.getId(), today);
        instance.setParentId(UserContext.userId());
        instance.setConfirmTime(time.now());
        instance.setRewardGranted(1);
        transition(instance, "SUBMITTED", "CONFIRMED");
        int currentStreak = updateStreak(instance.getChildId(), instance.getClaimDate());
        int confirmedCount = instances.selectCount(new QueryWrapper<ChoreInstance>()
                .eq("child_id", instance.getChildId()).eq("status", "CONFIRMED").eq("delete_at", 0L)).intValue();
        awardChoreMedals(member, confirmedCount, currentStreak);
        audit.record("CHORE_CONFIRM", UserContext.userId(), instance.getFamilyId(), "CHORE", instance.getId(),
                null, "reward=" + money(instance.getRewardAmount()) + ";streak=" + currentStreak
                        + ";confirmedCount=" + confirmedCount);
        return response(instance, taskTitle(instance.getTaskId()));
    }

    /** 家长驳回，状态 SUBMITTED→REJECTED。 */
    public ChoreInstanceResp reject(ChoreRejectReq req) {
        requireParent();
        ChoreInstance instance = lockInstance(req.getId());
        requireStatus(instance, "SUBMITTED", req.getExpectedVersion());
        FamilyMember member = authorization.lockBoundChild(instance.getChildId());
        if (!member.getFamilyId().equals(instance.getFamilyId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        instance.setParentId(UserContext.userId());
        instance.setRejectReason(req.getReason());
        transition(instance, "SUBMITTED", "REJECTED");
        audit.record("CHORE_REJECT", UserContext.userId(), instance.getFamilyId(), "CHORE", instance.getId(),
                null, "reason=" + req.getReason());
        return response(instance, taskTitle(instance.getTaskId()));
    }

    /** 查询某儿童的家务实例（按状态过滤可选）。 */
    public List<ChoreInstanceResp> listInstances(Long childId, String status) {
        FamilyMember member = authorization.lockBoundChild(childId);
        authorization.requireConsent(member);
        QueryWrapper<ChoreInstance> query = new QueryWrapper<ChoreInstance>()
                .eq("child_id", childId).eq("delete_at", 0L);
        if (status != null && STATUSES.contains(status)) {
            query.eq("status", status);
        }
        return instances.selectList(query.orderByDesc("id")).stream()
                .map(i -> response(i, taskTitle(i.getTaskId()))).toList();
    }

    /** 实例详情（含任务标题）。 */
    public ChoreInstanceResp detail(Long id) {
        ChoreInstance instance = lockInstance(id);
        if ("CHILD".equals(UserContext.role()) && !instance.getChildId().equals(UserContext.userId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        if ("PARENT".equals(UserContext.role()) && !UserContext.familyIds().contains(instance.getFamilyId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        return response(instance, taskTitle(instance.getTaskId()));
    }

    private void awardChoreMedals(FamilyMember member, int confirmedCount, int currentStreak) {
        List<MedalDefinition> defs = medalDefs.selectList(new QueryWrapper<MedalDefinition>()
                .eq("category", "CHORE").eq("status", "NORMAL"));
        for (MedalDefinition def : defs) {
            boolean eligible = switch (def.getConditionType()) {
                case "COUNT" -> confirmedCount >= def.getThreshold();
                case "STREAK" -> currentStreak >= def.getThreshold();
                default -> false;
            };
            if (eligible) {
                medalService.award(member.getUserId(), member.getFamilyId(), def.getCode(),
                        (long) def.getThreshold(), "STREAK".equals(def.getConditionType()) ? currentStreak : 0);
            }
        }
    }

    private int updateStreak(Long childId, LocalDate claimDate) {
        ChoreStreak streak = streaks.selectOne(new QueryWrapper<ChoreStreak>()
                .eq("child_id", childId).last("FOR UPDATE"));
        if (streak == null) {
            streak = new ChoreStreak();
            streak.setChildId(childId);
            streak.setCurrentStreak(1);
            streak.setLongestStreak(1);
            streak.setLastClaimDate(claimDate);
            streaks.insert(streak);
            return 1;
        }
        int current = streak.getCurrentStreak();
        if (claimDate.equals(streak.getLastClaimDate())) {
            // 同日多次确认不重复累计
        } else if (streak.getLastClaimDate() != null
                && streak.getLastClaimDate().plusDays(1).equals(claimDate)) {
            current = current + 1;
        } else {
            current = 1;
        }
        streak.setCurrentStreak(current);
        streak.setLongestStreak(Math.max(streak.getLongestStreak(), current));
        streak.setLastClaimDate(claimDate);
        streaks.updateById(streak);
        return current;
    }

    private ChoreInstance lockInstance(Long id) {
        ChoreInstance instance = instances.selectOne(new QueryWrapper<ChoreInstance>()
                .eq("id", id).eq("delete_at", 0L).last("FOR UPDATE"));
        if (instance == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        return instance;
    }

    private void requireStatus(ChoreInstance instance, String required, Integer expected) {
        if (!required.equals(instance.getStatus()) || !instance.getVersion().equals(expected)) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
    }

    private void transition(ChoreInstance instance, String requiredStatus, String target) {
        int before = instance.getVersion();
        instance.setStatus(target);
        instance.setVersion(Math.incrementExact(before));
        if (instances.update(instance, new UpdateWrapper<ChoreInstance>()
                .eq("id", instance.getId()).eq("status", requiredStatus).eq("version", before)) != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
    }

    private String taskTitle(Long taskId) {
        ChoreTask task = tasks.selectById(taskId);
        return task == null ? "" : task.getTitle();
    }

    private ChoreInstanceResp response(ChoreInstance instance, String taskTitle) {
        return new ChoreInstanceResp(instance.getId().toString(), instance.getTaskId().toString(), taskTitle,
                instance.getChildId().toString(), instance.getStatus(), instance.getVersion(),
                instance.getClaimDate() == null ? null : instance.getClaimDate().toString(),
                instance.getSubmitTime() == null ? null : instance.getSubmitTime().toLocalDate().toString(),
                instance.getConfirmTime() == null ? null : instance.getConfirmTime().toLocalDate().toString(),
                instance.getParentId() == null ? null : instance.getParentId().toString(),
                money(instance.getRewardAmount()), instance.getRewardGranted() != null && instance.getRewardGranted() == 1,
                instance.getMedalCode(), instance.getRejectReason());
    }

    private ChoreTaskResp toTask(ChoreTask t) {
        return new ChoreTaskResp(t.getId().toString(), t.getTitle(), t.getDescription(), t.getIcon(),
                t.getEstimatedMinutes() == null ? 0 : t.getEstimatedMinutes(), money(t.getRewardAmount()),
                t.getCycle(), t.getSortOrder() == null ? 0 : t.getSortOrder(), t.getStatus());
    }

    private void requireParent() {
        if (!"PARENT".equals(UserContext.role())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
    }

    private void requireRole(String role) {
        if (!role.equals(UserContext.role())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
    }
}
