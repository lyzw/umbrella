package cn.studykid.growthplanet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

/**
 * 访问日志配置属性（前缀 {@code access-log}）。
 * <p>请求体默认记录，但会先脱敏再落日志：儿童/家长数据（微信 code、手机号、真实姓名、凭证等）
 * 一律禁止以明文进入日志文件。非 JSON / 非表单的请求体只记录字节数，不记录内容。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "access-log")
public class AccessLogProperties {

    /** 是否启用访问日志；关闭后过滤器直接放行，不打任何日志。 */
    private boolean enabled = true;

    /** 慢请求阈值（毫秒）：耗时超过该值以 WARN 输出（便于告警），否则 INFO。 */
    private int slowThresholdMs = 1000;

    /** 单条日志里请求体 / 查询串的最大字符数，超出部分截断。 */
    private int maxBodyLength = 1024;

    /** 请求体超过该字节数则只记录字节数（避免大 payload 的解析与内存开销）。 */
    private int maxBodyBytes = 8192;

    /** 是否记录（已脱敏的）请求体；关闭后只记录方法 / URI / 状态 / 耗时。 */
    private boolean includeRequestBody = true;

    /** 不记录访问日志的路径（Ant 风格），用于排除健康检查与静态资源。 */
    private List<String> excludePatterns = List.of(
            "/actuator/**", "/actuator", "/error", "/favicon.ico",
            "/**/*.js", "/**/*.css", "/**/*.png", "/**/*.jpg", "/**/*.jpeg",
            "/**/*.gif", "/**/*.ico", "/**/*.svg", "/**/*.woff2");

    /**
     * 按「字段名完全相等」脱敏的键（不区分大小写）。
     * {@code code} 指微信登录 code，可换取会话，必须脱敏。
     */
    private Set<String> sensitiveExactKeys = Set.of(
            "code", "password", "passwd", "pwd", "token", "secret", "authorization",
            "sessionkey", "session_key", "phone", "mobile", "idcard", "id_card",
            "idno", "id_no", "realname", "real_name", "bankcard", "bank_card",
            "smscode", "verifycode", "captcha", "credential", "jscode", "js_code");

    /** 按「字段名包含」脱敏的键（不区分大小写），覆盖 accessToken / idToken 等组合名。 */
    private Set<String> sensitivePartialKeys = Set.of(
            "password", "passwd", "pwd", "token", "secret", "authorization",
            "session", "phone", "mobile", "idcard", "id_card", "credential", "captcha", "sms", "verify");
}
