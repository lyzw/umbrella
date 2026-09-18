package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.NoticeSubscriptionReq;
import cn.studykid.growthplanet.entity.Notice;
import cn.studykid.growthplanet.entity.NoticeSubscription;
import cn.studykid.growthplanet.mapper.NoticeMapper;
import cn.studykid.growthplanet.mapper.NoticeSubscriptionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NoticeSubscriptionService {
    private final NoticeMapper notices;
    private final NoticeSubscriptionMapper subscriptions;
    private final NoticeEligibilityService eligibility;
    private final AuditService audit;

    public NoticeSubscriptionService(NoticeMapper notices, NoticeSubscriptionMapper subscriptions,
            NoticeEligibilityService eligibility, AuditService audit) {
        this.notices = notices;
        this.subscriptions = subscriptions;
        this.eligibility = eligibility;
        this.audit = audit;
    }

    public String authorize(NoticeSubscriptionReq req) {
        if (req == null || req.getNoticeId() == null || req.getNoticeId() <= 0 || req.getAccepted() == null) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        if (UserContext.userId() == null) throw new BizException(ResultCode.E001_NO_WX_AUTH);
        Notice reference = notices.selectOne(own(req.getNoticeId()));
        if (reference == null) throw new BizException(ResultCode.E404_NOT_FOUND);
        eligibility.lockScope(reference);
        NoticeEligibilityService.Eligibility allowed = Boolean.TRUE.equals(req.getAccepted())
                ? eligibility.requireEligible(reference) : null;
        Notice notice = notices.selectOne(own(req.getNoticeId()).last("FOR UPDATE"));
        if (notice == null) throw new BizException(ResultCode.E404_NOT_FOUND);
        if ("SENT".equals(notice.getStatus()) || "FAILED".equals(notice.getStatus())
                || notice.getAttemptCount() >= 4) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        long now = System.currentTimeMillis();
        NoticeSubscription subscription = subscriptions.selectOne(new QueryWrapper<NoticeSubscription>()
                .eq("notice_id", notice.getId()).last("FOR UPDATE"));
        if (Boolean.TRUE.equals(req.getAccepted()) && subscription != null
                && "AUTHORIZED".equals(subscription.getStatus()) && subscription.getExpiresAt() > now
                && allowed.consentId().equals(subscription.getConsentId())
                && "PENDING".equals(notice.getStatus())) {
            return notice.getStatus();
        }
        if (subscription == null) {
            // A denial needs no consent and creates no positive authorization record.
            if (allowed != null) {
                subscription = new NoticeSubscription();
                subscription.setNoticeId(notice.getId());
                subscription.setUserId(UserContext.userId());
                subscription.setConsentId(allowed.consentId());
                subscription.setStatus("AUTHORIZED");
                subscription.setAuthorizedAt(now);
                subscription.setExpiresAt(now + 24L * 3600 * 1000);
                subscriptions.insert(subscription);
            }
        } else {
            subscription.setStatus(allowed == null ? "REVOKED" : "AUTHORIZED");
            if (allowed != null) {
                subscription.setConsentId(allowed.consentId());
                subscription.setAuthorizedAt(now);
                subscription.setExpiresAt(now + 24L * 3600 * 1000);
            }
            subscriptions.updateById(subscription);
        }
        String status = allowed == null ? "UNAUTHORIZED" : "PENDING";
        // Reauthorization never resets attempts or brings a retry forward.
        Long nextRetry = allowed == null ? null : notice.getNextRetryAt() == null ? now : notice.getNextRetryAt();
        if (allowed != null && notice.getAttemptCount() > 0 && notice.getLastAttemptAt() != null) {
            nextRetry = Math.max(nextRetry,
                    notice.getLastAttemptAt() + NoticeDeliveryService.retryDelay(notice.getAttemptCount()));
        }
        notices.update(null, new UpdateWrapper<Notice>().eq("id", notice.getId())
                .set("status", status).set("next_retry_at", nextRetry)
                .set("last_error", allowed == null ? "AUTH_REVOKED" : null));
        audit.record("NOTICE_AUTH", UserContext.userId(), notice.getFamilyId(),
                "NOTICE", notice.getId(), null, status);
        return status;
    }

    private QueryWrapper<Notice> own(Long id) {
        return new QueryWrapper<Notice>().eq("id", id).eq("receiver_id", UserContext.userId())
                .eq("channel", "SUBSCRIBE");
    }
}
