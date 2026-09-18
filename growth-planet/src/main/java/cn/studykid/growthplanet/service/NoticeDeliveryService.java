package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.config.NoticeProperties;
import cn.studykid.growthplanet.entity.Notice;
import cn.studykid.growthplanet.entity.NoticeSubscription;
import cn.studykid.growthplanet.mapper.NoticeMapper;
import cn.studykid.growthplanet.mapper.NoticeSubscriptionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NoticeDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(NoticeDeliveryService.class);
    private static final long[] RETRY_DELAYS = {60_000L, 300_000L, 1_800_000L};
    private final NoticeMapper notices;
    private final NoticeSubscriptionMapper subscriptions;
    private final NoticeEligibilityService eligibility;
    private final NoticeProperties settings;
    private final NoticeTransport transport;
    private final TransactionTemplate transaction;

    public NoticeDeliveryService(NoticeMapper notices, NoticeSubscriptionMapper subscriptions,
            NoticeEligibilityService eligibility, NoticeProperties settings, NoticeTransport transport,
            PlatformTransactionManager transactionManager) {
        this.notices = notices;
        this.subscriptions = subscriptions;
        this.eligibility = eligibility;
        this.settings = settings;
        this.transport = transport;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaction.setTimeout(20);
    }

    public int deliverBatch() {
        if (!settings.isDeliveryEnabled() || settings.getPlatformApprovalReference().isBlank()
                || !transport.isConfigured() || settings.getTemplates().isEmpty()) return 0;
        int limit = Math.max(1, Math.min(settings.getBatchSize(), 100));
        long now = System.currentTimeMillis();
        var batch = notices.selectList(new QueryWrapper<Notice>().eq("channel", "SUBSCRIBE")
                .eq("status", "PENDING").lt("attempt_count", 4).le("next_retry_at", now)
                .in("event_type", settings.getTemplates().keySet()).orderByAsc("next_retry_at", "id")
                .last("LIMIT " + limit));
        int processed = 0;
        for (Notice reference : batch) {
            try {
                if (Boolean.TRUE.equals(transaction.execute(status -> deliver(reference)))) processed++;
            } catch (RuntimeException ex) {
                // No exception messages, credentials or payloads enter logs.
                log.warn("notice_delivery_transaction_failed noticeId={}", reference.getId());
            }
        }
        return processed;
    }

    private boolean deliver(Notice reference) {
        NoticeEligibilityService.Eligibility allowed = null;
        try {
            allowed = eligibility.requireEligible(reference);
        } catch (BizException ex) {
            // A missing scope, revoked/expired consent or a closed collection gate
            // cancels delivery; infrastructure failures instead roll back the attempt.
        }
        Notice notice = notices.selectOne(new QueryWrapper<Notice>().eq("id", reference.getId())
                .eq("channel", "SUBSCRIBE").last("FOR UPDATE"));
        long now = System.currentTimeMillis();
        if (notice == null || !"PENDING".equals(notice.getStatus()) || notice.getNextRetryAt() == null
                || notice.getNextRetryAt() > now || notice.getAttemptCount() >= 4) return false;
        NoticeSubscription subscription = subscriptions.selectOne(new QueryWrapper<NoticeSubscription>()
                .eq("notice_id", notice.getId()).eq("user_id", notice.getReceiverId()).last("FOR UPDATE"));
        if (allowed == null || subscription == null || !"AUTHORIZED".equals(subscription.getStatus())
                || subscription.getExpiresAt() <= now || !allowed.consentId().equals(subscription.getConsentId())) {
            notices.update(null, new UpdateWrapper<Notice>().eq("id", notice.getId())
                    .set("status", "UNAUTHORIZED").set("next_retry_at", null).set("last_error", "AUTH_INVALID"));
            return true;
        }
        String template = settings.getTemplates().get(notice.getEventType());
        if (template == null || template.isBlank()) return false;
        int attempts = notice.getAttemptCount() + 1;
        NoticeTransport.Outcome outcome;
        try {
            // Holding scope and event locks makes a committed revoke win over any
            // later send. A crash after platform acceptance remains at-least-once.
            outcome = transport.send(allowed.openid(), template, notice.getEventType(), notice.getEventKey());
        } catch (RuntimeException ex) {
            outcome = NoticeTransport.Outcome.RETRYABLE_FAILURE;
        }
        boolean sent = outcome == NoticeTransport.Outcome.ACCEPTED;
        boolean terminal = sent || outcome == NoticeTransport.Outcome.REJECTED || attempts == 4;
        String status = sent ? "SENT" : terminal ? "FAILED" : "PENDING";
        String error = sent ? null : outcome == NoticeTransport.Outcome.REJECTED
                ? "PLATFORM_REJECTED" : "TRANSPORT_FAILURE";
        long finishedAt = System.currentTimeMillis();
        notices.update(null, new UpdateWrapper<Notice>().eq("id", notice.getId())
                .set("status", status).set("attempt_count", attempts).set("last_error", error)
                .set("last_attempt_at", finishedAt)
                .set("next_retry_at", terminal ? null : finishedAt + retryDelay(attempts)));
        return true;
    }

    static long retryDelay(int attempts) {
        if (attempts < 1 || attempts > RETRY_DELAYS.length) {
            throw new IllegalArgumentException("Retry budget exhausted");
        }
        return RETRY_DELAYS[attempts - 1];
    }
}
