package com.growthplanet.service.impl;

import com.growthplanet.entity.AuditLog;
import com.growthplanet.mapper.AuditLogMapper;
import com.growthplanet.service.AuditService;
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
        log.setIp(ip);
        log.setDetail(detail);
        auditLogMapper.insert(log);
    }
}
