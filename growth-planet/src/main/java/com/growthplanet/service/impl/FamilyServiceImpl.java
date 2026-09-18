package com.growthplanet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.growthplanet.common.context.LoginUser;
import com.growthplanet.common.context.UserContext;
import com.growthplanet.common.enums.BindStatusEnum;
import com.growthplanet.common.enums.GuardianStatusEnum;
import com.growthplanet.common.enums.RoleEnum;
import com.growthplanet.common.exception.BizException;
import com.growthplanet.common.result.ResultCode;
import com.growthplanet.config.ComplianceProperties;
import com.growthplanet.dto.request.BindApproveReq;
import com.growthplanet.dto.request.CreateFamilyReq;
import com.growthplanet.dto.request.JoinFamilyReq;
import com.growthplanet.dto.response.BindApproveResp;
import com.growthplanet.dto.response.CreateFamilyResp;
import com.growthplanet.dto.response.InviteCodeResp;
import com.growthplanet.dto.response.JoinFamilyResp;
import com.growthplanet.entity.Family;
import com.growthplanet.entity.FamilyMember;
import com.growthplanet.mapper.FamilyMapper;
import com.growthplanet.mapper.FamilyMemberMapper;
import com.growthplanet.service.AuditService;
import com.growthplanet.service.FamilyService;
import com.growthplanet.util.InviteCodeUtil;
import com.growthplanet.util.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * FAMILY 模块业务实现。
 */
@Service
public class FamilyServiceImpl implements FamilyService {

    private static final long INVITE_CODE_TTL = 24L * 3600 * 1000;

    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;
    private final com.growthplanet.service.ChildAuthorizationService authorization;
    private final com.growthplanet.service.NoticeService notices;
    private final com.growthplanet.mapper.UserMapper users;
    private final com.growthplanet.service.SessionService sessions;
    private final ComplianceProperties policy;

    public FamilyServiceImpl(FamilyMapper familyMapper, FamilyMemberMapper familyMemberMapper,
                             JwtUtil jwtUtil, AuditService auditService,
                             com.growthplanet.service.ChildAuthorizationService authorization,
                             com.growthplanet.service.NoticeService notices,
                             com.growthplanet.mapper.UserMapper users,
                             com.growthplanet.service.SessionService sessions, ComplianceProperties policy) {
        this.familyMapper = familyMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.jwtUtil = jwtUtil;
        this.auditService = auditService;
        this.authorization = authorization;
        this.notices = notices;
        this.users = users;
        this.sessions = sessions;
        this.policy = policy;
    }

    @Override
    @Transactional
    public CreateFamilyResp createFamily(CreateFamilyReq req) {
        policy.requireCollection();
        LoginUser ctx = requireLogin();
        requireParent(ctx);

        users.selectOne(new QueryWrapper<com.growthplanet.entity.User>().eq("id", ctx.getUserId()).last("FOR UPDATE"));
        if (familyMemberMapper.selectCount(new QueryWrapper<FamilyMember>()
                .eq("user_id", ctx.getUserId()).eq("bind_status", "BOUND")) > 0) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "Sprint 1 仅支持一个家庭");
        }

        String code = generateUniqueInviteCode();
        long expire = System.currentTimeMillis() + INVITE_CODE_TTL;

        Family family = new Family();
        family.setFamilyName(req.getFamilyName());
        family.setOwnerUserId(ctx.getUserId());
        family.setInviteCode(code);
        family.setInviteCodeExpire(expire);
        familyMapper.insert(family);
        Long familyId = family.getId();

        FamilyMember ownerMember = new FamilyMember();
        ownerMember.setFamilyId(familyId);
        ownerMember.setUserId(ctx.getUserId());
        ownerMember.setRelationLabel("家长");
        ownerMember.setRole(RoleEnum.PARENT.name());
        ownerMember.setBindStatus(BindStatusEnum.BOUND.name());
        ownerMember.setGuardianStatus(GuardianStatusEnum.UNVERIFIED.name());
        familyMemberMapper.insert(ownerMember);

        auditService.record(AuditService.ACTION_CREATE_FAMILY, ctx.getUserId(), familyId, "FAMILY", familyId, null, "create family");

        // 创建成功后重新签发 token，写入 family_ids 并返回
        LoginUser updated = sessions.current(users.selectById(ctx.getUserId()));
        String token = jwtUtil.generateToken(updated);
        return CreateFamilyResp.builder()
                .familyId(familyId)
                .inviteCode(code)
                .expireAt(expire)
                .token(token)
                .build();
    }

    @Override
    @Transactional
    public InviteCodeResp getInviteCode() {
        LoginUser ctx = requireLogin();
        requireParent(ctx);
        Long familyId = ctx.firstFamilyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "无家庭上下文");
        }
        Family family = familyMapper.selectOne(new QueryWrapper<Family>().eq("id", familyId).last("FOR UPDATE"));
        authorization.requireParent(familyId);
        if (family == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "家庭不存在");
        }
        long now = System.currentTimeMillis();
        if (family.getInviteCode() == null || family.getInviteCodeExpire() == null
                || family.getInviteCodeExpire() < now) {
            String code = generateUniqueInviteCode();
            family.setInviteCode(code);
            family.setInviteCodeExpire(now + INVITE_CODE_TTL);
            familyMapper.updateById(family);
        }
        return InviteCodeResp.builder()
                .inviteCode(family.getInviteCode())
                .expireAt(family.getInviteCodeExpire())
                .qrBase64(null)
                .build();
    }

    @Override
    @Transactional
    public JoinFamilyResp joinFamily(JoinFamilyReq req) {
        policy.requireCollection();
        LoginUser ctx = requireLogin();
        requireChild(ctx);

        Family family = familyMapper.selectOne(new QueryWrapper<Family>().eq("invite_code", req.getInviteCode()));
        if (family == null) {
            throw new BizException(ResultCode.E003_INVITE_CODE_EXPIRED, "邀请码不存在");
        }
        family = authorization.lockScope(family.getId(), ctx.getUserId());
        long now = System.currentTimeMillis();
        if (!req.getInviteCode().equals(family.getInviteCode())
                || family.getInviteCodeExpire() == null || family.getInviteCodeExpire() <= now) {
            throw new BizException(ResultCode.E003_INVITE_CODE_EXPIRED);
        }
        // 防重复加入（同一家庭同一用户）
        FamilyMember existed = familyMemberMapper.selectOne(new QueryWrapper<FamilyMember>()
                .eq("family_id", family.getId())
                .eq("user_id", ctx.getUserId()).last("FOR UPDATE"));
        if (existed != null && !"REJECTED".equals(existed.getBindStatus())) {
            return JoinFamilyResp.builder().applyId(existed.getId()).status(existed.getBindStatus()).build();
        }
        if (familyMemberMapper.selectCount(new QueryWrapper<FamilyMember>()
                .eq("user_id", ctx.getUserId()).in("bind_status", "PENDING", "BOUND")
                .ne("family_id", family.getId())) > 0) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "已有其他有效家庭申请或绑定");
        }

        FamilyMember member = existed == null ? new FamilyMember() : existed;
        member.setFamilyId(family.getId());
        member.setUserId(ctx.getUserId());
        member.setRole(RoleEnum.CHILD.name());
        member.setBindStatus(BindStatusEnum.PENDING.name());
        member.setGuardianStatus(GuardianStatusEnum.UNVERIFIED.name());
        if (existed == null) {
            familyMemberMapper.insert(member);
        } else {
            member.setApplicationVersion(member.getApplicationVersion() + 1);
            familyMemberMapper.updateById(member);
        }

        auditService.record(AuditService.ACTION_JOIN, ctx.getUserId(), family.getId(), "FAMILY_MEMBER", member.getId(), null, "join family");
        notices.recordBinding(member, family.getOwnerUserId());

        return JoinFamilyResp.builder()
                .applyId(member.getId())
                .status(BindStatusEnum.PENDING.name())
                .build();
    }

    @Override
    @Transactional
    public BindApproveResp bindApprove(BindApproveReq req) {
        LoginUser ctx = requireLogin();
        requireParent(ctx);
        Long familyId = ctx.firstFamilyId();

        FamilyMember member = familyMemberMapper.selectById(req.getApplyId());
        if (member == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "申请不存在");
        }
        // 跨家庭二次比对
        if (!member.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "越权访问他人家庭申请");
        }

        member = authorization.lockChild(familyId, member.getUserId());
        if (!member.getId().equals(req.getApplyId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        BindStatusEnum status = Boolean.TRUE.equals(req.getApprove()) ? BindStatusEnum.BOUND : BindStatusEnum.REJECTED;
        if (status.name().equals(member.getBindStatus())) {
            return BindApproveResp.builder().bindStatus(status.name()).build();
        }
        if (!"PENDING".equals(member.getBindStatus())) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "申请已处理");
        }
        if (status == BindStatusEnum.BOUND) {
            var consent = authorization.requireConsent(member);
            member.setGuardianStatus(consent.getGuardianStatus());
            if (familyMemberMapper.selectCount(new QueryWrapper<FamilyMember>()
                    .eq("user_id", member.getUserId()).eq("bind_status", "BOUND")
                    .ne("family_id", familyId)) > 0) {
                throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "儿童已绑定其他家庭");
            }
        }
        member.setBindStatus(status.name());
        member.setRelationLabel(req.getRelationLabel());
        familyMemberMapper.updateById(member);
        notices.recordBinding(member, member.getUserId());

        auditService.record(AuditService.ACTION_BIND, ctx.getUserId(), familyId, "FAMILY_MEMBER", member.getId(), null,
                "bind approve=" + req.getApprove() + ";applicationVersion=" + member.getApplicationVersion());

        return BindApproveResp.builder().bindStatus(status.name()).build();
    }

    /** 生成全局唯一邀请码（最多重试 5 次）。 */
    private String generateUniqueInviteCode() {
        for (int i = 0; i < 5; i++) {
            String code = InviteCodeUtil.randomCode();
            Family existed = familyMapper.selectOne(new QueryWrapper<Family>().eq("invite_code", code));
            if (existed == null) {
                return code;
            }
        }
        throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "生成唯一邀请码失败");
    }

    private LoginUser requireLogin() {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH);
        }
        return user;
    }

    private void requireParent(LoginUser ctx) {
        if (!RoleEnum.PARENT.name().equals(ctx.getRole())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "仅家长可操作");
        }
    }

    private void requireChild(LoginUser ctx) {
        if (!RoleEnum.CHILD.name().equals(ctx.getRole())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "仅儿童可操作");
        }
    }
}
