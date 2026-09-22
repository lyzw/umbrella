package cn.studykid.growthplanet.dto.response;

import cn.studykid.growthplanet.entity.AuditLog;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * M6 审计日志响应行。
 * actorLabel 为操作人展示名：后台动作解析 sys_admin_user.username，C 端动作解析 usr_user.nickname，
 * 解析不到时回退为 "#id"。
 */
@Data
public class AdminAuditLogResp implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long actorUserId;
    private String actorLabel;
    private Long familyId;
    private String action;
    private String targetType;
    private Long targetId;
    private String ip;
    private String detail;
    private String requestId;
    private String result;
    private String errorCode;
    private LocalDateTime createTime;

    public static AdminAuditLogResp from(AuditLog log, String actorLabel) {
        AdminAuditLogResp resp = new AdminAuditLogResp();
        resp.setId(log.getId());
        resp.setActorUserId(log.getActorUserId());
        resp.setActorLabel(actorLabel);
        resp.setFamilyId(log.getFamilyId());
        resp.setAction(log.getAction());
        resp.setTargetType(log.getTargetType());
        resp.setTargetId(log.getTargetId());
        resp.setIp(log.getIp());
        resp.setDetail(log.getDetail());
        resp.setRequestId(log.getRequestId());
        resp.setResult(log.getResult());
        resp.setErrorCode(log.getErrorCode());
        resp.setCreateTime(log.getCreateTime());
        return resp;
    }
}
