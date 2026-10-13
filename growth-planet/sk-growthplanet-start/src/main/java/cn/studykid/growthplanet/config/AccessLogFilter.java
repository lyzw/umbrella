package cn.studykid.growthplanet.config;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 访问日志：全量记录 {@code /api/mini/**} 与 {@code /api/admin/**} 请求。
 *
 * <p><b>为什么是 Filter 而不是 HandlerInterceptor</b>：要记录请求体，必须在 DispatcherServlet
 * 读取输入流之前包装 request；拦截器执行时流已被 {@code @RequestBody} 消费，拿不到内容。
 * Filter 还能覆盖拦截器之外的请求（401/403、404、未匹配 handler），更适合做访问审计。</p>
 *
 * <p>记录字段：traceId / spanId / method / uri / query / status / durationMs / ip / ua / body。
 * 请求体与查询串均按 {@link AccessLogProperties} 脱敏后截断；非 JSON、非表单的请求体只记字节数。</p>
 *
 * <p>traceId 复用 {@link RequestContextFilter} 写入 MDC 的 requestId（同一请求一个 ID，便于串联
 * 业务日志与响应头），spanId 每次请求新生成；两者同时写入 MDC 与响应头 {@code X-Trace-Id} /
 * {@code X-Span-Id}。排序在 RequestContextFilter 之后，确保 requestId 已就绪。</p>
 *
 * <p><b>Logger 名</b>：{@code @Slf4j(topic = "cn.studykid.growthplanet.access")} 让本类日志走
 * 独立 logger 名而非类名，因此可单独调级别（{@code logging.level.cn.studykid.growthplanet.access}）
 * 也可单独输出文件（见 {@code logback-spring.xml} 的 ACCESS_FILE）。</p>
 */
@Slf4j(topic = "cn.studykid.growthplanet.access")
@Component("accessLogFilter")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final String MASK = "***";
    private static final String NONE = "-";
    private static final int MAX_UA_LENGTH = 200;
    private static final String FORMAT =
            "access traceId={} spanId={} method={} uri={} query={} status={} durationMs={} ip={} ua={} body={}";

    private final AccessLogProperties props;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AccessLogFilter(AccessLogProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return props.getExcludePatterns() != null
                && props.getExcludePatterns().stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        // 包装后才能缓存后续被 controller 读取的请求体。缓存上限比阈值多 1 字节，
        // 这样超限请求体的缓存长度必然 > maxBodyBytes，能被判定为「只记字节数」。
        // 该上限只影响日志缓存，不影响 controller 读取完整流。
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request, props.getMaxBodyBytes() + 1);

        // 复用 RequestContextFilter 生成的 requestId；缺失时（如绕过该过滤器的调用）自行生成并负责回收。
        boolean generatedRequestId = false;
        String traceId = MDC.get("requestId");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
            MDC.put("requestId", traceId);
            generatedRequestId = true;
        }
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);
        response.setHeader("X-Trace-Id", traceId);
        response.setHeader("X-Span-Id", spanId);
        try {
            chain.doFilter(wrapped, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            try {
                writeAccessLog(wrapped, response, traceId, spanId, durationMs);
            } catch (RuntimeException ex) {
                // 访问日志本身绝不能影响请求处理。
                log.warn("access_log_failed uri={} exceptionType={}", request.getRequestURI(),
                        ex.getClass().getSimpleName());
            }
            MDC.remove("traceId");
            MDC.remove("spanId");
            if (generatedRequestId) {
                MDC.remove("requestId");
            }
        }
    }

    private void writeAccessLog(ContentCachingRequestWrapper request, HttpServletResponse response,
                                String traceId, String spanId, long durationMs) {
        Object[] args = {
                traceId,
                spanId,
                request.getMethod(),
                request.getRequestURI(),
                maskQuery(request.getQueryString()),
                response.getStatus(),
                durationMs,
                clientIp(request),
                truncate(request.getHeader("User-Agent"), MAX_UA_LENGTH),
                props.isIncludeRequestBody() ? maskedBody(request) : NONE
        };
        if (durationMs > props.getSlowThresholdMs()) {
            log.warn(FORMAT, args);
        } else {
            log.info(FORMAT, args);
        }
    }

    /**
     * 客户端 IP：优先 {@code X-Forwarded-For} 首段（生产走 nginx 反代），否则回落直连地址。
     * 该头可被伪造，只有在可信代理后面才有意义——代理会覆盖客户端自带的值。
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ---------- 脱敏 ----------

    private String maskQuery(String query) {
        if (query == null || query.isBlank()) {
            return NONE;
        }
        return truncate(maskForm(query));
    }

    private String maskedBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content == null || content.length == 0) {
            return NONE;
        }
        if (content.length > props.getMaxBodyBytes()) {
            return "<omitted:" + content.length + " bytes>";
        }
        String type = request.getContentType() == null ? "" : request.getContentType().toLowerCase(Locale.ROOT);
        try {
            if (type.contains("json")) {
                JsonNode node = objectMapper.readTree(new String(content, StandardCharsets.UTF_8));
                return truncate(objectMapper.writeValueAsString(maskNode(node)));
            }
            if (type.contains("x-www-form-urlencoded")) {
                return truncate(maskForm(new String(content, StandardCharsets.UTF_8)));
            }
            if (type.contains("multipart")) {
                return "<multipart>";
            }
        } catch (Exception ex) {
            // 解析失败时绝不回退打印原文：可能含未识别的敏感字段。
            return "<unparsed:" + content.length + " bytes>";
        }
        return "<binary:" + content.length + " bytes>";
    }

    /** 递归脱敏：命中敏感键的值整体替换为 {@code ***}，其余节点保留原值继续下钻。 */
    private JsonNode maskNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                array.set(i, maskNode(array.get(i)));
            }
            return array;
        }
        if (!node.isObject()) {
            return node;
        }
        ObjectNode object = (ObjectNode) node;
        // 先收集字段名再改值：遍历过程中修改 ObjectNode 会破坏迭代。
        List<String> names = new ArrayList<>(object.propertyNames());
        for (String name : names) {
            if (isSensitive(name)) {
                object.put(name, MASK);
            } else {
                object.set(name, maskNode(object.get(name)));
            }
        }
        return object;
    }

    /** {@code key=value&key2=value2} 形式（表单或查询串）按 key 脱敏。 */
    private String maskForm(String text) {
        String[] pairs = text.split("&");
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                out.append('&');
            }
            String pair = pairs[i];
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                out.append(pair);
                continue;
            }
            String key = pair.substring(0, eq);
            out.append(key).append('=').append(isSensitive(key) ? MASK : pair.substring(eq + 1));
        }
        return out.toString();
    }

    private boolean isSensitive(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (props.getSensitiveExactKeys() != null && props.getSensitiveExactKeys().contains(lower)) {
            return true;
        }
        if (props.getSensitivePartialKeys() == null) {
            return false;
        }
        for (String part : props.getSensitivePartialKeys()) {
            if (part != null && !part.isBlank() && lower.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String text, int max) {
        if (text == null || text.isBlank()) {
            return NONE;
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }

    private String truncate(String text) {
        return truncate(text, props.getMaxBodyLength());
    }
}
