package cn.studykid.growthplanet.common.exception;

import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：携带 {@link ResultCode}，由 {@code GlobalExceptionHandler} 统一转换为 {@link Result}。
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ResultCode resultCode;

    /** 具体原因（可空）。仅用于对外提示，不参与 equals/日志分类。 */
    private final String detail;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.detail = null;
    }

    public BizException(ResultCode resultCode, String detail) {
        super(resultCode.getMessage() + " : " + detail);
        this.resultCode = resultCode;
        this.detail = detail;
    }

    /**
     * 面向调用方的可读提示：带 detail 时拼在业务码文案之后，便于前端直接展示。
     *
     * <p>{@link #getMessage()} 保持 {@code "业务文案 : 详情"} 形式（日志友好），
     * 而 API 响应统一走本方法，避免把日志分隔符「 : 」暴露给终端用户。</p>
     */
    public String displayMessage() {
        return detail == null || detail.isBlank() ? resultCode.getMessage() : resultCode.getMessage() + "：" + detail;
    }
}
