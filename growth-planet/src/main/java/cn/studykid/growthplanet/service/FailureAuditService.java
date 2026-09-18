package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.RequestContext;
import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.entity.AuditLog;
import cn.studykid.growthplanet.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FailureAuditService {
    private final AuditLogMapper logs;

    public FailureAuditService(AuditLogMapper logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ResultCode code) {
        AuditLog audit = new AuditLog();
        audit.setAction("REQUEST_FAILED");
        audit.setActorUserId(UserContext.userId());
        audit.setFamilyId(UserContext.familyId());
        audit.setIp(RequestContext.ip());
        audit.setRequestId(RequestContext.requestId());
        audit.setResult(code.getHttpStatus().is5xxServerError() ? "FAILED" : "DENIED");
        audit.setErrorCode(code.getCode());
        // 不记录请求正文、SQL 异常详情或字段值，避免未经同意的数据进入日志。
        logs.insert(audit);
    }
}
