package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.response.MenuReminderResp;
import cn.studykid.growthplanet.entity.Family;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.entity.Notice;
import cn.studykid.growthplanet.mapper.FamilyMapper;
import cn.studykid.growthplanet.mapper.FamilyMemberMapper;
import cn.studykid.growthplanet.mapper.NoticeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 儿童提醒家庭创建家长发布今日餐单。
 *
 * <p>以当前儿童的有效家庭关系行作为并发锁，同一儿童同一业务日的重复请求
 * 会在事务内返回幂等结果，不依赖客户端传入家庭、家长或日期。</p>
 */
@Service
@Transactional
public class MenuReminderService {
    private static final String EVENT_TYPE = "MENU_REMINDER";
    private static final String CREATED = "CREATED";
    private static final String ALREADY_EXISTS = "ALREADY_EXISTS";

    private final FamilyMapper families;
    private final FamilyMemberMapper members;
    private final NoticeMapper notices;
    private final NoticeService noticeService;
    private final BusinessTime businessTime;

    public MenuReminderService(FamilyMapper families, FamilyMemberMapper members, NoticeMapper notices,
            NoticeService noticeService, BusinessTime businessTime) {
        this.families = families;
        this.members = members;
        this.notices = notices;
        this.noticeService = noticeService;
        this.businessTime = businessTime;
    }

    public MenuReminderResp remindParent() {
        Long childId = UserContext.userId();
        Long familyId = UserContext.familyId();
        if (childId == null || childId <= 0 || familyId == null || familyId <= 0) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }

        FamilyMember member = members.selectOne(new QueryWrapper<FamilyMember>()
                .eq("user_id", childId)
                .eq("family_id", familyId)
                .eq("role", "CHILD")
                .eq("bind_status", "BOUND")
                .eq("delete_at", 0L)
                .last("FOR UPDATE"));
        if (member == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }

        Family family = families.selectOne(new QueryWrapper<Family>()
                .eq("id", familyId)
                .eq("delete_at", 0L)
                .last("FOR UPDATE"));
        if (family == null || family.getOwnerUserId() == null || family.getOwnerUserId() <= 0) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }

        LocalDate businessDate = businessTime.today();
        String eventKey = "menu-reminder:" + childId + ":" + businessDate;
        Long receiverId = family.getOwnerUserId();
        Notice existing = notices.selectOne(new QueryWrapper<Notice>()
                .eq("event_key", eventKey)
                .eq("receiver_id", receiverId)
                .eq("channel", "IN_APP")
                .last("FOR UPDATE"));
        if (existing != null) {
            if (!EVENT_TYPE.equals(existing.getEventType())
                    || !familyId.equals(existing.getFamilyId())
                    || !childId.equals(existing.getChildId())) {
                throw new BizException(ResultCode.E012_IDEMPOTENCY_CONFLICT);
            }
            return MenuReminderResp.builder().status(ALREADY_EXISTS).build();
        }

        // NoticeService 在同一事务内写入 IN_APP 和 SUBSCRIBE 两个通道；
        // SUBSCRIBE 初始为 UNAUTHORIZED，不能据此宣称已送达微信。
        noticeService.recordEvent(eventKey, EVENT_TYPE, familyId, childId, receiverId);
        return MenuReminderResp.builder().status(CREATED).build();
    }
}
