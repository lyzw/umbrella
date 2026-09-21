package cn.studykid.growthplanet.common.result;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一错误码（E-001 ~ E-010，外加系统异常兜底 E-500）。
 * {@code GlobalExceptionHandler} 以 {@link #httpStatus} 与 {@link #code} 组装 {@link Result}。
 */
@Getter
public enum ResultCode {

    E001_NO_WX_AUTH(1001, HttpStatus.UNAUTHORIZED, "未登录或登录失效"),
    E002_PROFILE_INCOMPLETE(1002, HttpStatus.CONFLICT, "档案未补全"),
    E003_INVITE_CODE_EXPIRED(1003, HttpStatus.BAD_REQUEST, "邀请码过期或无效"),
    E004_GUARDIAN_VERIFY_FAILED(1004, HttpStatus.BAD_REQUEST, "监护人声明不满足要求"),
    E005_SUBMIT_TIMEOUT(1005, HttpStatus.GATEWAY_TIMEOUT, "提交超时"),
    E006_BALANCE_INSUFFICIENT(1006, HttpStatus.CONFLICT, "虚拟余额不足"),
    E007_CONCURRENCY_CONFLICT(1007, HttpStatus.CONFLICT, "并发冲突"),
    E008_NOTICE_FAILED(1008, HttpStatus.SERVICE_UNAVAILABLE, "通知失败"),
    E009_FORBIDDEN(1009, HttpStatus.FORBIDDEN, "越权访问"),
    E010_CONSENT_REVOKED(1010, HttpStatus.CONFLICT, "同意缺失、失效或已撤回"),
    E011_ALLOWANCE_CONFIRM_REQUIRED(1011, HttpStatus.CONFLICT, "额度已变化，请重新确认"),
    E012_IDEMPOTENCY_CONFLICT(1012, HttpStatus.CONFLICT, "请求标识已用于其他内容"),
    E013_DAILY_LIMIT_REACHED(1013, HttpStatus.CONFLICT, "今日打卡已达上限"),
    E400_INVALID_ARGUMENT(1400, HttpStatus.BAD_REQUEST, "参数不合法"),
    E404_NOT_FOUND(1404, HttpStatus.NOT_FOUND, "资源不存在"),
    E405_METHOD_NOT_ALLOWED(1405, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    E410_GONE(1410, HttpStatus.GONE, "资源已过期"),
    E415_UNSUPPORTED_MEDIA(1415, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求内容类型不支持"),
    E503_UNAVAILABLE(1503, HttpStatus.SERVICE_UNAVAILABLE, "服务暂不可用"),
    E500_SYSTEM_ERROR(1500, HttpStatus.INTERNAL_SERVER_ERROR, "系统异常");

    /** 业务错误码（E-xxx 字符串）。 */
    private final String code;

    /** 对应 HTTP 状态码。 */
    private final HttpStatus httpStatus;

    /** 默认提示文案。 */
    private final String message;

    ResultCode(int code, HttpStatus httpStatus, String message) {
        this.code = "E-%03d".formatted(code - 1000);
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
