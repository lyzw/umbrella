package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.response.NoticeResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.entity.Notice;
import cn.studykid.growthplanet.mapper.NoticeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class NoticeCenterService {
    private final NoticeMapper notices;

    public NoticeCenterService(NoticeMapper notices) {
        this.notices = notices;
    }

    public PageResp<NoticeResp> list(int page, int pageSize, Boolean unreadOnly) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        QueryWrapper<Notice> query = inbox();
        if (Boolean.TRUE.equals(unreadOnly)) query.isNull("read_at");
        long total = notices.selectCount(query);
        var rows = notices.selectList(query.orderByDesc("id")
                .last("LIMIT " + pageSize + " OFFSET " + ((long) (page - 1) * pageSize)));
        Map<String, Notice> subscriptions = rows.isEmpty() ? Map.of() : notices.selectList(
                new QueryWrapper<Notice>().eq("receiver_id", UserContext.userId()).eq("channel", "SUBSCRIBE")
                        .in("event_key", rows.stream().map(Notice::getEventKey).toList()))
                .stream().collect(Collectors.toMap(Notice::getEventKey, Function.identity()));
        return new PageResp<>(rows.stream().map(row -> {
            Notice subscription = subscriptions.get(row.getEventKey());
            return NoticeResp.builder().id(row.getId().toString()).eventType(row.getEventType())
                    .familyId(row.getFamilyId().toString()).childId(row.getChildId().toString())
                    .read(row.getReadAt() != null).createTime(row.getCreateTime())
                    .subscriptionNoticeId(subscription == null ? null : subscription.getId().toString())
                    .subscriptionStatus(subscription == null ? null : subscription.getStatus()).build();
        }).toList(), total, page, pageSize);
    }

    public long unreadCount() {
        return notices.selectCount(inbox().isNull("read_at"));
    }

    public void markRead(Long id) {
        Notice notice = notices.selectOne(inbox().eq("id", id).last("FOR UPDATE"));
        if (notice == null) throw new BizException(ResultCode.E404_NOT_FOUND);
        notices.update(null, new UpdateWrapper<Notice>().eq("id", id)
                .eq("receiver_id", UserContext.userId()).isNull("read_at")
                .set("read_at", System.currentTimeMillis()));
    }

    private QueryWrapper<Notice> inbox() {
        if (UserContext.userId() == null) throw new BizException(ResultCode.E001_NO_WX_AUTH);
        return new QueryWrapper<Notice>().eq("receiver_id", UserContext.userId()).eq("channel", "IN_APP");
    }
}
