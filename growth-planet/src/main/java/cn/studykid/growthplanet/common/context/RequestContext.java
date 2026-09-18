package cn.studykid.growthplanet.common.context;

import org.slf4j.MDC;
import java.util.UUID;

public final class RequestContext {
    private RequestContext() {
    }

    public static String requestId() {
        String id = MDC.get("requestId");
        return id == null ? UUID.randomUUID().toString() : id;
    }

    public static String ip() {
        return MDC.get("remoteIp");
    }
}
