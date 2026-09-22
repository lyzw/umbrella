package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.common.context.RequestContext;
import cn.studykid.growthplanet.entity.AuditLog;
import cn.studykid.growthplanet.mapper.AuditLogMapper;
import cn.studykid.growthplanet.service.AuditService;
import org.springframework.stereotype.Service;

/**
 * 审计服务实现：写入 sys_audit_log（仅插入）。
 */
@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuditServiceImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void record(String action, Long actorUserId, Long familyId, String targetType,
                      Long targetId, String ip, String detail) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setActorUserId(actorUserId);
        log.setFamilyId(familyId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setIp(ip == null ? RequestContext.ip() : ip);
        log.setDetail(detail);
        log.setRequestId(RequestContext.requestId());
        log.setResult("SUCCESS");
        auditLogMapper.insert(log);
    }
}
