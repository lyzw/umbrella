package com.growthplanet.common.result;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一错误码（E-001 ~ E-010，外加系统异常兜底 E-500）。
 * {@code GlobalExceptionHandler} 以 {@link #httpStatus} 与 {@link #code} 组装 {@link Result}。
 */
@Getter
public enum ResultCode {

    E001_NO_WX_AUTH(1001, HttpStatus.UNAUTHORIZED, "未登录或登录失效"),
    E002_PROFILE_INCOMPLETE(1002, HttpStatus.OK, "档案未补全"),
    E003_INVITE_CODE_EXPIRED(1003, HttpStatus.BAD_REQUEST, "邀请码过期或无效"),
    E004_GUARDIAN_VERIFY_FAILED(1004, HttpStatus.BAD_REQUEST, "监护人核验失败（年龄不足18岁）"),
    E005_SUBMIT_TIMEOUT(1005, HttpStatus.CONFLICT, "提交超时"),
    E006_BALANCE_INSUFFICIENT(1006, HttpStatus.CONFLICT, "虚拟余额不足"),
    E007_CONCURRENCY_CONFLICT(1007, HttpStatus.CONFLICT, "并发冲突"),
    E008_NOTICE_FAILED(1008, HttpStatus.OK, "通知失败"),
    E009_FORBIDDEN(1009, HttpStatus.FORBIDDEN, "越权访问"),
    E010_CONSENT_REVOKED(1010, HttpStatus.CONFLICT, "监护人撤回同意，能力降级"),
    E500_SYSTEM_ERROR(9999, HttpStatus.INTERNAL_SERVER_ERROR, "系统异常");

    /** 业务码（数值）。 */
    private final int code;

    /** 对应 HTTP 状态码。 */
    private final HttpStatus httpStatus;

    /** 默认提示文案。 */
    private final String message;

    ResultCode(int code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
