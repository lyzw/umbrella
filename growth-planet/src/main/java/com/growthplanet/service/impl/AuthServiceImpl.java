package com.growthplanet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.growthplanet.common.context.LoginUser;
import com.growthplanet.common.context.UserContext;
import com.growthplanet.common.enums.ProfileStatusEnum;
import com.growthplanet.common.enums.RoleEnum;
import com.growthplanet.common.exception.BizException;
import com.growthplanet.common.result.ResultCode;
import com.growthplanet.dto.request.ChildProfileReq;
import com.growthplanet.dto.request.SelectRoleReq;
import com.growthplanet.dto.request.WxLoginReq;
import com.growthplanet.dto.response.ChildProfileResp;
import com.growthplanet.dto.response.SelectRoleResp;
import com.growthplanet.dto.response.WxLoginResp;
import com.growthplanet.entity.ChildProfile;
import com.growthplanet.entity.FamilyMember;
import com.growthplanet.entity.User;
import com.growthplanet.mapper.ChildProfileMapper;
import com.growthplanet.mapper.FamilyMemberMapper;
import com.growthplanet.mapper.UserMapper;
import com.growthplanet.service.AuditService;
import com.growthplanet.service.AuthService;
import com.growthplanet.service.WechatClient;
import com.growthplanet.util.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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

    public AuthServiceImpl(UserMapper userMapper, ChildProfileMapper childProfileMapper,
                           FamilyMemberMapper familyMemberMapper, WechatClient wechatClient,
                           JwtUtil jwtUtil, AuditService auditService) {
        this.userMapper = userMapper;
        this.childProfileMapper = childProfileMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.wechatClient = wechatClient;
        this.jwtUtil = jwtUtil;
        this.auditService = auditService;
    }

    @Override
    public WxLoginResp wxLogin(WxLoginReq req) {
        if (req == null || req.getCode() == null || req.getCode().isBlank()) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "缺少 code");
        }
        WechatClient.WxSession session = wechatClient.code2Session(req.getCode());
        String openid = session.getOpenid();
        if (openid == null || openid.isBlank()) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "微信未返回 openid");
        }

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("openid", openid));
        boolean isNew = false;
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setUnionid(session.getUnionid());
            user.setRole(RoleEnum.UNSET.name());
            user.setStatus("NORMAL");
            userMapper.insert(user);
            isNew = true;
        }

        auditService.record(AuditService.ACTION_LOGIN, user.getId(), null, "USER", user.getId(), null, "wx-login");

        LoginUser loginUser = new LoginUser(user.getId(), user.getRole(), List.of(), UUID.randomUUID().toString());
        String token = jwtUtil.generateToken(loginUser);
        return WxLoginResp.builder()
                .token(token)
                .openid(openid)
                .role(user.getRole())
                .isNew(isNew)
                .build();
    }

    @Override
    @Transactional
    public SelectRoleResp selectRole(SelectRoleReq req) {
        LoginUser ctx = requireLogin();
        RoleEnum role;
        try {
            role = RoleEnum.valueOf(req.getRole());
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "非法角色");
        }
        if (role != RoleEnum.CHILD && role != RoleEnum.PARENT) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "角色仅可由前端选择 CHILD/PARENT");
        }

        User user = userMapper.selectById(ctx.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "用户不存在");
        }
        user.setRole(role.name());
        userMapper.updateById(user);

        auditService.record(AuditService.ACTION_ROLE, user.getId(), null, "USER", user.getId(), null,
                "select-role=" + role.name());

        // 重新签发 token（携带最新角色与既有 family_ids）
        LoginUser updated = new LoginUser(user.getId(), role.name(), ctx.getFamilyIds(), UUID.randomUUID().toString());
        String token = jwtUtil.generateToken(updated);
        String nextStep = role == RoleEnum.CHILD ? "child-profile" : "create-family";
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
        // 角色由 @RequireRole(CHILD) 保证；此处做数据归属校验：儿童必须已加入并审批通过某家庭
        FamilyMember member = familyMemberMapper.selectOne(new QueryWrapper<FamilyMember>()
                .eq("user_id", ctx.getUserId())
                .eq("role", RoleEnum.CHILD.name())
                .eq("bind_status", "APPROVED"));
        if (member == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "儿童尚未加入已审批家庭");
        }
        Long familyId = member.getFamilyId();

        ChildProfile existing = childProfileMapper.selectOne(new QueryWrapper<ChildProfile>().eq("user_id", ctx.getUserId()));
        ChildProfile profile = new ChildProfile();
        profile.setUserId(ctx.getUserId());
        profile.setFamilyId(familyId);
        profile.setNickname(req.getNickname());
        profile.setGrade(req.getGrade());
        profile.setSchool(req.getSchool());
        profile.setAllergies(req.getAllergies());
        profile.setDislikes(req.getDislikes());
        profile.setTastes(req.getTastes());
        profile.setProfileStatus(ProfileStatusEnum.COMPLETED.name());

        if (existing == null) {
            childProfileMapper.insert(profile);
        } else {
            profile.setId(existing.getId());
            childProfileMapper.updateById(profile);
        }
        return ChildProfileResp.builder().profileStatus(ProfileStatusEnum.COMPLETED.name()).build();
    }

    private LoginUser requireLogin() {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH);
        }
        return user;
    }
}
