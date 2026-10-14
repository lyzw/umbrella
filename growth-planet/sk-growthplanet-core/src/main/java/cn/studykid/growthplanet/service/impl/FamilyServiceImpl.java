package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.common.context.LoginUser;
import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.enums.BindStatusEnum;
import cn.studykid.growthplanet.common.enums.GuardianStatusEnum;
import cn.studykid.growthplanet.common.enums.InviteType;
import cn.studykid.growthplanet.common.enums.RelationEnum;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.dto.request.BindApproveReq;
import cn.studykid.growthplanet.dto.request.CreateFamilyReq;
import cn.studykid.growthplanet.dto.request.GenerateInviteCodeReq;
import cn.studykid.growthplanet.dto.request.JoinFamilyReq;
import cn.studykid.growthplanet.dto.request.RemoveMemberReq;
import cn.studykid.growthplanet.dto.request.RevokeInviteCodeReq;
import cn.studykid.growthplanet.dto.request.TransferOwnerReq;
import cn.studykid.growthplanet.dto.response.BindApproveResp;
import cn.studykid.growthplanet.dto.response.CreateFamilyResp;
import cn.studykid.growthplanet.dto.response.FamilyChildResp;
import cn.studykid.growthplanet.dto.response.FamilyInviteResp;
import cn.studykid.growthplanet.dto.response.InviteCodeResp;
import cn.studykid.growthplanet.dto.response.JoinFamilyResp;
import cn.studykid.growthplanet.dto.response.MyFamilyResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.entity.Family;
import cn.studykid.growthplanet.entity.FamilyInvite;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.mapper.FamilyInviteMapper;
import cn.studykid.growthplanet.mapper.FamilyMapper;
import cn.studykid.growthplanet.mapper.FamilyMemberMapper;
import cn.studykid.growthplanet.mapper.UserMapper;
import cn.studykid.growthplanet.service.AuditService;
import cn.studykid.growthplanet.service.ChildAuthorizationService;
import cn.studykid.growthplanet.service.FamilyService;
import cn.studykid.growthplanet.service.NoticeService;
import cn.studykid.growthplanet.service.SessionService;
import cn.studykid.growthplanet.util.InviteCodeUtil;
import cn.studykid.growthplanet.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FAMILY 模块业务实现。
 *
 * <p>家庭模型：多家长（父母 / 祖辈 / 亲友，权限不分级）+ 多学生 + 可多家庭归属。
 * 邀请码按类型（PARENT / CHILD）锁定加入角色：家长码免审直连（BOUND），学生码需家长审批（PENDING）。
 */
@Service
public class FamilyServiceImpl implements FamilyService {

    /** 邀请码默认有效期：7 天（毫秒）。 */
    private static final long INVITE_DEFAULT_TTL_MS = 7L * 24 * 3600 * 1000;

    /** 邀请码默认最大使用次数：0 = 不限制。 */
    private static final int INVITE_DEFAULT_MAX_USE = 0;

    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final FamilyInviteMapper familyInviteMapper;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;
    private final ChildAuthorizationService authorization;
    private final NoticeService notices;
    private final UserMapper users;
    private final SessionService sessions;
    private final ComplianceProperties policy;

    public FamilyServiceImpl(FamilyMapper familyMapper, FamilyMemberMapper familyMemberMapper,
                             FamilyInviteMapper familyInviteMapper, JwtUtil jwtUtil, AuditService auditService,
                             ChildAuthorizationService authorization,
                             NoticeService notices,
                             UserMapper users,
                             SessionService sessions, ComplianceProperties policy) {
        this.familyMapper = familyMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.familyInviteMapper = familyInviteMapper;
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
        // 多家庭：移除「Sprint 1 仅支持一个家庭」约束，允许同一用户创建 / 加入多个家庭。

        String code = generateUniqueInviteCode();
        long expire = System.currentTimeMillis() + INVITE_DEFAULT_TTL_MS;

        Family family = new Family();
        family.setFamilyName(req.getFamilyName());
        family.setOwnerUserId(ctx.getUserId());
        family.setInviteCode(code);              // 旧列保留以兼容历史路径，主路径走 usr_family_invite
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

        // 默认生成一张「家长邀请码」（PARENT 类型，免审），供家人凭码加入。
        // 其 code 与旧列 usr_family.invite_code 保持一致，兼容仍依赖旧列的接入路径（如 setupFamily）。
        FamilyInvite ownerInvite = buildInvite(familyId, InviteType.PARENT, ctx.getUserId(), INVITE_DEFAULT_TTL_MS, INVITE_DEFAULT_MAX_USE);
        ownerInvite.setCode(code);
        familyInviteMapper.insert(ownerInvite);

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
    @Deprecated
    public InviteCodeResp getInviteCode() {
        LoginUser ctx = requireLogin();
        requireParent(ctx);
        Long familyId = ctx.activeFamilyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "无家庭上下文");
        }
        // 兼容旧接口：返回本家庭一张 ACTIVE 的家长邀请码（不存在或过期则补生成）。
        FamilyInvite invite = familyInviteMapper.selectOne(new QueryWrapper<FamilyInvite>()
                .eq("family_id", familyId).eq("invite_type", InviteType.PARENT.name())
                .eq("status", "ACTIVE").eq("delete_at", 0).last("LIMIT 1"));
        if (invite == null || invite.getExpireAt() != null && invite.getExpireAt() <= System.currentTimeMillis()) {
            invite = buildInvite(familyId, InviteType.PARENT, ctx.getUserId(), INVITE_DEFAULT_TTL_MS, INVITE_DEFAULT_MAX_USE);
            familyInviteMapper.insert(invite);
        }
        return InviteCodeResp.builder()
                .inviteCode(invite.getCode())
                .expireAt(invite.getExpireAt())
                .qrBase64(null)
                .build();
    }

    @Override
    @Transactional
    public FamilyInviteResp generateInviteCode(GenerateInviteCodeReq req) {
        LoginUser ctx = requireLogin();
        requireParent(ctx);
        Long familyId = ctx.activeFamilyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "无家庭上下文");
        }
        if (!InviteType.isValid(req.getInviteType())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "inviteType 仅支持 PARENT / CHILD");
        }
        long ttlMs = (req.getExpireHours() != null && req.getExpireHours() > 0)
                ? req.getExpireHours() * 3600 * 1000 : INVITE_DEFAULT_TTL_MS;
        int maxUse = (req.getMaxUse() != null && req.getMaxUse() >= 0) ? req.getMaxUse() : INVITE_DEFAULT_MAX_USE;
        InviteType type = InviteType.valueOf(req.getInviteType().trim().toUpperCase());
        FamilyInvite invite = buildInvite(familyId, type, ctx.getUserId(), ttlMs, maxUse);
        familyInviteMapper.insert(invite);
        return toResp(invite);
    }

    @Override
    @Transactional
    public List<FamilyInviteResp> listInviteCodes() {
        LoginUser ctx = requireLogin();
        requireParent(ctx);
        Long familyId = ctx.activeFamilyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "无家庭上下文");
        }
        List<FamilyInvite> invites = familyInviteMapper.selectList(new QueryWrapper<FamilyInvite>()
                .eq("family_id", familyId).eq("delete_at", 0).orderByDesc("id"));
        return invites.stream().map(this::toResp).toList();
    }

    @Override
    @Transactional
    public void revokeInviteCode(RevokeInviteCodeReq req) {
        LoginUser ctx = requireLogin();
        requireParent(ctx);
        Long familyId = ctx.activeFamilyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "无家庭上下文");
        }
        FamilyInvite invite = familyInviteMapper.selectById(req.getInviteId());
        if (invite == null || invite.getDeleteAt() != null && invite.getDeleteAt() != 0) {
            throw new BizException(ResultCode.E404_NOT_FOUND, "邀请码不存在");
        }
        if (!invite.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "越权操作他人家庭邀请码");
        }
        invite.setStatus("REVOKED");
        familyInviteMapper.updateById(invite);
    }

    @Override
    @Transactional
    public JoinFamilyResp joinFamily(JoinFamilyReq req) {
        policy.requireCollection();
        LoginUser ctx = requireLogin();
        // 多家庭：移除 requireChild；任何已登录用户均可凭邀请码加入，角色由邀请码类型决定。

        FamilyInvite invite = familyInviteMapper.selectOne(new QueryWrapper<FamilyInvite>()
                .eq("code", req.getInviteCode()).eq("delete_at", 0).last("FOR UPDATE"));
        if (invite == null || !"ACTIVE".equals(invite.getStatus())
                || invite.getExpireAt() == null || invite.getExpireAt() <= System.currentTimeMillis()
                || (invite.getMaxUse() != null && invite.getMaxUse() > 0
                    && invite.getUsedCount() != null && invite.getUsedCount() >= invite.getMaxUse())) {
            throw new BizException(ResultCode.E003_INVITE_CODE_EXPIRED, "邀请码不存在、已过期、已撤销或已达使用上限");
        }
        Long familyId = invite.getFamilyId();
        Family family = familyMapper.selectById(familyId);
        if (family == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "家庭不存在");
        }
        User user = users.selectById(ctx.getUserId());
        if (user == null || !"NORMAL".equals(user.getStatus())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "账号状态异常");
        }

        // 同家庭重复：已绑定 / 待审直接返回；已拒绝则允许重新申请。
        FamilyMember existed = familyMemberMapper.selectOne(new QueryWrapper<FamilyMember>()
                .eq("family_id", familyId).eq("user_id", ctx.getUserId()).last("FOR UPDATE"));
        if (existed != null && !"REJECTED".equals(existed.getBindStatus())) {
            return JoinFamilyResp.builder().applyId(existed.getId()).status(existed.getBindStatus()).build();
        }
        // 多家庭：移除「已有其他有效家庭申请或绑定」E007 限制，允许一人同时属于多个家庭。

        // 角色由邀请码类型锁定：家长码 → PARENT/BOUND（免审）；学生码 → CHILD/PENDING（需审）。
        String role = invite.getInviteType();
        String bindStatus = InviteType.PARENT.name().equals(role)
                ? BindStatusEnum.BOUND.name()
                : BindStatusEnum.PENDING.name();
        String relationLabel = resolveRelationLabel(req.getRelationLabel(), role);

        FamilyMember member = existed == null ? new FamilyMember() : existed;
        member.setFamilyId(familyId);
        member.setUserId(ctx.getUserId());
        member.setRole(role);
        member.setBindStatus(bindStatus);
        member.setRelationLabel(relationLabel);
        member.setGuardianStatus(GuardianStatusEnum.UNVERIFIED.name());
        if (existed == null) {
            familyMemberMapper.insert(member);
        } else {
            member.setApplicationVersion(member.getApplicationVersion() + 1);
            familyMemberMapper.updateById(member);
        }

        // 累加邀请码使用次数（本次新加入 / 重新申请计数一次）。
        invite.setUsedCount((invite.getUsedCount() == null ? 0 : invite.getUsedCount()) + 1);
        familyInviteMapper.updateById(invite);

        auditService.record(AuditService.ACTION_JOIN, ctx.getUserId(), familyId, "FAMILY_MEMBER", member.getId(), null, "join family via invite");
        notices.recordBinding(member, family.getOwnerUserId());

        return JoinFamilyResp.builder()
                .applyId(member.getId())
                .status(bindStatus)
                .build();
    }

    @Override
    @Transactional
    public BindApproveResp bindApprove(BindApproveReq req) {
        LoginUser ctx = requireLogin();
        requireParent(ctx);
        Long familyId = ctx.activeFamilyId();

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
            // 多家长：同意可由家庭内任意 BOUND PARENT 授予（见 ChildAuthorizationService.requireConsent）。
            var consent = authorization.requireConsent(member);
            member.setGuardianStatus(consent.getGuardianStatus());
            // 多家庭：允许同一儿童同时属于多个家庭，故移除「已绑定其他家庭」拦截。
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
        Long familyId = requireLogin().activeFamilyId();
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

    @Override
    @Transactional
    public List<MyFamilyResp> myFamilies() {
        LoginUser ctx = requireLogin();
        List<FamilyMember> memberships = familyMemberMapper.selectList(new QueryWrapper<FamilyMember>()
                .eq("user_id", ctx.getUserId()).eq("delete_at", 0).orderByAsc("id"));
        List<MyFamilyResp> result = new ArrayList<>();
        for (FamilyMember m : memberships) {
            Family f = familyMapper.selectById(m.getFamilyId());
            if (f == null) {
                continue;
            }
            result.add(MyFamilyResp.builder()
                    .familyId(f.getId())
                    .familyName(f.getFamilyName())
                    .role(m.getRole())
                    .bindStatus(m.getBindStatus())
                    .relationLabel(m.getRelationLabel())
                    .isOwner(f.getOwnerUserId() != null && f.getOwnerUserId().equals(ctx.getUserId()))
                    .build());
        }
        return result;
    }

    @Override
    @Transactional
    public void leaveFamily() {
        LoginUser ctx = requireLogin();
        Long familyId = ctx.activeFamilyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "无家庭上下文");
        }
        Family family = familyMapper.selectById(familyId);
        if (family != null && family.getOwnerUserId() != null && family.getOwnerUserId().equals(ctx.getUserId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "owner 不可直接退出，请先转让或解散家庭");
        }
        FamilyMember me = familyMemberMapper.selectOne(new QueryWrapper<FamilyMember>()
                .eq("family_id", familyId).eq("user_id", ctx.getUserId()).last("FOR UPDATE"));
        if (me == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND, "您不属于该家庭");
        }
        // 软删：仅解除在当前家庭的绑定关系，不影响其在其他家庭的归属。
        // 注意：delete_at 是 MyBatis-Plus 逻辑删除字段，updateById 不会将其写入 SET，
        // 须用 UpdateWrapper 显式设置 delete_at 与 bind_status。
        final Long leaveMemberId = me.getId();
        familyMemberMapper.update(null, new UpdateWrapper<FamilyMember>()
                .eq("id", leaveMemberId)
                .eq("delete_at", 0)
                .set("bind_status", "REMOVED")
                .set("delete_at", System.currentTimeMillis()));
        auditService.record(AuditService.ACTION_LEAVE, ctx.getUserId(), familyId, "FAMILY_MEMBER", leaveMemberId, null, "leave family");
    }

    @Override
    @Transactional
    public void removeMember(RemoveMemberReq req) {
        LoginUser ctx = requireLogin();
        requireParent(ctx);
        Long familyId = ctx.activeFamilyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "无家庭上下文");
        }
        FamilyMember target = familyMemberMapper.selectById(req.getMemberId());
        if (target == null || target.getDeleteAt() != null && target.getDeleteAt() != 0) {
            throw new BizException(ResultCode.E404_NOT_FOUND, "成员不存在");
        }
        if (!target.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "越权操作他人家庭成员");
        }
        Family family = familyMapper.selectById(familyId);
        if (family != null && family.getOwnerUserId() != null && family.getOwnerUserId().equals(target.getUserId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "不可移除 owner，请先转让");
        }
        // 软删：仅解除目标成员在本家庭的绑定关系。
        // 注意：delete_at 是 MyBatis-Plus 逻辑删除字段，updateById 不会将其写入 SET，
        // 须用 UpdateWrapper 显式设置 delete_at 与 bind_status。
        final Long removeTargetId = target.getId();
        familyMemberMapper.update(null, new UpdateWrapper<FamilyMember>()
                .eq("id", removeTargetId)
                .eq("delete_at", 0)
                .set("bind_status", "REMOVED")
                .set("delete_at", System.currentTimeMillis()));
        auditService.record(AuditService.ACTION_REMOVE_MEMBER, ctx.getUserId(), familyId, "FAMILY_MEMBER", removeTargetId, null, "remove member");
    }

    @Override
    @Transactional
    public void transferOwner(TransferOwnerReq req) {
        LoginUser ctx = requireLogin();
        Long familyId = ctx.activeFamilyId();
        if (familyId == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "无家庭上下文");
        }
        Family family = familyMapper.selectById(familyId);
        if (family == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "家庭不存在");
        }
        if (!ctx.getUserId().equals(family.getOwnerUserId())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "仅当前 owner 可发起转让");
        }
        Long target = req.getTargetUserId();
        if (target == null || target.equals(ctx.getUserId())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "目标用户非法");
        }
        FamilyMember targetMember = familyMemberMapper.selectOne(new QueryWrapper<FamilyMember>()
                .eq("family_id", familyId).eq("user_id", target)
                .eq("role", "PARENT").eq("bind_status", "BOUND").last("FOR UPDATE"));
        if (targetMember == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "目标须为本家庭内已绑定的 PARENT");
        }
        // 手写乐观锁：owner_user_id 由当前 owner 更新为 target，0 行 → E007 并发冲突。
        int rows = familyMapper.update(null, new UpdateWrapper<Family>()
                .eq("id", familyId).eq("owner_user_id", ctx.getUserId())
                .set("owner_user_id", target));
        if (rows != 1) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "转让失败，请重试");
        }
        auditService.record(AuditService.ACTION_TRANSFER_OWNER, ctx.getUserId(), familyId, "FAMILY", familyId, target.toString(), "transfer owner");
    }

    private FamilyChildResp childResponse(FamilyMember member) {
        return FamilyChildResp.builder().familyId(member.getFamilyId()).childId(member.getUserId())
                .applyId(member.getId()).bindStatus(member.getBindStatus())
                .applicationVersion(member.getApplicationVersion()).relationLabel(member.getRelationLabel()).build();
    }

    /** 构建一张邀请码（不落库）。 */
    private FamilyInvite buildInvite(Long familyId, InviteType type, Long creator, long ttlMs, int maxUse) {
        FamilyInvite invite = new FamilyInvite();
        invite.setFamilyId(familyId);
        invite.setCode(generateUniqueInviteCode());
        invite.setInviteType(type.name());
        invite.setCreatorUserId(creator);
        invite.setStatus("ACTIVE");
        invite.setExpireAt(System.currentTimeMillis() + ttlMs);
        invite.setMaxUse(maxUse);
        invite.setUsedCount(0);
        return invite;
    }

    private FamilyInviteResp toResp(FamilyInvite invite) {
        return FamilyInviteResp.builder()
                .id(invite.getId())
                .inviteCode(invite.getCode())
                .inviteType(invite.getInviteType())
                .status(invite.getStatus())
                .expireAt(invite.getExpireAt())
                .maxUse(invite.getMaxUse())
                .usedCount(invite.getUsedCount())
                .creatorUserId(invite.getCreatorUserId())
                .build();
    }

    /** 关系标签解析：支持枚举名（PATERNAL_GF）或中文标签（爷爷）；非法 / 空则按角色取默认。 */
    private String resolveRelationLabel(String provided, String role) {
        String label = RelationEnum.resolveLabel(provided);
        if (label != null) {
            return label;
        }
        return InviteType.PARENT.name().equals(role) ? "家长" : "孩子";
    }

    /** 生成全局唯一邀请码（最多重试 5 次），同时保证在 usr_family 旧列与 usr_family_invite 新表中均不重复。 */
    private String generateUniqueInviteCode() {
        for (int i = 0; i < 5; i++) {
            String code = InviteCodeUtil.randomCode();
            Family existed = familyMapper.selectOne(new QueryWrapper<Family>().eq("invite_code", code));
            FamilyInvite existedInvite = familyInviteMapper.selectOne(new QueryWrapper<FamilyInvite>().eq("code", code).eq("delete_at", 0));
            if (existed == null && existedInvite == null) {
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
