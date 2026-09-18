package cn.studykid.growthplanet.common.exception;

import cn.studykid.growthplanet.common.context.RequestContext;
import cn.studykid.growthplanet.common.result.Result;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.service.FailureAuditService;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：将所有异常统一转换为 {@link Result}，禁止裸异常外泄。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final FailureAuditService audit;

    public GlobalExceptionHandler(FailureAuditService audit) {
        this.audit = audit;
    }

    private ResponseEntity<Result<Void>> failure(ResultCode code) {
        auditFailure(code);
        return ResponseEntity.status(code.getHttpStatus()).body(Result.fail(code));
    }

    private void auditFailure(ResultCode code) {
        try {
            audit.record(code);
        } catch (RuntimeException ex) {
            log.error("failure_audit_unavailable requestId={} errorCode={} exceptionType={}",
                    RequestContext.requestId(), code.getCode(), ex.getClass().getSimpleName());
        }
    }

    @ExceptionHandler(AllowanceConfirmationException.class)
    public ResponseEntity<Result<cn.studykid.growthplanet.dto.response.AllowancePreviewResp>>
            handleAllowanceConfirmation(AllowanceConfirmationException ex) {
        ResultCode code = ResultCode.E011_ALLOWANCE_CONFIRM_REQUIRED;
        auditFailure(code);
        return ResponseEntity.status(code.getHttpStatus()).body(Result.of(code, ex.getPreview()));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException ex) {
        ResultCode rc = ex.getResultCode();
        return failure(rc);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException ex) {
        return failure(ResultCode.E400_INVALID_ARGUMENT);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return failure(ResultCode.E400_INVALID_ARGUMENT);
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public ResponseEntity<Result<Void>> handleMalformed(Exception ex) {
        return failure(ResultCode.E400_INVALID_ARGUMENT);
    }

    @ExceptionHandler({org.springframework.dao.DuplicateKeyException.class,
            org.springframework.dao.PessimisticLockingFailureException.class})
    public ResponseEntity<Result<Void>> handleConflict(Exception ex) {
        return failure(ResultCode.E007_CONCURRENCY_CONFLICT);
    }

    @ExceptionHandler(org.springframework.dao.DataAccessResourceFailureException.class)
    public ResponseEntity<Result<Void>> handleUnavailable(Exception ex) {
        return failure(ResultCode.E503_UNAVAILABLE);
    }

    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class})
    public ResponseEntity<Result<Void>> handleNotFound(Exception ex) {
        return failure(ResultCode.E404_NOT_FOUND);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotAllowed(Exception ex) {
        return failure(ResultCode.E405_METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleUnsupportedMedia(Exception ex) {
        return failure(ResultCode.E415_UNSUPPORTED_MEDIA);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex) {
        log.error("unexpected_failure requestId={} exceptionType={}",
                RequestContext.requestId(), ex.getClass().getSimpleName());
        return failure(ResultCode.E500_SYSTEM_ERROR);
    }
}
