package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.CheckItemReq;
import cn.studykid.growthplanet.dto.response.CheckCalendarResp;
import cn.studykid.growthplanet.dto.response.CheckItemResp;
import cn.studykid.growthplanet.dto.response.CheckItemTodayResp;
import cn.studykid.growthplanet.dto.response.CheckRecordResp;
import cn.studykid.growthplanet.entity.CheckItem;
import cn.studykid.growthplanet.entity.CheckRecord;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.entity.MedalDefinition;
import cn.studykid.growthplanet.mapper.CheckItemMapper;
import cn.studykid.growthplanet.mapper.CheckRecordMapper;
import cn.studykid.growthplanet.mapper.MedalDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 健康打卡（F-033~F-035）。纯行为激励，不涉及零花钱/额度护栏。
 * 所有健康接口均过 {@code requireConsent}，撤回同意后停采并隐藏（F-007/NF-5）。
 */
@Service
@Transactional
public class CheckService {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CheckItemMapper items;
    private final CheckRecordMapper records;
    private final MedalDefinitionMapper medalDefs;
    private final ChildAuthorizationService authorization;
    private final MedalService medalService;
    private final AuditService audit;
    private final BusinessTime time;

    public CheckService(CheckItemMapper items, CheckRecordMapper records, MedalDefinitionMapper medalDefs,
            ChildAuthorizationService authorization, MedalService medalService, AuditService audit, BusinessTime time) {
        this.items = items;
        this.records = records;
        this.medalDefs = medalDefs;
        this.authorization = authorization;
        this.medalService = medalService;
        this.audit = audit;
        this.time = time;
    }

    // ---- 家长端：打卡项配置（F-033）----

    public CheckItemResp createItem(CheckItemReq req) {
        requireParent();
        Long familyId = UserContext.familyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        CheckItem item = new CheckItem();
        item.setFamilyId(familyId);
        item.setName(req.getName());
        item.setIcon(req.getIcon());
        item.setUnit(req.getUnit());
        item.setDailyTarget(req.getDailyTarget() == null ? 0 : req.getDailyTarget());
        item.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        item.setVersion(0);
        items.insert(item);
        audit.record("CHECK_ITEM_CREATE", UserContext.userId(), familyId, "CHECK_ITEM", item.getId(), null,
                "name=" + req.getName());
        return toItem(item);
    }

    public List<CheckItemResp> listItems() {
        Long familyId = UserContext.familyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        return items.selectList(new QueryWrapper<CheckItem>().eq("family_id", familyId).eq("delete_at", 0L)
                .orderByAsc("sort_order", "id")).stream().map(this::toItem).toList();
    }

    public CheckItemResp updateItem(Long id, CheckItemReq req, Integer expectedVersion) {
        requireParent();
        CheckItem item = lockItem(id, UserContext.familyId());
        requireVersion(item, expectedVersion);
        item.setName(req.getName());
        item.setIcon(req.getIcon());
        item.setUnit(req.getUnit());
        item.setDailyTarget(req.getDailyTarget() == null ? 0 : req.getDailyTarget());
        item.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        transition(item, expectedVersion);
        audit.record("CHECK_ITEM_UPDATE", UserContext.userId(), item.getFamilyId(), "CHECK_ITEM", id, null,
                "name=" + req.getName());
        return toItem(item);
    }

    public void deleteItem(Long id, Integer expectedVersion) {
        requireParent();
        Long familyId = requireParentFamily();
        CheckItem item = lockItem(id, familyId);
        requireVersion(item, expectedVersion);
        int rows = items.update(null, new UpdateWrapper<CheckItem>()
                .eq("id", id).eq("version", expectedVersion)
                .setSql("delete_at = UNIX_TIMESTAMP() * 1000")
                .setSql("update_time = CURRENT_TIMESTAMP")
                .set("version", expectedVersion + 1));
        if (rows != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        audit.record("CHECK_ITEM_DELETE", UserContext.userId(), familyId, "CHECK_ITEM", id, null, null);
    }

    // ---- 儿童端：打卡（F-034/F-035）----

    public List<CheckItemResp> listAvailableItems() {
        FamilyMember member = authorization.lockBoundChild(UserContext.userId());
        authorization.requireConsent(member);
        return items.selectList(new QueryWrapper<CheckItem>().eq("family_id", member.getFamilyId())
                .eq("delete_at", 0L).orderByAsc("sort_order", "id")).stream().map(this::toItem).toList();
    }

    public CheckRecordResp checkIn(Long itemId) {
        FamilyMember member = authorization.lockBoundChild(UserContext.userId());
        authorization.requireConsent(member);
        Long familyId = member.getFamilyId();
        Long childId = member.getUserId();
        CheckItem item = items.selectOne(new QueryWrapper<CheckItem>().eq("id", itemId)
                .eq("family_id", familyId).eq("delete_at", 0L).last("FOR UPDATE"));
        if (item == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        if (item.getDailyTarget() != null && item.getDailyTarget() > 0) {
            long count = records.selectCount(new QueryWrapper<CheckRecord>()
                    .eq("child_id", childId).eq("item_id", itemId)
                    .eq("check_date", time.today()).eq("delete_at", 0L));
            if (count >= item.getDailyTarget()) {
                throw new BizException(ResultCode.E013_DAILY_LIMIT_REACHED);
            }
        }
        CheckRecord rec = new CheckRecord();
        rec.setFamilyId(familyId);
        rec.setChildId(childId);
        rec.setItemId(item.getId());
        rec.setItemName(item.getName());
        rec.setCheckDate(time.today());
        rec.setCheckTime(time.now());
        records.insert(rec);
        int streak = computeStreak(childId, time.today());
        awardHealthMedals(member, streak);
        return toRecord(rec);
    }

    public List<CheckItemTodayResp> todayCounts() {
        FamilyMember member = authorization.lockBoundChild(UserContext.userId());
        authorization.requireConsent(member);
        Long familyId = member.getFamilyId();
        Long childId = member.getUserId();
        LocalDate today = time.today();
        List<CheckItem> familyItems = items.selectList(new QueryWrapper<CheckItem>()
                .eq("family_id", familyId).eq("delete_at", 0L).orderByAsc("sort_order", "id"));
        return familyItems.stream().map(item -> {
            long count = records.selectCount(new QueryWrapper<CheckRecord>()
                    .eq("child_id", childId).eq("item_id", item.getId())
                    .eq("check_date", today).eq("delete_at", 0L));
            int target = item.getDailyTarget() == null ? 0 : item.getDailyTarget();
            return CheckItemTodayResp.builder()
                    .itemId(item.getId()).itemName(item.getName())
                    .count((int) count).dailyTarget(target)
                    .reached(target > 0 && count >= target).build();
        }).toList();
    }

    public CheckCalendarResp calendar(String month) {
        FamilyMember member = authorization.lockBoundChild(UserContext.userId());
        authorization.requireConsent(member);
        Long childId = member.getUserId();
        int streak = computeStreak(childId, time.today());
        List<CheckRecord> recs = records.selectList(new QueryWrapper<CheckRecord>()
                .eq("child_id", childId).eq("delete_at", 0L).select("check_date"));
        Set<String> dates = recs.stream().map(r -> r.getCheckDate().format(DATE_FMT)).collect(Collectors.toSet());
        List<String> checked = month == null || month.isBlank()
                ? dates.stream().sorted().toList()
                : dates.stream().filter(d -> d.startsWith(month)).sorted().toList();
        return CheckCalendarResp.builder().childId(childId).currentStreak(streak).checkedDates(checked).build();
    }

    // ---- 私有辅助 ----

    private int computeStreak(Long childId, LocalDate today) {
        List<CheckRecord> recs = records.selectList(new QueryWrapper<CheckRecord>()
                .eq("child_id", childId).eq("delete_at", 0L).select("check_date"));
        Set<LocalDate> dates = recs.stream().map(CheckRecord::getCheckDate).collect(Collectors.toSet());
        if (dates.isEmpty()) {
            return 0;
        }
        LocalDate cursor;
        if (dates.contains(today)) {
            cursor = today;
        } else if (dates.contains(today.minusDays(1))) {
            cursor = today.minusDays(1);
        } else {
            return 0;
        }
        int streak = 0;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private void awardHealthMedals(FamilyMember member, int streak) {
        if (streak <= 0) {
            return;
        }
        List<MedalDefinition> defs = medalDefs.selectList(new QueryWrapper<MedalDefinition>()
                .eq("category", "HEALTH").eq("status", "NORMAL").eq("condition_type", "STREAK"));
        for (MedalDefinition def : defs) {
            if (def.getThreshold() != null && streak >= def.getThreshold()) {
                medalService.award(member.getUserId(), member.getFamilyId(), def.getCode(),
                        (long) def.getThreshold(), streak);
            }
        }
    }

    private CheckItem lockItem(Long id, Long familyId) {
        CheckItem item = items.selectOne(new QueryWrapper<CheckItem>()
                .eq("id", id).eq("family_id", familyId).eq("delete_at", 0L).last("FOR UPDATE"));
        if (item == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        return item;
    }

    private Long requireParentFamily() {
        Long familyId = UserContext.familyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        return familyId;
    }

    private void requireParent() {
        if (!"PARENT".equals(UserContext.role())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
    }

    private void requireVersion(CheckItem item, Integer expected) {
        if (expected == null || !expected.equals(item.getVersion())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
    }

    private void transition(CheckItem item, Integer expectedVersion) {
        int before = item.getVersion();
        item.setVersion(Math.incrementExact(before));
        if (items.update(item, new UpdateWrapper<CheckItem>()
                .eq("id", item.getId()).eq("version", before)) != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
    }

    private CheckItemResp toItem(CheckItem item) {
        return CheckItemResp.builder()
                .itemId(item.getId()).familyId(item.getFamilyId()).name(item.getName())
                .icon(item.getIcon()).unit(item.getUnit())
                .dailyTarget(item.getDailyTarget() == null ? 0 : item.getDailyTarget())
                .sortOrder(item.getSortOrder() == null ? 0 : item.getSortOrder())
                .version(item.getVersion()).build();
    }

    private CheckRecordResp toRecord(CheckRecord rec) {
        return CheckRecordResp.builder()
                .recordId(rec.getId()).itemId(rec.getItemId()).itemName(rec.getItemName())
                .checkDate(rec.getCheckDate() == null ? null : rec.getCheckDate().format(DATE_FMT))
                .checkTime(rec.getCheckTime() == null ? null : rec.getCheckTime().toString())
                .build();
    }
}
