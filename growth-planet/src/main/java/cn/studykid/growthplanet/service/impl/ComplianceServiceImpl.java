package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.dto.request.ConsentReq;
import cn.studykid.growthplanet.dto.request.DataExportReq;
import cn.studykid.growthplanet.dto.request.RevokeConsentReq;
import cn.studykid.growthplanet.dto.response.ConsentResp;
import cn.studykid.growthplanet.dto.response.DataExportResp;
import cn.studykid.growthplanet.service.AuditService;
import cn.studykid.growthplanet.service.ChildAuthorizationService;
import cn.studykid.growthplanet.service.ComplianceService;
import cn.studykid.growthplanet.service.NoticeService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.config.ComplianceProperties;
import com.growthplanet.dto.request.*;
import com.growthplanet.dto.response.*;
import cn.studykid.growthplanet.entity.ConsentLog;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.entity.PrivacyRequest;
import cn.studykid.growthplanet.mapper.ConsentLogMapper;
import cn.studykid.growthplanet.mapper.PrivacyRequestMapper;
import com.growthplanet.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Transactional
public class ComplianceServiceImpl implements ComplianceService {
    private final ConsentLogMapper consents;
    private final PrivacyRequestMapper requests;
    private final ChildAuthorizationService authorization;
    private final ComplianceProperties policy;
    private final AuditService audit;
    private final NoticeService notices;

    public ComplianceServiceImpl(ConsentLogMapper consents, PrivacyRequestMapper requests,
            ChildAuthorizationService authorization, ComplianceProperties policy,
            AuditService audit, NoticeService notices) {
        this.consents = consents;
        this.requests = requests;
        this.authorization = authorization;
        this.policy = policy;
        this.audit = audit;
        this.notices = notices;
    }

    @Override
    public ConsentResp getConsent(Long childId, String consentType) {
        requireType(consentType);
        FamilyMember member = authorization.lockChild(UserContext.familyId(), childId);
        ConsentLog latest = authorization.latest(member, consentType);
        String status = latest == null ? "NONE" : "REVOKE".equals(latest.getAction()) ? "REVOKED"
                : !policy.getAgreementVersion().equals(latest.getVersion()) ? "VERSION_CHANGED"
                : latest.getExpireAt() == null || latest.getExpireAt() <= System.currentTimeMillis() ? "EXPIRED" : "GRANTED";
        audit.record("CONSENT_QUERY", UserContext.userId(), member.getFamilyId(),
                "CHILD", childId, null, "status=" + status);
        return ConsentResp.builder().agreementText(policy.getAgreementText())
                .version(policy.getAgreementVersion()).currentStatus(status)
                .guardianStatus(latest == null ? "UNVERIFIED" : latest.getGuardianStatus()).build();
    }

    @Override
    public ConsentResp submitConsent(ConsentReq req) {
        policy.requireCollection();
        requireType(req.getConsentType());
        if (!Boolean.TRUE.equals(req.getAgreed()) || req.getSelfReportedAge() == null
                || req.getSelfReportedAge() < 18 || req.getSelfReportedAge() > 120) {
            throw new BizException(ResultCode.E004_GUARDIAN_VERIFY_FAILED);
        }
        if (!policy.getAgreementVersion().equals(req.getVersion())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "协议已更新");
        }
        FamilyMember member = authorization.lockChild(UserContext.familyId(), req.getChildId());
        if (!member.getId().equals(req.getApplyId()) || !"PENDING".equals(member.getBindStatus())
                && !"BOUND".equals(member.getBindStatus())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "申请不匹配或已拒绝");
        }
        ConsentLog latest = authorization.latest(member, req.getConsentType());
        if (latest == null || !"GRANT".equals(latest.getAction()) || !req.getVersion().equals(latest.getVersion())
                || latest.getExpireAt() == null || latest.getExpireAt() <= System.currentTimeMillis()) {
            latest = newLog(member, req.getConsentType(), req.getVersion(), "GRANT");
            latest.setSelfReportedAge(req.getSelfReportedAge());
            latest.setGuardianStatus("SELF_ATTESTED");
            latest.setExpireAt(System.currentTimeMillis() + 365L * 24 * 3600 * 1000);
            consents.insert(latest);
            audit.record(AuditService.ACTION_GRANT, UserContext.userId(), member.getFamilyId(),
                    "CONSENT", latest.getId(), null, "version=" + latest.getVersion()
                            + ";applicationVersion=" + member.getApplicationVersion());
        }
        return ConsentResp.builder().version(latest.getVersion()).currentStatus("GRANTED")
                .guardianStatus(latest.getGuardianStatus()).build();
    }

    @Override
    public String revokeConsent(RevokeConsentReq req) {
        requireType(req.getConsentType());
        FamilyMember member = authorization.lockChild(UserContext.familyId(), req.getChildId());
        ConsentLog latest = authorization.latest(member, req.getConsentType());
        if (latest == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND, "无对应同意记录");
        }
        if (!latest.getVersion().equals(req.getVersion())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "同意版本不匹配");
        }
        if (!"REVOKE".equals(latest.getAction())) {
            ConsentLog revoke = newLog(member, req.getConsentType(), req.getVersion(), "REVOKE");
            revoke.setGuardianStatus(latest.getGuardianStatus());
            revoke.setSelfReportedAge(latest.getSelfReportedAge());
            revoke.setExpireAt(System.currentTimeMillis());
            consents.insert(revoke);
            notices.cancelSubscriptions(member.getFamilyId(), member.getUserId());
            audit.record(AuditService.ACTION_REVOKE, UserContext.userId(), member.getFamilyId(),
                    "CONSENT", revoke.getId(), null, "version=" + revoke.getVersion());
        }
        // 撤回停止受该同意授权的处理，不退出家长会话，保留查询和权利请求。
        return "REVOKED";
    }

    @Override
    public DataExportResp dataExport(DataExportReq req, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "Idempotency-Key 格式不合法");
        }
        FamilyMember member = authorization.lockChild(UserContext.familyId(), req.getChildId());
        if (!"BOUND".equals(member.getBindStatus())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "仅支持已绑定儿童");
        }
        String key = idempotencyKey == null ? UUID.randomUUID().toString() : idempotencyKey;
        String hash = hash("EXPORT:" + member.getFamilyId() + ":" + req.getChildId());
        PrivacyRequest existing = requests.selectOne(new QueryWrapper<PrivacyRequest>()
                .eq("requester_id", UserContext.userId()).eq("request_type", "EXPORT")
                .eq("idempotency_key", key).last("FOR UPDATE"));
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash())) {
                throw new BizException(ResultCode.E012_IDEMPOTENCY_CONFLICT);
            }
            return response(existing);
        }
        PrivacyRequest request = new PrivacyRequest();
        request.setRequesterId(UserContext.userId());
        request.setFamilyId(member.getFamilyId());
        request.setChildId(req.getChildId());
        request.setRequestType("EXPORT");
        request.setIdempotencyKey(key);
        request.setRequestHash(hash);
        request.setStatus("RECEIVED");
        // 工作日历和办理人员在 Sprint 4 确认后填写 dueAt，不虚构已完成或期限承诺。
        requests.insert(request);
        audit.record(AuditService.ACTION_EXPORT, UserContext.userId(), member.getFamilyId(),
                "PRIVACY_REQUEST", request.getId(), null, "RECEIVED");
        return response(request);
    }

    @Override
    public DataExportResp getRequest(Long id) {
        PrivacyRequest request = requests.selectOne(new QueryWrapper<PrivacyRequest>()
                .eq("id", id).eq("requester_id", UserContext.userId()));
        if (request == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND);
        }
        audit.record("PRIVACY_QUERY", UserContext.userId(), request.getFamilyId(),
                "PRIVACY_REQUEST", id, null, "query");
        return response(request);
    }

    private DataExportResp response(PrivacyRequest request) {
        return DataExportResp.builder().taskId(String.valueOf(request.getId())).status(request.getStatus())
                .dueAt(request.getDueAt()).errorCode(request.getErrorCode()).downloadAvailable(false).build();
    }

    private ConsentLog newLog(FamilyMember member, String type, String version, String action) {
        ConsentLog log = new ConsentLog();
        log.setUserId(UserContext.userId());
        log.setChildId(member.getUserId());
        log.setFamilyId(member.getFamilyId());
        log.setApplyId(member.getId());
        log.setApplicationVersion(member.getApplicationVersion());
        log.setConsentType(type);
        log.setVersion(version);
        log.setAction(action);
        log.setSignedAt(System.currentTimeMillis());
        return log;
    }

    private void requireType(String type) {
        if (!"PROFILE".equals(type)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "Sprint 1 仅支持 PROFILE");
        }
    }

    private String hash(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
