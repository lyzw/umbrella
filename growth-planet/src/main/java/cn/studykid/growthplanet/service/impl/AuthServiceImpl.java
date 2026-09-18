package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.service.ChildAuthorizationService;
import cn.studykid.growthplanet.service.SessionService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.studykid.growthplanet.common.context.LoginUser;
import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.enums.ProfileStatusEnum;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.ChildProfileReq;
import cn.studykid.growthplanet.dto.request.SelectRoleReq;
import cn.studykid.growthplanet.dto.request.WxLoginReq;
import cn.studykid.growthplanet.dto.response.ChildProfileResp;
import cn.studykid.growthplanet.dto.response.SelectRoleResp;
import cn.studykid.growthplanet.dto.response.WxLoginResp;
import cn.studykid.growthplanet.entity.ChildProfile;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.mapper.ChildProfileMapper;
import cn.studykid.growthplanet.mapper.FamilyMemberMapper;
import cn.studykid.growthplanet.mapper.UserMapper;
import cn.studykid.growthplanet.service.AuditService;
import cn.studykid.growthplanet.service.AuthService;
import cn.studykid.growthplanet.service.WechatClient;
import cn.studykid.growthplanet.util.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUTH 模块业务实现。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final ChildProfileMapper childProfileMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final WechatClient wechatClient;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;
    private final SessionService sessions;
    private final ChildAuthorizationService authorization;
    private final ComplianceProperties policy;

    public AuthServiceImpl(UserMapper userMapper, ChildProfileMapper childProfileMapper,
                           FamilyMemberMapper familyMemberMapper, WechatClient wechatClient,
                           JwtUtil jwtUtil, AuditService auditService,
                           SessionService sessions,
                           ChildAuthorizationService authorization,
                           ComplianceProperties policy) {
        this.userMapper = userMapper;
        this.childProfileMapper = childProfileMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.wechatClient = wechatClient;
        this.jwtUtil = jwtUtil;
        this.auditService = auditService;
        this.sessions = sessions;
        this.authorization = authorization;
        this.policy = policy;
    }

    @Override
    @Transactional
    public WxLoginResp wxLogin(WxLoginReq req) {
        policy.requireCollection();
        if (req == null || req.getCode() == null || req.getCode().isBlank()) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "缺少 code");
        }
        WechatClient.WxSession session = wechatClient.code2Session(req.getCode());
        String openid = session.getOpenid();
        if (openid == null || openid.isBlank() || openid.length() > 64
                || session.getUnionid() != null && session.getUnionid().length() > 64) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "微信未返回 openid");
        }

        User user = userMapper.findIdentityIncludingDeleted(openid);
        boolean isNew = false;
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setUnionid(session.getUnionid());
            user.setRole(RoleEnum.UNSELECTED.name());
            user.setStatus("NORMAL");
            userMapper.insert(user);
            isNew = true;
        }
        if (!"NORMAL".equals(user.getStatus()) || user.getDeleteAt() != 0L) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH);
        }

        auditService.record(AuditService.ACTION_LOGIN, user.getId(), null, "USER", user.getId(), null, "wx-login");

        LoginUser loginUser = sessions.current(user);
        String token = jwtUtil.generateToken(loginUser);
        return WxLoginResp.builder()
                .token(token)
                .openid(openid)
                .role(user.getRole())
                .isNew(isNew)
                .expiresIn(jwtUtil.getExpiresIn())
                .build();
    }

    @Override
    @Transactional
    public SelectRoleResp selectRole(SelectRoleReq req) {
        policy.requireCollection();
        LoginUser ctx = requireLogin();
        RoleEnum role;
        try {
            role = RoleEnum.valueOf(req.getRole());
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "非法角色");
        }
        if (role != RoleEnum.CHILD && role != RoleEnum.PARENT) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "角色仅可选择 CHILD/PARENT");
        }

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("id", ctx.getUserId()).last("FOR UPDATE"));
        if (user == null) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "用户不存在");
        }
        if (!RoleEnum.UNSELECTED.name().equals(user.getRole()) && !role.name().equals(user.getRole())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "角色不可切换");
        }
        if (RoleEnum.UNSELECTED.name().equals(user.getRole())) {
            user.setRole(role.name());
            user.setTokenVersion(user.getTokenVersion() + 1);
            userMapper.updateById(user);
        }

        auditService.record(AuditService.ACTION_ROLE, user.getId(), null, "USER", user.getId(), null,
                "select-role=" + role.name());

        // 重新签发 token（携带最新角色与既有 family_ids）
        LoginUser updated = sessions.current(user);
        String token = jwtUtil.generateToken(updated);
        String nextStep = role == RoleEnum.CHILD ? "join-family" : "create-family";
        return SelectRoleResp.builder()
                .role(role.name())
                .token(token)
                .nextStep(nextStep)
                .build();
    }

    @Override
    @Transactional
    public ChildProfileResp saveChildProfile(ChildProfileReq req) {
        LoginUser ctx = requireLogin();
        FamilyMember member = authorization.lockChild(ctx.firstFamilyId(), req.getChildId());
        if (!"BOUND".equals(member.getBindStatus())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "儿童尚未完成绑定");
        }
        var consent = authorization.requireConsent(member);
        policy.validateProfile(req);
        Long familyId = member.getFamilyId();

        ChildProfile existing = childProfileMapper.selectOne(new QueryWrapper<ChildProfile>()
                .eq("user_id", req.getChildId()).last("FOR UPDATE"));
        ChildProfile profile = new ChildProfile();
        profile.setUserId(req.getChildId());
        profile.setFamilyId(familyId);
        profile.setNickname(req.getNickname());
        profile.setGrade(req.getGrade());
        profile.setSchool(req.getSchool());
        profile.setAllergies(req.getAllergies());
        profile.setDislikes(req.getDislikes());
        profile.setTastes(req.getTastes());
        profile.setProfileStatus(ProfileStatusEnum.COMPLETE.name());

        if (existing == null) {
            childProfileMapper.insert(profile);
        } else {
            profile.setId(existing.getId());
            childProfileMapper.updateById(profile);
        }
        auditService.record("PROFILE", ctx.getUserId(), familyId, "CHILD", req.getChildId(), null,
                "consentId=" + consent.getId() + ";version=" + consent.getVersion());
        return ChildProfileResp.builder().profileStatus(ProfileStatusEnum.COMPLETE.name()).build();
    }

    @Override
    @Transactional
    public void logout() {
        User user = userMapper.selectOne(new QueryWrapper<User>()
                .eq("id", requireLogin().getUserId()).last("FOR UPDATE"));
        if (user == null) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH);
        }
        user.setTokenVersion(user.getTokenVersion() + 1);
        userMapper.updateById(user);
        auditService.record("LOGOUT", user.getId(), null, "USER", user.getId(), null, "all sessions");
    }

    private LoginUser requireLogin() {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH);
        }
        return user;
    }
}
