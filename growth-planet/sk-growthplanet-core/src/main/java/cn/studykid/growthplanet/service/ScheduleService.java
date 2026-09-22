package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.ScheduleCreateReq;
import cn.studykid.growthplanet.dto.response.ScheduleOccurrenceResp;
import cn.studykid.growthplanet.dto.response.ScheduleResp;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.entity.Schedule;
import cn.studykid.growthplanet.mapper.ScheduleMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 家庭日程/提醒配置与查询（F-036 配置、F-037 儿童日程视图）。 */
@Service
@Transactional
public class ScheduleService {
    private static final int MAX_RANGE_DAYS = 62;

    private final ScheduleMapper schedules;
    private final ChildAuthorizationService authorization;
    private final AuditService audit;
    private final BusinessTime time;

    public ScheduleService(ScheduleMapper schedules, ChildAuthorizationService authorization,
            AuditService audit, BusinessTime time) {
        this.schedules = schedules;
        this.authorization = authorization;
        this.audit = audit;
        this.time = time;
    }

    /** 家长创建日程（F-036）。 */
    public ScheduleResp create(ScheduleCreateReq req) {
        requireParent();
        FamilyMember member = authorization.lockBoundChild(req.getChildId());
        LocalDate date = parseDate(req.getScheduleDate());
        if (date.isBefore(time.today())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "日程日期不能早于今天");
        }
        String weekdays = normalizeWeekdays(req);

        Schedule schedule = new Schedule();
        schedule.setFamilyId(member.getFamilyId());
        schedule.setChildId(req.getChildId());
        schedule.setCreatorId(UserContext.userId());
        schedule.setTitle(req.getTitle());
        schedule.setCategory(req.getCategory());
        schedule.setNote(req.getNote() == null ? "" : req.getNote());
        schedule.setScheduleDate(date);
        schedule.setScheduleTime(req.getScheduleTime());
        schedule.setRepeatType(req.getRepeatType());
        schedule.setRepeatWeekdays(weekdays);
        schedule.setRemindMinutes(req.getRemindMinutes() == null ? 0 : req.getRemindMinutes());
        schedule.setStatus("NORMAL");
        schedules.insert(schedule);

        audit.record("SCHEDULE_CREATE", UserContext.userId(), member.getFamilyId(), "SCHEDULE", schedule.getId(),
                null, "title=" + req.getTitle() + ";repeat=" + req.getRepeatType() + ";date=" + date);
        return toResp(schedule);
    }

    /** 某儿童的生效日程列表（家长配置视图 / 儿童列表视图共用）。 */
    public List<ScheduleResp> list(Long childId) {
        FamilyMember member = authorization.lockBoundChild(childId);
        return schedules.selectList(new QueryWrapper<Schedule>()
                        .eq("family_id", member.getFamilyId()).eq("child_id", childId)
                        .eq("status", "NORMAL").eq("delete_at", 0L)
                        .orderByAsc("schedule_date", "schedule_time"))
                .stream().map(this::toResp).toList();
    }

    /**
     * 按重复规则把日程展开为 [from, to] 区间内的逐日发生项（F-037 日历/列表）。
     * 服务端展开可保证与投递口径一致；区间上限 62 天，避免无界计算。
     */
    public List<ScheduleOccurrenceResp> occurrences(Long childId, String from, String to) {
        FamilyMember member = authorization.lockBoundChild(childId);
        LocalDate start = parseDate(from);
        LocalDate end = parseDate(to);
        if (end.isBefore(start)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "查询区间不得超过 62 天");
        }
        List<Schedule> active = schedules.selectList(new QueryWrapper<Schedule>()
                .eq("family_id", member.getFamilyId()).eq("child_id", childId)
                .eq("status", "NORMAL").eq("delete_at", 0L).le("schedule_date", end));

        List<ScheduleOccurrenceResp> result = new ArrayList<>();
        for (Schedule schedule : active) {
            LocalDate cursor = schedule.getScheduleDate().isBefore(start) ? start : schedule.getScheduleDate();
            for (; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
                if (ScheduleRules.occursOn(schedule, cursor)) {
                    result.add(occurrence(schedule, cursor));
                }
            }
        }
        result.sort(Comparator.comparing(ScheduleOccurrenceResp::date)
                .thenComparing(ScheduleOccurrenceResp::time));
        return result;
    }

    /** 日程详情（F-037 点击看详情）。 */
    public ScheduleResp detail(Long id) {
        Schedule schedule = lock(id);
        if ("CHILD".equals(UserContext.role()) && !schedule.getChildId().equals(UserContext.userId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        if ("PARENT".equals(UserContext.role()) && !UserContext.familyIds().contains(schedule.getFamilyId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        return toResp(schedule);
    }

    /** 家长取消日程（NORMAL→CANCELLED）。 */
    public ScheduleResp cancel(Long id) {
        requireParent();
        Schedule schedule = lock(id);
        if (!UserContext.familyIds().contains(schedule.getFamilyId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        if (!"NORMAL".equals(schedule.getStatus())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "该日程已取消");
        }
        schedule.setStatus("CANCELLED");
        schedules.updateById(schedule);
        audit.record("SCHEDULE_CANCEL", UserContext.userId(), schedule.getFamilyId(), "SCHEDULE", schedule.getId(),
                null, "status=CANCELLED");
        return toResp(schedule);
    }

    private String normalizeWeekdays(ScheduleCreateReq req) {
        String raw = req.getRepeatWeekdays() == null ? "" : req.getRepeatWeekdays().trim();
        if (!"WEEKLY".equals(req.getRepeatType())) {
            if (!raw.isEmpty()) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "仅每周重复可指定星期");
            }
            return "";
        }
        if (raw.isEmpty()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "每周重复需指定星期（1-7，周一=1）");
        }
        TreeSet<Integer> days = new TreeSet<>(ScheduleRules.weekdays(raw));
        if (days.isEmpty()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "星期取值须为 1-7");
        }
        return days.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private Schedule lock(Long id) {
        Schedule schedule = schedules.selectOne(new QueryWrapper<Schedule>()
                .eq("id", id).eq("delete_at", 0L).last("FOR UPDATE"));
        if (schedule == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        return schedule;
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "日期格式须为 yyyy-MM-dd");
        }
    }

    private ScheduleOccurrenceResp occurrence(Schedule schedule, LocalDate date) {
        return new ScheduleOccurrenceResp(schedule.getId().toString(), date.toString(), schedule.getScheduleTime(),
                schedule.getTitle(), schedule.getCategory(), schedule.getNote(),
                date.equals(schedule.getLastRemindDate()));
    }

    private ScheduleResp toResp(Schedule s) {
        return new ScheduleResp(s.getId().toString(), s.getChildId().toString(), s.getTitle(), s.getCategory(),
                s.getNote(), s.getScheduleDate().toString(), s.getScheduleTime(), s.getRepeatType(),
                s.getRepeatWeekdays() == null ? "" : s.getRepeatWeekdays(),
                s.getRemindMinutes() == null ? 0 : s.getRemindMinutes(), s.getStatus(),
                s.getCreatorId().toString());
    }

    private void requireParent() {
        if (!"PARENT".equals(UserContext.role())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
    }
}
