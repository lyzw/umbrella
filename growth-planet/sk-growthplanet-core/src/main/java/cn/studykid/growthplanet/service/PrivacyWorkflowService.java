package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.config.PrivacyProperties;
import cn.studykid.growthplanet.dto.request.PrivacyTransitionReq;
import cn.studykid.growthplanet.dto.response.DataExportResp;
import cn.studykid.growthplanet.entity.*;
import cn.studykid.growthplanet.mapper.*;
import cn.studykid.growthplanet.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional
public class PrivacyWorkflowService {
    private static final Set<String> TARGETS = Set.of("PROCESSING", "READY", "COMPLETED", "FAILED", "REJECTED");
    private static final Set<String> ERRORS = Set.of("IDENTITY_UNCONFIRMED", "OUT_OF_SCOPE", "MANUAL_REVIEW_FAILED");
    private final PrivacyRequestMapper requests;
    private final PrivacyProperties settings;
    private final UserMapper users;
    private final FamilyMemberMapper members;
    private final ChildProfileMapper profiles;
    private final ConsentLogMapper consents;
    private final ChildAuthorizationService authorization;
    private final AuditService audit;

    public PrivacyWorkflowService(PrivacyRequestMapper requests, PrivacyProperties settings, UserMapper users,
            FamilyMemberMapper members, ChildProfileMapper profiles, ConsentLogMapper consents,
            ChildAuthorizationService authorization, AuditService audit) {
        this.requests = requests;
        this.settings = settings;
        this.users = users;
        this.members = members;
        this.profiles = profiles;
        this.consents = consents;
        this.authorization = authorization;
        this.audit = audit;
    }

    public DataExportResp getRequest(Long id) {
        PrivacyRequest request;
        if ("ADMIN".equals(UserContext.role())) {
            requireOperator();
            request = requests.selectById(id);
        } else {
            request = requests.selectOne(new QueryWrapper<PrivacyRequest>()
                    .eq("id", id).eq("requester_id", UserContext.userId()));
        }
        if (request == null) throw new BizException(ResultCode.E404_NOT_FOUND);
        audit.record("PRIVACY_QUERY", UserContext.userId(), request.getFamilyId(),
                "PRIVACY_REQUEST", request.getId(), null, "status=" + request.getStatus());
        return response(request);
    }

    public DataExportResp transition(Long id, PrivacyTransitionReq req) {
        requireOperator();
        if (id == null || id <= 0 || req == null || req.getExpectedVersion() == null
                || req.getExpectedVersion() < 0 || req.getStatus() == null || !TARGETS.contains(req.getStatus())
                || req.getEvidenceRef() == null || !req.getEvidenceRef().matches("[A-Za-z0-9_-]{1,128}")
                || req.getErrorCode() != null && !ERRORS.contains(req.getErrorCode())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        PrivacyRequest reference = requests.selectById(id);
        if (reference == null) throw new BizException(ResultCode.E404_NOT_FOUND);
        // READY authorizes a current scoped snapshot. It never accepts an external
        // object path/URL, and the download rechecks requester ownership separately.
        if ("READY".equals(req.getStatus())) lockRequesterScope(reference);
        PrivacyRequest request = requests.selectOne(new QueryWrapper<PrivacyRequest>()
                .eq("id", id).last("FOR UPDATE"));
        if (request == null) throw new BizException(ResultCode.E404_NOT_FOUND);
        if (!req.getExpectedVersion().equals(request.getVersion())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        String previous = request.getStatus();
        boolean received = "RECEIVED".equals(previous);
        boolean processing = "PROCESSING".equals(previous);
        boolean allowed = switch (req.getStatus()) {
            case "PROCESSING" -> received;
            case "READY" -> processing && "EXPORT".equals(request.getRequestType());
            case "COMPLETED" -> processing && "DELETE".equals(request.getRequestType())
                    && request.getVerifiedAt() != null;
            case "FAILED", "REJECTED" -> received || processing;
            default -> false;
        };
        if (!allowed) throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        long now = System.currentTimeMillis();
        if ("PROCESSING".equals(req.getStatus())) {
            if (req.getDueAt() == null || req.getDueAt() <= now) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
            }
            request.setDueAt(req.getDueAt());
        } else if (req.getDueAt() != null) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        boolean failure = Set.of("FAILED", "REJECTED").contains(req.getStatus());
        if (failure != (req.getErrorCode() != null)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        if ("READY".equals(req.getStatus())) {
            request.setResultRef("GENERATED_PROFILE_V1");
            request.setExpiresAt(now + 24L * 3600 * 1000);
        }
        request.setStatus(req.getStatus());
        request.setErrorCode(req.getErrorCode());
        request.setEvidenceRef(req.getEvidenceRef());
        request.setOperatorId(UserContext.userId());
        request.setVersion(request.getVersion() + 1);
        requests.updateById(request);
        // Evidence is an opaque ticket ID, not a path or a free-form sensitive note.
        audit.record("PRIVACY_TRANSITION", UserContext.userId(), request.getFamilyId(), "PRIVACY_REQUEST",
                request.getId(), null, previous + "->" + request.getStatus() + ";version=" + request.getVersion()
                        + ";evidence=" + request.getEvidenceRef());
        return response(request);
    }

    public byte[] download(Long id) {
        if (!"PARENT".equals(UserContext.role())) throw new BizException(ResultCode.E009_FORBIDDEN);
        PrivacyRequest reference = requests.selectOne(new QueryWrapper<PrivacyRequest>()
                .eq("id", id).eq("requester_id", UserContext.userId()));
        if (reference == null) throw new BizException(ResultCode.E404_NOT_FOUND);
        lockRequesterScope(reference);
        PrivacyRequest request = requests.selectOne(new QueryWrapper<PrivacyRequest>()
                .eq("id", id).eq("requester_id", UserContext.userId()).last("FOR UPDATE"));
        if (request == null) throw new BizException(ResultCode.E404_NOT_FOUND);
        if (expired(request)) throw new BizException(ResultCode.E410_GONE);
        if (!"EXPORT".equals(request.getRequestType()) || !"READY".equals(request.getStatus())
                || !"GENERATED_PROFILE_V1".equals(request.getResultRef()) || request.getExpiresAt() == null) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("format", "PROFILE_RIGHTS_V1");
        snapshot.put("generatedAt", System.currentTimeMillis());
        snapshot.put("requestId", request.getId().toString());
        snapshot.put("requesterId", request.getRequesterId().toString());
        snapshot.put("childId", request.getChildId().toString());
        snapshot.put("familyId", request.getFamilyId().toString());
        snapshot.put("scope", "Current profile/preferences and last 500 requester consent records; "
                + "not a full historical export. Financial/order records require a separate reviewed work order.");
        ChildProfile profile = profiles.selectOne(new QueryWrapper<ChildProfile>()
                .eq("user_id", request.getChildId()).eq("family_id", request.getFamilyId()));
        Map<String, Object> profileData = new LinkedHashMap<>();
        if (profile != null) {
            profileData.put("nickname", profile.getNickname());
            profileData.put("grade", profile.getGrade());
            profileData.put("school", profile.getSchool());
            profileData.put("allergies", profile.getAllergies());
            profileData.put("dislikes", profile.getDislikes());
            profileData.put("tastes", profile.getTastes());
            profileData.put("favoriteDishIds", profile.getFavoriteDishIds() == null ? List.of()
                    : profile.getFavoriteDishIds().stream().map(String::valueOf).toList());
            profileData.put("profileStatus", profile.getProfileStatus());
        }
        snapshot.put("profile", profileData);
        QueryWrapper<ConsentLog> consentScope = new QueryWrapper<ConsentLog>()
                .eq("family_id", request.getFamilyId()).eq("child_id", request.getChildId())
                .eq("user_id", request.getRequesterId());
        long consentCount = consents.selectCount(consentScope);
        snapshot.put("consentHistoryTruncated", consentCount > 500);
        snapshot.put("consents", consents.selectList(consentScope.orderByDesc("id").last("LIMIT 500"))
                .stream().map(consent -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", consent.getId().toString());
                    row.put("type", consent.getConsentType());
                    row.put("action", consent.getAction());
                    row.put("version", consent.getVersion());
                    row.put("guardianStatus", consent.getGuardianStatus());
                    row.put("signedAt", consent.getSignedAt());
                    row.put("expiresAt", consent.getExpireAt());
                    return row;
                }).toList());
        byte[] payload = JsonUtils.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
        audit.record("PRIVACY_DOWNLOAD", UserContext.userId(), request.getFamilyId(),
                "PRIVACY_REQUEST", request.getId(), null, "format=PROFILE_RIGHTS_V1");
        return payload;
    }

    public DataExportResp response(PrivacyRequest request) {
        boolean expired = expired(request);
        return DataExportResp.builder().taskId(request.getId().toString())
                .status(expired ? "EXPIRED" : request.getStatus()).requestType(request.getRequestType())
                .dueAt(request.getDueAt()).errorCode(request.getErrorCode()).expiresAt(request.getExpiresAt())
                .version(request.getVersion()).downloadAvailable(!expired
                        && "EXPORT".equals(request.getRequestType()) && "READY".equals(request.getStatus())
                        && "GENERATED_PROFILE_V1".equals(request.getResultRef())
                        && request.getExpiresAt() != null
                        && Objects.equals(request.getRequesterId(), UserContext.userId())).build();
    }

    private boolean expired(PrivacyRequest request) {
        return "EXPIRED".equals(request.getStatus()) || "READY".equals(request.getStatus())
                && request.getExpiresAt() != null && request.getExpiresAt() <= System.currentTimeMillis();
    }

    private void requireOperator() {
        if (!"ADMIN".equals(UserContext.role()) || UserContext.userId() == null
                || !settings.getOperatorIds().contains(UserContext.userId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        User operator = users.selectById(UserContext.userId());
        if (operator == null || !"ADMIN".equals(operator.getRole()) || !"NORMAL".equals(operator.getStatus())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
    }

    private void lockRequesterScope(PrivacyRequest request) {
        authorization.lockScope(request.getFamilyId(), request.getChildId());
        User requester = users.selectById(request.getRequesterId());
        FamilyMember child = members.selectOne(new QueryWrapper<FamilyMember>()
                .eq("family_id", request.getFamilyId()).eq("user_id", request.getChildId())
                .eq("role", "CHILD").last("FOR UPDATE"));
        if (requester == null || !"NORMAL".equals(requester.getStatus()) || !"PARENT".equals(requester.getRole())
                || child == null || !"BOUND".equals(child.getBindStatus())
                || members.selectCount(new QueryWrapper<FamilyMember>().eq("family_id", request.getFamilyId())
                .eq("user_id", request.getRequesterId()).eq("role", "PARENT").eq("bind_status", "BOUND")) != 1) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
    }
}
