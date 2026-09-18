package com.growthplanet.common.exception;

import com.growthplanet.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：携带 {@link ResultCode}，由 {@code GlobalExceptionHandler} 统一转换为 {@link com.growthplanet.common.result.Result}。
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BizException(ResultCode resultCode, String detail) {
        super(resultCode.getMessage() + " : " + detail);
        this.resultCode = resultCode;
    }
}
