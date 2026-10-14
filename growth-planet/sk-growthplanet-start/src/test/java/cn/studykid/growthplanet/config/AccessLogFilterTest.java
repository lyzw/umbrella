package cn.studykid.growthplanet.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 访问日志过滤器单测：字段完整性、请求体脱敏、覆盖排除与慢请求级别。
 * 不启动 Spring 容器，直接以 mock request/response 驱动过滤器。
 */
class AccessLogFilterTest {

    private static final String LOGGER_NAME = "cn.studykid.growthplanet.access";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private MockHttpServletResponse lastResponse;

    @BeforeEach
    void setUp() {
        // 测试里必须按 logger 名取回实例才能挂 ListAppender 断言输出，故此处不走 @Slf4j。
        // 生产代码统一用 @Slf4j（访问日志类用 @Slf4j(topic = LOGGER_NAME) 保持该 logger 名不变）。
        logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    /** 驱动一次请求：chain 内读取输入流（触发缓存）并返回 200。 */
    private String run(AccessLogProperties props, MockHttpServletRequest request) throws Exception {
        lastResponse = new MockHttpServletResponse();
        new AccessLogFilter(props, objectMapper).doFilter(request, lastResponse, (req, res) -> {
            req.getInputStream().readAllBytes();
            ((HttpServletResponse) res).setStatus(200);
        });
        List<ILoggingEvent> events = appender.list;
        return events.isEmpty() ? null : events.get(events.size() - 1).getFormattedMessage();
    }

    private String run(MockHttpServletRequest request) throws Exception {
        return run(new AccessLogProperties(), request);
    }

    private MockHttpServletRequest jsonRequest(String uri, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("User-Agent", "unit-test-agent");
        return request;
    }

    @Test
    void 记录基础字段与追踪标识() throws Exception {
        String line = run(jsonRequest("/api/mini/parent/menu-daily", "{\"dishIds\":[1,2]}"));
        assertNotNull(line, "应输出访问日志");
        assertAll(
                () -> assertTrue(line.contains("method=POST"), line),
                () -> assertTrue(line.contains("uri=/api/mini/parent/menu-daily"), line),
                () -> assertTrue(line.contains("status=200"), line),
                () -> assertTrue(line.contains("durationMs="), line),
                () -> assertTrue(line.contains("ua=unit-test-agent"), line),
                () -> assertTrue(line.contains("traceId="), line),
                () -> assertTrue(line.contains("spanId="), line),
                () -> assertTrue(line.contains("body={\"dishIds\":[1,2]}"), line)
        );
    }

    @Test
    void 追踪标识写入响应头且同一请求内一致() throws Exception {
        run(jsonRequest("/api/mini/any", "{}"));
        String traceId = lastResponse.getHeader("X-Trace-Id");
        String spanId = lastResponse.getHeader("X-Span-Id");
        assertAll(
                () -> assertNotNull(traceId, "X-Trace-Id 应存在"),
                () -> assertNotNull(spanId, "X-Span-Id 应存在"),
                () -> assertEquals(16, spanId.length(), "spanId 应为 16 位十六进制"),
                () -> assertTrue(appender.list.get(0).getFormattedMessage().contains("traceId=" + traceId))
        );
    }

    @Test
    void 复用已有的requestId作为traceId() throws Exception {
        MDC.put("requestId", "fixed-request-id");
        String line = run(jsonRequest("/api/mini/any", "{}"));
        assertAll(
                () -> assertTrue(line.contains("traceId=fixed-request-id"), line),
                () -> assertEquals("fixed-request-id", MDC.get("requestId"), "不应回收外部写入的 requestId")
        );
    }

    @Test
    void 请求体敏感字段脱敏() throws Exception {
        String line = run(jsonRequest("/api/mini/auth/wx-login",
                "{\"code\":\"wx-secret-code\",\"password\":\"p@ssw0rd\",\"accessToken\":\"tok-123\",\"dishIds\":[1,2]}"));
        assertNotNull(line);
        assertAll(
                () -> assertFalse(line.contains("wx-secret-code"), line),
                () -> assertFalse(line.contains("p@ssw0rd"), line),
                () -> assertFalse(line.contains("tok-123"), line),
                () -> assertTrue(line.contains("\"code\":\"***\""), line),
                () -> assertTrue(line.contains("\"dishIds\":[1,2]"), "非敏感字段应保留")
        );
    }

    @Test
    void 嵌套与数组中的敏感字段同样脱敏() throws Exception {
        String line = run(jsonRequest("/api/mini/any",
                "{\"children\":[{\"realName\":\"张三\",\"age\":8}],\"family\":{\"phone\":\"13800000000\",\"name\":\"李家\"}}"));
        assertAll(
                () -> assertFalse(line.contains("张三"), line),
                () -> assertFalse(line.contains("13800000000"), line),
                () -> assertTrue(line.contains("\"name\":\"李家\""), line)
        );
    }

    @Test
    void 查询串敏感参数脱敏() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mini/menu");
        request.setQueryString("code=abc123&menuDate=2026-10-01");
        String line = run(request);
        assertAll(
                () -> assertTrue(line.contains("code=***"), line),
                () -> assertTrue(line.contains("menuDate=2026-10-01"), "非敏感参数应保留")
        );
    }

    @Test
    void 排除路径不记录日志() throws Exception {
        assertNull(run(jsonRequest("/actuator/health", "{}")), "健康检查不应输出访问日志");
        assertNull(run(jsonRequest("/static/app.js", "{}")), "静态资源不应输出访问日志");
    }

    @Test
    void 慢请求以warn输出() throws Exception {
        AccessLogProperties props = new AccessLogProperties();
        props.setSlowThresholdMs(-1);
        run(props, jsonRequest("/api/mini/slow", "{}"));
        assertEquals(Level.WARN, appender.list.get(0).getLevel());
    }

    @Test
    void 正常请求以info输出() throws Exception {
        run(jsonRequest("/api/mini/ok", "{}"));
        assertEquals(Level.INFO, appender.list.get(0).getLevel());
    }

    @Test
    void 关闭后不记录任何日志() throws Exception {
        AccessLogProperties props = new AccessLogProperties();
        props.setEnabled(false);
        assertNull(run(props, jsonRequest("/api/mini/any", "{}")));
    }

    @Test
    void 超大请求体只记录字节数() throws Exception {
        AccessLogProperties props = new AccessLogProperties();
        props.setMaxBodyBytes(16);
        String line = run(props, jsonRequest("/api/mini/any", "0123456789012345678901234567890"));
        assertAll(
                () -> assertTrue(line.contains("<omitted:"), line),
                () -> assertFalse(line.contains("0123456789012345678901234567890"), line)
        );
    }

    @Test
    void 非json请求体不打印原文() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mini/upload");
        request.setContentType("text/plain");
        request.setContent("password=plaintext-secret".getBytes(StandardCharsets.UTF_8));
        String line = run(request);
        assertAll(
                () -> assertTrue(line.contains("<binary:"), line),
                () -> assertFalse(line.contains("plaintext-secret"), line)
        );
    }

    @Test
    void 非法json不回退打印原文() throws Exception {
        String line = run(jsonRequest("/api/mini/any", "{\"token\": broken"));
        assertAll(
                () -> assertTrue(line.contains("<unparsed:"), line),
                () -> assertFalse(line.contains("broken"), line)
        );
    }

    @Test
    void 请求体超长时截断() throws Exception {
        AccessLogProperties props = new AccessLogProperties();
        props.setMaxBodyLength(20);
        String line = run(props, jsonRequest("/api/mini/any", "{\"note\":\"" + "x".repeat(200) + "\"}"));
        assertTrue(line.contains("..."), "超长请求体应截断");
    }
}
