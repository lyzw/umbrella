package cn.studykid.growthplanet.service;

public interface NoticeTransport {
    boolean isConfigured();

    /**
     * Implementations must use bounded network timeouts, never log openid/payload,
     * and return ACCEPTED only for a verified platform acceptance response.
     * The event key supports reconciliation, not an exactly-once delivery promise.
     */
    Outcome send(String openid, String templateId, String eventType, String eventKey);

    enum Outcome {
        ACCEPTED, RETRYABLE_FAILURE, REJECTED
    }
}
