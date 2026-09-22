package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.mapper.UserMapper;
import cn.studykid.growthplanet.service.ChildAuthorizationService;
import cn.studykid.growthplanet.service.NoticeService;
import cn.studykid.growthplanet.service.SessionService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.studykid.growthplanet.common.context.LoginUser;
import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.enums.BindStatusEnum;
import cn.studykid.growthplanet.common.enums.GuardianStatusEnum;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.dto.request.BindApproveReq;
import cn.studykid.growthplanet.dto.request.CreateFamilyReq;
import cn.studykid.growthplanet.dto.request.JoinFamilyReq;
import cn.studykid.growthplanet.dto.response.BindApproveResp;
import cn.studykid.growthplanet.dto.response.CreateFamilyResp;
import cn.studykid.growthplanet.dto.response.InviteCodeResp;
import cn.studykid.growthplanet.dto.response.JoinFamilyResp;
import cn.studykid.growthplanet.dto.response.FamilyChildResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.entity.Family;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.mapper.FamilyMapper;
import cn.studykid.growthplanet.mapper.FamilyMemberMapper;
import cn.studykid.growthplanet.service.AuditService;
import cn.studykid.growthplanet.service.FamilyService;
import cn.studykid.growthplanet.util.InviteCodeUtil;
import cn.studykid.growthplanet.util.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ChildAuthorizationService authorization;
    private final NoticeService notices;
    private final UserMapper users;
    private final SessionService sessions;
    private final ComplianceProperties policy;

    public FamilyServiceImpl(FamilyMapper familyMapper, FamilyMemberMapper familyMemberMapper,
                             JwtUtil jwtUtil, AuditService auditService,
                             ChildAuthorizationService authorization,
                             NoticeService notices,
                             UserMapper users,
                             SessionService sessions, ComplianceProperties policy) {
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

        users.selectOne(new QueryWrapper<User>().eq("id", ctx.getUserId()).last("FOR UPDATE"));
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

    @Override
    @Transactional
    public PageResp<FamilyChildResp> getChildren(int page, int pageSize, String bindStatus) {
        if (page < 1 || pageSize < 1 || pageSize > 100
                || bindStatus != null && !java.util.Set.of("PENDING", "BOUND", "REJECTED").contains(bindStatus)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT);
        }
        Long familyId = requireLogin().firstFamilyId();
        authorization.requireParent(familyId);
        if (familyMapper.selectById(familyId) == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        QueryWrapper<FamilyMember> query = new QueryWrapper<FamilyMember>()
                .eq("family_id", familyId).eq("role", "CHILD")
                .eq(bindStatus != null, "bind_status", bindStatus);
        long total = familyMemberMapper.selectCount(query);
        // LIMIT 仅拼接经过校验的数值；先转 long，防止页码乘法溢出。
        long offset = ((long) page - 1) * pageSize;
        var items = familyMemberMapper.selectList(query.orderByAsc("id")
                .last("LIMIT " + pageSize + " OFFSET " + offset)).stream().map(this::childResponse).toList();
        auditService.record("FAMILY_QUERY", UserContext.userId(), familyId, "FAMILY", familyId, null,
                "page=" + page + ";pageSize=" + pageSize);
        return PageResp.<FamilyChildResp>builder().items(items).total(total).page(page).pageSize(pageSize).build();
    }

    @Override
    @Transactional
    public FamilyChildResp getBinding() {
        LoginUser ctx = requireLogin();
        requireChild(ctx);
        FamilyMember member = familyMemberMapper.selectOne(new QueryWrapper<FamilyMember>()
                .eq("user_id", ctx.getUserId()).eq("role", "CHILD").in("bind_status", "PENDING", "BOUND"));
        if (member == null) {
            member = familyMemberMapper.selectOne(new QueryWrapper<FamilyMember>()
                    .eq("user_id", ctx.getUserId()).eq("role", "CHILD").eq("bind_status", "REJECTED")
                    .orderByDesc("update_time", "id").last("LIMIT 1"));
        }
        auditService.record("BIND_QUERY", ctx.getUserId(), member == null ? null : member.getFamilyId(),
                "CHILD", ctx.getUserId(), null, "query own binding");
        return member == null ? FamilyChildResp.builder().childId(ctx.getUserId()).bindStatus("NONE").build()
                : childResponse(member);
    }

    private FamilyChildResp childResponse(FamilyMember member) {
        return FamilyChildResp.builder().familyId(member.getFamilyId()).childId(member.getUserId())
                .applyId(member.getId()).bindStatus(member.getBindStatus())
                .applicationVersion(member.getApplicationVersion()).relationLabel(member.getRelationLabel()).build();
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
