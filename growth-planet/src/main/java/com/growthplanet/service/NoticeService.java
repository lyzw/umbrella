package com.growthplanet.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.growthplanet.entity.FamilyMember;
import com.growthplanet.entity.Notice;
import com.growthplanet.mapper.NoticeMapper;
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
        for (String channel : new String[]{"IN_APP", "SUBSCRIBE"}) {
            Notice notice = new Notice();
            notice.setEventKey("binding:" + member.getId() + ":" + member.getApplicationVersion()
                    + ":" + member.getBindStatus());
            notice.setReceiverId(receiverId);
            notice.setFamilyId(member.getFamilyId());
            notice.setChildId(member.getUserId());
            notice.setChannel(channel);
            // Sprint 1 不采集订阅授权，也不投递；未授权事件不能伪装为已发送。
            notice.setStatus("IN_APP".equals(channel) ? "PENDING" : "UNAUTHORIZED");
            notice.setEventType("BIND_" + member.getBindStatus());
            notices.insert(notice);
        }
    }

    public void cancelSubscriptions(Long familyId, Long childId) {
        notices.update(null, new UpdateWrapper<Notice>().eq("family_id", familyId)
                .eq("child_id", childId).eq("channel", "SUBSCRIBE").eq("status", "PENDING")
                .set("status", "UNAUTHORIZED"));
    }
}
