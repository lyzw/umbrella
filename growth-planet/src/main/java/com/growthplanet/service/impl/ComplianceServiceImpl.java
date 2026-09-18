package com.growthplanet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.growthplanet.common.context.LoginUser;
import com.growthplanet.common.context.UserContext;
import com.growthplanet.common.enums.ConsentActionEnum;
import com.growthplanet.common.enums.GuardianStatusEnum;
import com.growthplanet.common.enums.RoleEnum;
import com.growthplanet.common.exception.BizException;
import com.growthplanet.common.result.ResultCode;
import com.growthplanet.dto.request.ConsentReq;
import com.growthplanet.dto.request.DataExportReq;
import com.growthplanet.dto.request.RevokeConsentReq;
import com.growthplanet.dto.response.ConsentResp;
import com.growthplanet.dto.response.DataExportResp;
import com.growthplanet.entity.ChildProfile;
import com.growthplanet.entity.ConsentLog;
import com.growthplanet.entity.FamilyMember;
import com.growthplanet.mapper.ChildProfileMapper;
import com.growthplanet.mapper.ConsentLogMapper;
import com.growthplanet.mapper.FamilyMemberMapper;
import com.growthplanet.service.AuditService;
import com.growthplanet.service.ComplianceService;
import com.growthplanet.util.JsonUtils;
import com.growthplanet.util.JwtUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * COMPLIANCE 模块业务实现。
 */
@Service
public class ComplianceServiceImpl implements ComplianceService {

    private static final String AGREEMENT_TEXT = "未成年人饮食与健康数据监护人同意书（v1）。"
            + "监护人确认已年满18周岁，并授权成长星球在儿童账户下收集饮食偏好、忌口、健康档案等数据用于家庭配餐推荐。";
    private static final String DEFAULT_CONSENT_TYPE = "ORDER";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final ConsentLogMapper consentLogMapper;
    private final ChildProfileMapper childProfileMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final AuditService auditService;

    public ComplianceServiceImpl(ConsentLogMapper consentLogMapper, ChildProfileMapper childProfileMapper,
                                 FamilyMemberMapper familyMemberMapper, JwtUtil jwtUtil,
                                 RedisTemplate<String, String> redisTemplate, AuditService auditService) {
        this.consentLogMapper = consentLogMapper;
        this.childProfileMapper = childProfileMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.auditService = auditService;
    }

    @Override
    public ConsentResp getConsent(Long childId) {
        LoginUser ctx = requireParentLogin();
        Long familyId = ctx.firstFamilyId();

        QueryWrapper<ConsentLog> qw = new QueryWrapper<ConsentLog>()
                .eq("family_id", familyId)
                .eq("user_id", ctx.getUserId());
        if (childId != null) {
            qw.eq("child_id", childId);
        }
        qw.orderByDesc("id").last("LIMIT 1");
        ConsentLog latest = consentLogMapper.selectOne(qw);
        String currentStatus = latest == null ? "NONE" : latest.getGuardianStatus();

        return ConsentResp.builder()
                .agreementText(AGREEMENT_TEXT)
                .version("v1")
                .currentStatus(currentStatus)
                .build();
    }

    @Override
    @Transactional
    public ConsentResp submitConsent(ConsentReq req) {
        LoginUser ctx = requireParentLogin();
        Long familyId = ctx.firstFamilyId();
        Long childId = resolveChildId(ctx, req.getChildId());

        // 自报年龄 >=18 才允许 agreed=true
        if (req.isAgreed() && (req.getSelfReportedAge() == null || req.getSelfReportedAge() < 18)) {
            throw new BizException(ResultCode.E004_GUARDIAN_VERIFY_FAILED);
        }

        ConsentLog log = new ConsentLog();
        log.setUserId(ctx.getUserId());
        log.setChildId(childId);
        log.setFamilyId(familyId);
        log.setConsentType(DEFAULT_CONSENT_TYPE);
        log.setAction(ConsentActionEnum.GRANT.name());
        log.setVersion(req.getVersion());
        log.setSelfReportedAge(req.getSelfReportedAge());
        log.setGuardianStatus(req.isAgreed()
                ? GuardianStatusEnum.APPROVED.name()
                : GuardianStatusEnum.PENDING.name());
        log.setSignedAt(System.currentTimeMillis());
        log.setExpireAt(System.currentTimeMillis() + 365L * 24 * 3600 * 1000);
        consentLogMapper.insert(log);

        auditService.record(AuditService.ACTION_GRANT, ctx.getUserId(), familyId, "CONSENT", log.getId(), null, "consent grant");

        return ConsentResp.builder()
                .version(req.getVersion())
                .guardianStatus(log.getGuardianStatus())
                .build();
    }

    @Override
    @Transactional
    public String revokeConsent(RevokeConsentReq req) {
        LoginUser ctx = requireParentLogin();
        Long familyId = ctx.firstFamilyId();

        QueryWrapper<ConsentLog> qw = new QueryWrapper<ConsentLog>()
                .eq("family_id", familyId)
                .eq("user_id", ctx.getUserId())
                .eq("child_id", req.getChildId())
                .eq("consent_type", req.getConsentType())
                .orderByDesc("id").last("LIMIT 1");
        ConsentLog log = consentLogMapper.selectOne(qw);
        if (log == null) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "无对应同意书记录");
        }

        log.setAction(ConsentActionEnum.REVOKE.name());
        log.setGuardianStatus(GuardianStatusEnum.REVOKED.name());
        consentLogMapper.updateById(log);

        // 当前 token 的 jti 加入 Redis 黑名单实现即时降级（Redis 不可用时 fail-open）
        String jti = ctx.getJti();
        if (jti != null) {
            try {
                long ttl = jwtUtil.getRemainingTtl(jwtUtil.parse(UserContext.currentToken()));
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", Duration.ofMillis(ttl));
            } catch (Exception ignored) {
                // fail-open：Redis 不可用不影响主流程
            }
        }

        auditService.record(AuditService.ACTION_REVOKE, ctx.getUserId(), familyId, "CONSENT", log.getId(), null, "consent revoke");

        return GuardianStatusEnum.REVOKED.name();
    }

    @Override
    @Transactional
    public DataExportResp dataExport(DataExportReq req) {
        LoginUser ctx = requireParentLogin();
        Long familyId = ctx.firstFamilyId();

        // 校验儿童属于当前家庭（数据隔离 / 越权拦截）
        FamilyMember member = familyMemberMapper.selectOne(new QueryWrapper<FamilyMember>()
                .eq("family_id", familyId)
                .eq("user_id", req.getChildId())
                .eq("role", RoleEnum.CHILD.name()));
        if (member == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "儿童不属于当前家庭");
        }

        ChildProfile profile = childProfileMapper.selectOne(new QueryWrapper<ChildProfile>()
                .eq("user_id", req.getChildId())
                .eq("family_id", familyId));
        List<ConsentLog> logs = consentLogMapper.selectList(new QueryWrapper<ConsentLog>()
                .eq("family_id", familyId)
                .eq("child_id", req.getChildId()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("childProfile", profile);
        data.put("consentLogs", logs);
        data.put("exportedAt", System.currentTimeMillis());
        String json = JsonUtils.toJson(data);

        auditService.record(AuditService.ACTION_EXPORT, ctx.getUserId(), familyId, "CHILD", req.getChildId(), null, "data export");

        return DataExportResp.builder()
                .taskId("task_" + System.currentTimeMillis())
                .status("DONE")
                .data(json)
                .build();
    }

    /** 解析儿童 ID：请求指定则用请求值，否则取家庭内首个 CHILD 成员。 */
    private Long resolveChildId(LoginUser ctx, Long childId) {
        if (childId != null) {
            return childId;
        }
        FamilyMember member = familyMemberMapper.selectOne(new QueryWrapper<FamilyMember>()
                .eq("family_id", ctx.firstFamilyId())
                .eq("role", RoleEnum.CHILD.name())
                .last("LIMIT 1"));
        if (member == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "家庭中无儿童");
        }
        return member.getUserId();
    }

    /** 从当前线程上下文取出原始 token（供黑名单 TTL 计算）。 */
    private String currentToken() {
        // UserContext 仅存解析后的 LoginUser；token 由拦截器在校验时暂存到 request attribute。
        // 为简化，revoke 直接使用 LoginUser 的 jti + 默认剩余有效期近似；此处读取拦截器放入的 token。
        Object token = TOKEN_HOLDER.get();
        if (token == null) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "无法获取当前 token");
        }
        return (String) token;
    }

    // 由 JwtInterceptor 通过 setCurrentToken 注入当前请求 token
    public static final ThreadLocal<String> TOKEN_HOLDER = new ThreadLocal<>();

    private LoginUser requireParentLogin() {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH);
        }
        if (!RoleEnum.PARENT.name().equals(user.getRole())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "仅家长可操作");
        }
        return user;
    }
}
