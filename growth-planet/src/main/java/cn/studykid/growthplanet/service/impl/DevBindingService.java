package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.common.context.LoginUser;
import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.enums.BindStatusEnum;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.dto.request.BindApproveReq;
import cn.studykid.growthplanet.dto.request.CreateFamilyReq;
import cn.studykid.growthplanet.dto.request.JoinFamilyReq;
import cn.studykid.growthplanet.dto.request.QuickBindParentReq;
import cn.studykid.growthplanet.dto.response.CreateFamilyResp;
import cn.studykid.growthplanet.dto.response.InviteCodeResp;
import cn.studykid.growthplanet.dto.response.JoinFamilyResp;
import cn.studykid.growthplanet.dto.response.QuickBindParentResp;
import cn.studykid.growthplanet.entity.ConsentLog;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.mapper.ConsentLogMapper;
import cn.studykid.growthplanet.mapper.FamilyMemberMapper;
import cn.studykid.growthplanet.mapper.UserMapper;
import cn.studykid.growthplanet.service.FamilyService;
import cn.studykid.growthplanet.service.SessionService;
import cn.studykid.growthplanet.service.WechatClient;
import cn.studykid.growthplanet.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 开发/测试专用：儿童一键绑定家长（绕过微信 OAuth 与邀请码握手）。
 * 仅 dev / test profile 启用，生产环境不加载此 Bean。
 */
@Service
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class DevBindingService {

    private final WechatClient wechatClient;
    private final UserMapper users;
    private final FamilyMemberMapper familyMemberMapper;
    private final ConsentLogMapper consents;
    private final FamilyService familyService;
    private final SessionService sessions;
    private final JwtUtil jwtUtil;
    private final ComplianceProperties policy;

    @Transactional
    public QuickBindParentResp quickBindParent(LoginUser child, QuickBindParentReq req) {
        policy.requireCollection();

        // 1. 解析家长账号 -> openid -> User（不存在则建合成账号）
        String openid = wechatClient.code2Session(req.getParentAccount()).getOpenid();
        User parent = users.findIdentityIncludingDeleted(openid);
        if (parent == null) {
            parent = new User();
            parent.setOpenid(openid);
            parent.setUnionid("mock_unionid_" + req.getParentAccount());
            parent.setRole(RoleEnum.UNSELECTED.name());
            parent.setStatus("NORMAL");
            users.insert(parent);
        } else if (!"NORMAL".equals(parent.getStatus()) || parent.getDeleteAt() != 0L) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "家长账号已失效，请更换");
        }
        // 未选角色的家长账号，测试便捷自动选为 PARENT
        if (RoleEnum.UNSELECTED.name().equals(parent.getRole())) {
            parent.setRole(RoleEnum.PARENT.name());
            parent.setTokenVersion(parent.getTokenVersion() + 1);
            users.updateById(parent);
        } else if (!RoleEnum.PARENT.name().equals(parent.getRole())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "该账号不是家长角色");
        }

        // 2. 确定家长家庭的邀请码（无家庭则创建）
        LoginUser parentLogin = sessions.current(parent);
        Long familyId;
        String inviteCode;
        UserContext.set(parentLogin);
        try {
            FamilyMember owned = familyMemberMapper.selectOne(new QueryWrapper<FamilyMember>()
                    .eq("user_id", parent.getId()).eq("role", "PARENT").eq("bind_status", "BOUND").last("LIMIT 1"));
            if (owned == null) {
                CreateFamilyReq createReq = new CreateFamilyReq();
                createReq.setFamilyName(req.getParentAccount() + " 的家庭");
                CreateFamilyResp created = familyService.createFamily(createReq);
                familyId = created.getFamilyId();
                inviteCode = created.getInviteCode();
            } else {
                familyId = owned.getFamilyId();
                InviteCodeResp invite = familyService.getInviteCode();
                inviteCode = invite.getInviteCode();
            }
        } finally {
            UserContext.clear();
        }
        // 刷新家长上下文以携带（新建的）家庭 ID，供后续审批越权校验使用
        parentLogin = sessions.current(users.selectById(parent.getId()));

        // 3. 儿童 join 家庭
        LoginUser childLogin = sessions.current(users.selectById(child.getUserId()));
        UserContext.set(childLogin);
        Long applyId;
        try {
            JoinFamilyReq joinReq = new JoinFamilyReq();
            joinReq.setInviteCode(inviteCode);
            JoinFamilyResp joined = familyService.joinFamily(joinReq);
            applyId = joined.getApplyId();
        } finally {
            UserContext.clear();
        }

        // 4. 补一条合成 PROFILE 同意记录（bindApprove 通过需校验同意）
        FamilyMember member = familyMemberMapper.selectById(applyId);
        long now = System.currentTimeMillis();
        ConsentLog consent = new ConsentLog();
        consent.setUserId(parent.getId());
        consent.setChildId(child.getUserId());
        consent.setFamilyId(familyId);
        consent.setApplyId(applyId);
        consent.setApplicationVersion(member.getApplicationVersion());
        consent.setConsentType("PROFILE");
        consent.setAction("GRANT");
        consent.setVersion(policy.getAgreementVersion());
        consent.setSelfReportedAge(30);
        consent.setGuardianStatus("VERIFIED");
        consent.setSignedAt(now);
        consent.setExpireAt(now + 365L * 24 * 3600 * 1000);
        consents.insert(consent);

        // 5. 家长审批通过
        UserContext.set(parentLogin);
        try {
            BindApproveReq approveReq = new BindApproveReq();
            approveReq.setApplyId(applyId);
            approveReq.setApprove(true);
            familyService.bindApprove(approveReq);
        } finally {
            UserContext.clear();
        }

        // 6. 重新签发儿童 token（携带最新 family_ids）并返回
        User reloaded = users.selectById(child.getUserId());
        LoginUser freshChild = sessions.current(reloaded);
        String token = jwtUtil.generateToken(freshChild);
        return QuickBindParentResp.builder()
                .token(token)
                .role(freshChild.getRole())
                .expiresIn(jwtUtil.getExpiresIn())
                .familyId(familyId)
                .bindStatus(BindStatusEnum.BOUND.name())
                .build();
    }
}
