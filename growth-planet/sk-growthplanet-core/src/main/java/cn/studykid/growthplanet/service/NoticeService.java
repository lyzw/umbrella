package cn.studykid.growthplanet.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.entity.Notice;
import cn.studykid.growthplanet.mapper.NoticeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class NoticeService {
    private final NoticeMapper notices;

    public NoticeService(NoticeMapper notices) {
        this.notices = notices;
    }

    public void recordBinding(FamilyMember member, Long receiverId) {
        recordEvent("binding:" + member.getId() + ":" + member.getApplicationVersion()
                + ":" + member.getBindStatus(), "BIND_" + member.getBindStatus(),
                member.getFamilyId(), member.getUserId(), receiverId);
    }

    public void recordEvent(String eventKey, String eventType, Long familyId, Long childId, Long receiverId) {
        if (eventKey == null || !eventKey.matches("[A-Za-z0-9:_-]{1,128}")
                || eventType == null || !eventType.matches("[A-Z0-9_]{1,32}")
                || familyId == null || familyId <= 0 || childId == null || childId <= 0
                || receiverId == null || receiverId <= 0) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        for (String channel : new String[]{"IN_APP", "SUBSCRIBE"}) {
            Notice existing = notices.selectOne(new QueryWrapper<Notice>().eq("event_key", eventKey)
                    .eq("receiver_id", receiverId).eq("channel", channel).last("FOR UPDATE"));
            if (existing != null) {
                if (!eventType.equals(existing.getEventType()) || !familyId.equals(existing.getFamilyId())
                        || !childId.equals(existing.getChildId())) {
                    throw new BizException(ResultCode.E012_IDEMPOTENCY_CONFLICT);
                }
                continue;
            }
            Notice notice = new Notice();
            notice.setEventKey(eventKey);
            notice.setReceiverId(receiverId);
            notice.setFamilyId(familyId);
            notice.setChildId(childId);
            notice.setChannel(channel);
            // 每条订阅事件必须由接收账号单独授权，不能继承另一个账号或事件的授权。
            notice.setStatus("IN_APP".equals(channel) ? "PENDING" : "UNAUTHORIZED");
            notice.setEventType(eventType);
            notices.insert(notice);
        }
    }

    public void cancelSubscriptions(Long familyId, Long childId) {
        notices.update(null, new UpdateWrapper<Notice>().eq("family_id", familyId)
                .eq("child_id", childId).eq("channel", "SUBSCRIBE").eq("status", "PENDING")
                .set("status", "UNAUTHORIZED").set("next_retry_at", null)
                .set("last_error", "CONSENT_REVOKED"));
    }
}
