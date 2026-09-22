package cn.studykid.growthplanet.common.result;

import cn.studykid.growthplanet.common.context.RequestContext;
import cn.studykid.growthplanet.common.exception.BizException;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体。所有 Controller 必须返回 {@code Result<T>}，禁止裸返回对象或 Map。
 *
 * <p>约定：{@code code=0} 表示成功；非 0 见 {@link ResultCode}。成功用 {@link #ok(Object)}，
 * 业务失败统一抛 {@link BizException} 由
 * {@code GlobalExceptionHandler} 转换为 {@code Result}。</p>
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务码：0=成功，其余见 ResultCode。 */
    private Object code;

    private String requestId;

    /** 提示信息。 */
    private String message;

    /** 业务数据。 */
    private T data;

    public static <T> Result<T> ok() {
        return of(0, "success", null);
    }

    public static <T> Result<T> ok(T data) {
        return of(0, "success", data);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return of(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 以指定业务码 + 数据构造响应（用于如撤回降级 E-010 仍带 data 的场景）。 */
    public static <T> Result<T> of(ResultCode resultCode, T data) {
        return of(resultCode.getCode(), resultCode.getMessage(), data);
    }

    private static <T> Result<T> of(Object code, String message, T data) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        result.data = data;
        result.requestId = RequestContext.requestId();
        return result;
    }
}
