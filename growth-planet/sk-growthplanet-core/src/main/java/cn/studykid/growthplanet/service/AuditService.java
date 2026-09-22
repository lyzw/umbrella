package cn.studykid.growthplanet.service;

/**
 * 审计服务：写入 sys_audit_log。
 */
public interface AuditService {

    /** 审计动作常量（LOGIN/ROLE/CREATE_FAMILY/JOIN/BIND/GRANT/REVOKE/EXPORT...）。 */
    String ACTION_LOGIN = "LOGIN";
    String ACTION_ROLE = "ROLE";
    String ACTION_CREATE_FAMILY = "CREATE_FAMILY";
    String ACTION_JOIN = "JOIN";
    String ACTION_BIND = "BIND";
    String ACTION_GRANT = "GRANT";
    String ACTION_REVOKE = "REVOKE";
    String ACTION_EXPORT = "EXPORT";

    /**
     * 记录一条审计日志。
     *
     * @param action      动作（见本接口常量）
     * @param actorUserId 操作人
     * @param familyId    家庭 ID（可为 null）
     * @param targetType  目标类型
     * @param targetId    目标 ID
     * @param ip          来源 IP（可为 null）
     * @param detail      备注
     */
    void record(String action, Long actorUserId, Long familyId, String targetType,
                Long targetId, String ip, String detail);
}
