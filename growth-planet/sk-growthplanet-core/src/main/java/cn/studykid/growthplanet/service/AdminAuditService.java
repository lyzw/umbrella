package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.constant.AdminResource;
import cn.studykid.growthplanet.common.context.AdminUserContext;
import cn.studykid.growthplanet.common.context.RequestContext;
import cn.studykid.growthplanet.common.enums.AdminAction;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.response.AdminAuditLogResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.entity.AuditLog;
import cn.studykid.growthplanet.entity.SysAdminUser;
import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.mapper.AuditLogMapper;
import cn.studykid.growthplanet.mapper.AdminUserMapper;
import cn.studykid.growthplanet.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * M6 操作日志与审计（只读查询 + 受控导出）。
 *
 * <p>权限口径（详设 §3.4）：操作日志/C 端关键操作 查看=全部角色；导出=SA,RA。
 * SA 走 {@code isSuperAdmin()} 旁路；其余角色经 {@code AdminUserContext.requirePerm} 校验。</p>
 *
 * <p>导出本身是敏感动作：默认脱敏（本表无明文个人字段，detail 原样输出）、
 * 上限 {@link #EXPORT_LIMIT} 行，且导出行为本身落一条 EXPORT 审计。</p>
 */
@Service
public class AdminAuditService {

    public static final String ACTION_LOG_EXPORT = "EXPORT";

    /** 「C 端关键操作」白名单：家庭/绑定、额度变更、数据出口三类（详设 M6·C端关键操作）。 */
    private static final Set<String> C_KEY_ACTIONS = Set.of(
            // 家庭与绑定
            "CREATE_FAMILY", "JOIN", "BIND", "GRANT", "REVOKE",
            // 额度变更
            "WALLET_GRANT", "ALLOWANCE_RULE",
            // 数据出口
            "EXPORT", "PRIVACY_DOWNLOAD", "PRIVACY_QUERY", "PRIVACY_TRANSITION");

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_LIMIT = 10_000;

    private final AuditLogMapper auditLogs;
    private final AdminUserMapper adminUsers;
    private final UserMapper users;
    private final AuditService audit;

    public AdminAuditService(AuditLogMapper auditLogs, AdminUserMapper adminUsers,
                             UserMapper users, AuditService audit) {
        this.auditLogs = auditLogs;
        this.adminUsers = adminUsers;
        this.users = users;
        this.audit = audit;
    }

    // ==================== 后台操作日志 ====================

    /** 后台操作日志分页查询（全量动作，含后台与 C 端）。 */
    public PageResp<AdminAuditLogResp> listAuditLogs(AuditFilter filter, int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.AUDIT_LOG, AdminAction.VIEW.code());
        Page p = normalizePage(page, pageSize);

        long total = auditLogs.selectCount(filter.toWrapper(null));
        LambdaQueryWrapper<AuditLog> pageQuery = filter.toWrapper(null);
        pageQuery.orderByDesc(AuditLog::getId)
                .last("LIMIT " + p.offset() + ", " + p.pageSize());
        List<AuditLog> rows = auditLogs.selectList(pageQuery);

        Map<Long, String> labels = resolveActorLabels(rows);
        List<AdminAuditLogResp> items = rows.stream()
                .map(l -> AdminAuditLogResp.from(l, labels.getOrDefault(
                        l.getActorUserId(), "#" + l.getActorUserId())))
                .toList();
        return new PageResp<>(items, total, p.page(), p.pageSize());
    }

    /** 审计详情（detail JSON 全量回放）。 */
    public AdminAuditLogResp detail(Long id) {
        AdminUserContext.requirePerm(AdminResource.AUDIT_LOG, AdminAction.VIEW.code());
        AuditLog log = auditLogs.selectById(id);
        if (log == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND, "审计记录不存在");
        }
        Map<Long, String> labels = resolveActorLabels(List.of(log));
        return AdminAuditLogResp.from(log, labels.getOrDefault(
                log.getActorUserId(), "#" + log.getActorUserId()));
    }

    // ==================== C 端关键操作 ====================

    /** C 端关键操作（家庭/绑定、额度变更、数据出口白名单），只读。 */
    public PageResp<AdminAuditLogResp> listCAuditLogs(AuditFilter filter, int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.C_AUDIT, AdminAction.VIEW.code());
        Page p = normalizePage(page, pageSize);

        long total = auditLogs.selectCount(filter.toWrapper(C_KEY_ACTIONS));
        LambdaQueryWrapper<AuditLog> pageQuery = filter.toWrapper(C_KEY_ACTIONS);
        pageQuery.orderByDesc(AuditLog::getId)
                .last("LIMIT " + p.offset() + ", " + p.pageSize());
        List<AuditLog> rows = auditLogs.selectList(pageQuery);

        Map<Long, String> labels = resolveActorLabels(rows);
        List<AdminAuditLogResp> items = rows.stream()
                .map(l -> AdminAuditLogResp.from(l, labels.getOrDefault(
                        l.getActorUserId(), "#" + l.getActorUserId())))
                .toList();
        return new PageResp<>(items, total, p.page(), p.pageSize());
    }

    // ==================== 导出 ====================

    /** 审计日志 CSV 导出（上限 1 万行，导出行为本身落审计）。返回 CSV 文本（含 BOM 前缀由控制器处理）。 */
    public String exportCsv(AuditFilter filter) {
        AdminUserContext.requirePerm(AdminResource.AUDIT_LOG, AdminAction.EXPORT.code());

        LambdaQueryWrapper<AuditLog> query = filter.toWrapper(null);
        query.orderByDesc(AuditLog::getId).last("LIMIT " + EXPORT_LIMIT);
        List<AuditLog> rows = auditLogs.selectList(query);
        Map<Long, String> labels = resolveActorLabels(rows);

        StringBuilder sb = new StringBuilder();
        sb.append("id,操作人,操作人ID,家庭ID,动作,目标类型,目标ID,IP,结果,错误码,请求ID,时间,明细\n");
        for (AuditLog l : rows) {
            sb.append(csv(l.getId())).append(',')
                    .append(csv(labels.getOrDefault(l.getActorUserId(), "#" + l.getActorUserId()))).append(',')
                    .append(csv(l.getActorUserId())).append(',')
                    .append(csv(l.getFamilyId())).append(',')
                    .append(csv(l.getAction())).append(',')
                    .append(csv(l.getTargetType())).append(',')
                    .append(csv(l.getTargetId())).append(',')
                    .append(csv(l.getIp())).append(',')
                    .append(csv(l.getResult())).append(',')
                    .append(csv(l.getErrorCode())).append(',')
                    .append(csv(l.getRequestId())).append(',')
                    .append(csv(l.getCreateTime() == null ? "" : l.getCreateTime().toString())).append(',')
                    .append(csv(l.getDetail())).append('\n');
        }

        audit.record(ACTION_LOG_EXPORT, AdminUserContext.adminId(), null, "sys_audit_log", null,
                RequestContext.ip(),
                "audit-log export; rows=" + rows.size()
                        + "; filter=" + filter.describe());
        return sb.toString();
    }

    // ==================== 内部工具 ====================

    /** 批量解析操作人展示名：先查后台账号表，未命中的再查 C 端用户表。 */
    private Map<Long, String> resolveActorLabels(List<AuditLog> rows) {
        Set<Long> ids = rows.stream()
                .map(AuditLog::getActorUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> labels = new java.util.HashMap<>();
        List<SysAdminUser> admins = adminUsers.selectBatchIds(ids);
        for (SysAdminUser a : admins) {
            labels.put(a.getId(), a.getName() + "（" + a.getUsername() + "）");
            ids.remove(a.getId());
        }
        if (!ids.isEmpty()) {
            List<User> cUsers = users.selectBatchIds(ids);
            for (User u : cUsers) {
                labels.put(u.getId(), u.getNickname() == null ? ("C端#" + u.getId()) : u.getNickname());
            }
        }
        return labels;
    }

    private Page normalizePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "分页参数非法");
        }
        return new Page(page, pageSize, (long) (page - 1) * pageSize);
    }

    private static String csv(Object v) {
        if (v == null) {
            return "";
        }
        String s = v.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    /** 筛选参数载体。 */
    public record AuditFilter(Long actorUserId, String action, String targetType,
                              String result, LocalDate begin, LocalDate end) {

        LambdaQueryWrapper<AuditLog> toWrapper(Set<String> actionWhitelist) {
            LambdaQueryWrapper<AuditLog> q = new LambdaQueryWrapper<>();
            if (actorUserId != null) {
                q.eq(AuditLog::getActorUserId, actorUserId);
            }
            if (action != null && !action.isBlank()) {
                q.eq(AuditLog::getAction, action.trim());
            }
            if (actionWhitelist != null) {
                q.in(AuditLog::getAction, actionWhitelist);
            }
            if (targetType != null && !targetType.isBlank()) {
                q.eq(AuditLog::getTargetType, targetType.trim());
            }
            if (result != null && !result.isBlank()) {
                q.eq(AuditLog::getResult, result.trim());
            }
            if (begin != null) {
                q.ge(AuditLog::getCreateTime, begin.atStartOfDay());
            }
            if (end != null) {
                // 含 end 当天：用次日 0 点开区间
                q.lt(AuditLog::getCreateTime, end.plusDays(1).atStartOfDay());
            }
            return q;
        }

        String describe() {
            List<String> parts = new ArrayList<>();
            if (actorUserId != null) parts.add("actor=" + actorUserId);
            if (action != null && !action.isBlank()) parts.add("action=" + action.trim());
            if (targetType != null && !targetType.isBlank()) parts.add("targetType=" + targetType.trim());
            if (result != null && !result.isBlank()) parts.add("result=" + result.trim());
            if (begin != null) parts.add("begin=" + begin);
            if (end != null) parts.add("end=" + end);
            return String.join(";", parts);
        }
    }

    private record Page(int page, int pageSize, long offset) {
    }
}
