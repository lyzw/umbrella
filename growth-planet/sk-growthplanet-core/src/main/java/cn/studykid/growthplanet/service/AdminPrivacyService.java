package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.constant.AdminResource;
import cn.studykid.growthplanet.common.context.AdminUserContext;
import cn.studykid.growthplanet.common.enums.AdminAction;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.AdminComplianceCheckReq;
import cn.studykid.growthplanet.dto.request.AdminPrivacyRejectReq;
import cn.studykid.growthplanet.dto.request.AdminPrivacyVerifyReq;
import cn.studykid.growthplanet.dto.response.AdminComplianceItemResp;
import cn.studykid.growthplanet.dto.response.AdminConsentResp;
import cn.studykid.growthplanet.dto.response.AdminPrivacyReqResp;
import cn.studykid.growthplanet.dto.response.AdminVerificationResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.entity.ChildProfile;
import cn.studykid.growthplanet.entity.ConsentLog;
import cn.studykid.growthplanet.entity.PrivacyRequest;
import cn.studykid.growthplanet.entity.PrivacyVerification;
import cn.studykid.growthplanet.mapper.ChildProfileMapper;
import cn.studykid.growthplanet.mapper.ConsentLogMapper;
import cn.studykid.growthplanet.mapper.PrivacyRequestMapper;
import cn.studykid.growthplanet.mapper.PrivacyVerificationMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运营后台 · 合规与隐私中心（里程碑 A 批次 4 / M5）。
 * <p>覆盖：同意留痕查询、隐私工单列表（CP 隔离）、CP 核验闭环、驳回、核验记录、合规清单勾检。
 * 严格按规划文档 D2 复用 C 端既有状态机枚举（RECEIVED/PROCESSING/READY/FAILED/COMPLETED/REJECTED），
 * 不另起一套状态机，避免与 {@code PrivacyWorkflowService} 双写冲突。
 * 数据隔离：隐私域接口仅对持有「隐私工单:view」的角色开放（SA/CP/RA）；
 * 孩子全名等 PII 仅 CP/SA 可见，RA 仅持 childId（数据最小化，见 AdminPrivacyReqResp）。</p>
 */
@Service
@Transactional
public class AdminPrivacyService {

    private static final int MAX_PAGE_SIZE = 100;
    /** 隐私工单可核验/驳回的源状态。 */
    private static final java.util.Set<String> VERIFIABLE = java.util.Set.of("RECEIVED");
    private static final java.util.Set<String> REJECTABLE = java.util.Set.of("RECEIVED", "PROCESSING");

    private final ConsentLogMapper consents;
    private final PrivacyRequestMapper requests;
    private final PrivacyVerificationMapper verifications;
    private final ChildProfileMapper profiles;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public AdminPrivacyService(ConsentLogMapper consents, PrivacyRequestMapper requests,
            PrivacyVerificationMapper verifications, ChildProfileMapper profiles, AuditService audit,
            JdbcTemplate jdbc) {
        this.consents = consents;
        this.requests = requests;
        this.verifications = verifications;
        this.profiles = profiles;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    // ==================== 同意留痕 ====================

    public PageResp<AdminConsentResp> listConsents(Long childId, String consentType, String action,
            int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.CONSENT, AdminAction.VIEW.code());
        Pg p = normalizePage(page, pageSize);
        QueryWrapper<ConsentLog> query = new QueryWrapper<ConsentLog>()
                .eq(childId != null, "child_id", childId)
                .eq(consentType != null, "consent_type", consentType)
                .eq(action != null, "action", action);
        long total = consents.selectCount(query);
        List<ConsentLog> rows = consents.selectList(query.orderByDesc("id")
                .last("LIMIT " + p.offset() + ", " + p.pageSize()));
        boolean fullPii = AdminUserContext.isSuperAdmin()
                || AdminUserContext.hasPerm(AdminResource.PRIVACY_TICKET, AdminAction.APPROVE.code());
        Map<Long, String> names = childNames(rows.stream().map(ConsentLog::getChildId).toList());
        List<AdminConsentResp> items = rows.stream().map(c -> AdminConsentResp.builder()
                .id(c.getId()).userId(c.getUserId()).childId(c.getChildId())
                .childName(fullPii ? names.get(c.getChildId()) : null)
                .familyId(c.getFamilyId()).consentType(c.getConsentType()).action(c.getAction())
                .version(c.getVersion()).selfReportedAge(c.getSelfReportedAge())
                .guardianStatus(c.getGuardianStatus()).signedAt(c.getSignedAt()).expireAt(c.getExpireAt())
                .createTime(c.getCreateTime()).build()).toList();
        return new PageResp<>(items, total, p.page(), p.pageSize());
    }

    // ==================== 隐私工单 ====================

    public PageResp<AdminPrivacyReqResp> listPrivacyRequests(Long childId, String requestType, String status,
            int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.PRIVACY_TICKET, AdminAction.VIEW.code());
        Pg p = normalizePage(page, pageSize);
        QueryWrapper<PrivacyRequest> query = new QueryWrapper<PrivacyRequest>()
                .eq(childId != null, "child_id", childId)
                .eq(requestType != null, "request_type", requestType)
                .eq(status != null, "status", status);
        long total = requests.selectCount(query);
        List<PrivacyRequest> rows = requests.selectList(query.orderByDesc("id")
                .last("LIMIT " + p.offset() + ", " + p.pageSize()));
        // PII 隔离：仅 CP/SA 可见孩子全名；RA 不展示（仅 childId）。
        boolean fullPii = AdminUserContext.isSuperAdmin()
                || AdminUserContext.hasPerm(AdminResource.PRIVACY_TICKET, AdminAction.APPROVE.code());
        Map<Long, String> names = fullPii
                ? childNames(rows.stream().map(PrivacyRequest::getChildId).toList())
                : Map.of();
        List<AdminPrivacyReqResp> items = rows.stream().map(r -> AdminPrivacyReqResp.builder()
                .id(r.getId()).requesterId(r.getRequesterId()).childId(r.getChildId())
                .childName(fullPii ? names.get(r.getChildId()) : null)
                .familyId(r.getFamilyId()).requestType(r.getRequestType()).status(r.getStatus())
                .dueAt(r.getDueAt()).verifiedAt(r.getVerifiedAt()).resultRef(r.getResultRef())
                .expiresAt(r.getExpiresAt()).errorCode(r.getErrorCode()).operatorId(r.getOperatorId())
                .version(r.getVersion()).createTime(r.getCreateTime()).build()).toList();
        return new PageResp<>(items, total, p.page(), p.pageSize());
    }

    /**
     * CP 核验闭环（D2：待核验 RECEIVED → CP 核验通过 → PROCESSING）。
     * <p>手写乐观锁（项目约定：UpdateWrapper.eq("version", before).setVersion(before+1)，0 行 → E007）。
     * 核验码经 SHA-256 哈希后写入 sys_privacy_verification.code_hash，明文不落库。</p>
     */
    public AdminPrivacyReqResp verify(Long id, AdminPrivacyVerifyReq req) {
        AdminUserContext.requirePerm(AdminResource.PRIVACY_TICKET, AdminAction.APPROVE.code());
        PrivacyRequest reference = requests.selectById(id);
        if (reference == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        if (!VERIFIABLE.contains(reference.getStatus())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT,
                    "工单状态不可核验：" + reference.getStatus());
        }
        long now = System.currentTimeMillis();
        int updated = requests.update(new UpdateWrapper<PrivacyRequest>()
                .eq("id", id).eq("version", reference.getVersion())
                .set("status", "PROCESSING").set("version", reference.getVersion() + 1)
                .set("operator_id", AdminUserContext.adminId()).set("verified_at", now));
        if (updated == 0) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        PrivacyVerification v = new PrivacyVerification();
        v.setCodeHash(sha256Hex(req.getCode()));
        v.setRequesterId(reference.getRequesterId());
        v.setRequestId(id);
        v.setVerifiedAt(now);
        verifications.insert(v);
        audit.record("PRIVACY_VERIFY", AdminUserContext.adminId(), reference.getFamilyId(),
                "PRIVACY_REQUEST", id, null,
                "status=PROCESSING;codeHash=" + v.getCodeHash().substring(0, 8) + "****");
        return buildRow(reference.getChildId(), id, "PROCESSING", reference.getFamilyId(),
                reference.getRequesterId(), reference.getVersion() + 1, now, null, null, null);
    }

    /** CP 驳回（D2：RECEIVED/PROCESSING → REJECTED，原因落 error_code + 审计）。 */
    public AdminPrivacyReqResp reject(Long id, AdminPrivacyRejectReq req) {
        AdminUserContext.requirePerm(AdminResource.PRIVACY_TICKET, AdminAction.APPROVE.code());
        PrivacyRequest reference = requests.selectById(id);
        if (reference == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        if (!REJECTABLE.contains(reference.getStatus())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT,
                    "工单状态不可驳回：" + reference.getStatus());
        }
        int updated = requests.update(new UpdateWrapper<PrivacyRequest>()
                .eq("id", id).eq("version", reference.getVersion())
                .set("status", "REJECTED").set("version", reference.getVersion() + 1)
                .set("error_code", req.getReason()).set("operator_id", AdminUserContext.adminId()));
        if (updated == 0) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        audit.record("PRIVACY_REJECT", AdminUserContext.adminId(), reference.getFamilyId(),
                "PRIVACY_REQUEST", id, null, "reason=" + req.getReason());
        return buildRow(reference.getChildId(), id, "REJECTED", reference.getFamilyId(),
                reference.getRequesterId(), reference.getVersion() + 1, null, null, req.getReason(),
                AdminUserContext.adminId());
    }

    public PageResp<AdminVerificationResp> listVerifications(Long requestId, int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.VERIFY, AdminAction.VIEW.code());
        Pg p = normalizePage(page, pageSize);
        QueryWrapper<PrivacyVerification> query = new QueryWrapper<PrivacyVerification>()
                .eq(requestId != null, "request_id", requestId);
        long total = verifications.selectCount(query);
        List<PrivacyVerification> rows = verifications.selectList(query.orderByDesc("id")
                .last("LIMIT " + p.offset() + ", " + p.pageSize()));
        List<AdminVerificationResp> items = rows.stream().map(v -> AdminVerificationResp.builder()
                .id(v.getId())
                .codeHashMasked(v.getCodeHash() == null ? null
                        : v.getCodeHash().substring(0, 8) + "****")
                .requesterId(v.getRequesterId()).requestId(v.getRequestId())
                .verifiedAt(toLocal(v.getVerifiedAt())).build()).toList();
        return new PageResp<>(items, total, p.page(), p.pageSize());
    }

    // ==================== 合规清单 ====================

    public List<AdminComplianceItemResp> listCompliance() {
        AdminUserContext.requirePerm(AdminResource.COMPLIANCE, AdminAction.VIEW.code());
        List<SysComplianceItem> rows = complianceSelectAll();
        return rows.stream().map(r -> AdminComplianceItemResp.builder()
                .itemKey(r.itemKey).itemText(r.itemText).checked(r.checked)
                .checkedBy(r.checkedBy).checkedAt(r.checkedAt).build()).toList();
    }

    /** 按 item_key 切换勾检状态（config 权限）。 */
    public AdminComplianceItemResp checkCompliance(AdminComplianceCheckReq req) {
        AdminUserContext.requirePerm(AdminResource.COMPLIANCE, AdminAction.CONFIG.code());
        SysComplianceItem row = complianceSelectByKey(req.getItemKey());
        if (row == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND, "未找到合规清单项：" + req.getItemKey());
        }
        boolean checked = req.getChecked() == null || req.getChecked();
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update(
                "UPDATE sys_compliance_checklist SET checked = ?, checked_by = ?, checked_at = ? "
                        + "WHERE id = ? AND item_key = ? AND delete_at = 0",
                checked ? 1 : 0, checked ? AdminUserContext.adminId() : null,
                checked ? java.sql.Timestamp.valueOf(now) : null, row.id, row.itemKey);
        if (updated == 0) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        audit.record("COMPLIANCE_CHECK", AdminUserContext.adminId(), null, "COMPLIANCE_CHECKLIST",
                row.id, null, "itemKey=" + row.itemKey + ";checked=" + checked);
        return AdminComplianceItemResp.builder().itemKey(row.itemKey).itemText(row.itemText)
                .checked(checked).checkedBy(checked ? AdminUserContext.adminId() : null)
                .checkedAt(checked ? now : null).build();
    }

    // ==================== 内部辅助 ====================

    private AdminPrivacyReqResp buildRow(Long childId, Long id, String status, Long familyId,
            Long requesterId, Integer version, Long verifiedAt, Long dueAt, String errorCode,
            Long operatorId) {
        boolean fullPii = AdminUserContext.isSuperAdmin()
                || AdminUserContext.hasPerm(AdminResource.PRIVACY_TICKET, AdminAction.APPROVE.code());
        String name = fullPii ? childNames(List.of(childId)).get(childId) : null;
        return AdminPrivacyReqResp.builder().id(id).requesterId(requesterId).childId(childId)
                .childName(name).familyId(familyId).status(status).dueAt(dueAt).verifiedAt(verifiedAt)
                .errorCode(errorCode).operatorId(operatorId).version(version).build();
    }

    private Map<Long, String> childNames(List<Long> childIds) {
        if (childIds == null || childIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = childIds.stream().distinct().toList();
        List<ChildProfile> profilesList = profiles.selectList(
                new QueryWrapper<ChildProfile>().in("user_id", distinct));
        return profilesList.stream().collect(Collectors.toMap(
                ChildProfile::getUserId, ChildProfile::getNickname, (a, b) -> a));
    }

    private Pg normalizePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "分页参数非法");
        }
        return new Pg(page, pageSize, (long) (page - 1) * pageSize);
    }

    private LocalDateTime toLocal(Long millis) {
        return millis == null ? null : LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault());
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BizException(ResultCode.E500_SYSTEM_ERROR, "哈希算法不可用");
        }
    }

    // ==================== 合规清单表直接访问 ====================
    // 该表极轻量（个位数静态项），未单独建 Mapper，复用 jdbc 透传；列名与 v010 DDL 一致。

    private record SysComplianceItem(Long id, String itemKey, String itemText, boolean checked,
            Long checkedBy, LocalDateTime checkedAt) {
    }

    /** 轻量分页载体：record 自动生成 page()/pageSize()/offset() 访问器，规避 MP Page API 版本差异。 */
    private record Pg(int page, int pageSize, long offset) {
    }

    private List<SysComplianceItem> complianceSelectAll() {
        return jdbc.query(
                "SELECT id, item_key, item_text, checked, checked_by, checked_at FROM sys_compliance_checklist "
                        + "WHERE delete_at = 0 ORDER BY id",
                (rs, i) -> new SysComplianceItem(
                        rs.getLong("id"), rs.getString("item_key"), rs.getString("item_text"),
                        rs.getInt("checked") == 1,
                        rs.getObject("checked_by") == null ? null : rs.getLong("checked_by"),
                        rs.getTimestamp("checked_at") == null ? null
                                : rs.getTimestamp("checked_at").toLocalDateTime()));
    }

    private SysComplianceItem complianceSelectByKey(String itemKey) {
        return jdbc.query(
                "SELECT id, item_key, item_text, checked, checked_by, checked_at FROM sys_compliance_checklist "
                        + "WHERE delete_at = 0 AND item_key = ?",
                (rs, i) -> new SysComplianceItem(
                        rs.getLong("id"), rs.getString("item_key"), rs.getString("item_text"),
                        rs.getInt("checked") == 1,
                        rs.getObject("checked_by") == null ? null : rs.getLong("checked_by"),
                        rs.getTimestamp("checked_at") == null ? null
                                : rs.getTimestamp("checked_at").toLocalDateTime()),
                itemKey).stream().findFirst().orElse(null);
    }
}
