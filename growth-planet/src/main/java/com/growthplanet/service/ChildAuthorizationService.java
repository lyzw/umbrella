package com.growthplanet.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.growthplanet.common.context.UserContext;
import com.growthplanet.common.exception.BizException;
import com.growthplanet.common.result.ResultCode;
import com.growthplanet.config.ComplianceProperties;
import com.growthplanet.entity.*;
import com.growthplanet.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ChildAuthorizationService {
    private final FamilyMapper families;
    private final UserMapper users;
    private final FamilyMemberMapper members;
    private final ConsentLogMapper consents;
    private final ComplianceProperties policy;

    public ChildAuthorizationService(FamilyMapper families, UserMapper users,
            FamilyMemberMapper members, ConsentLogMapper consents, ComplianceProperties policy) {
        this.families = families;
        this.users = users;
        this.members = members;
        this.consents = consents;
        this.policy = policy;
    }

    // 所有儿童授权写操作固定按家庭、儿童账户、关系加锁，撤回与写入共用此入口。
    public Family lockScope(Long familyId, Long childId) {
        Family family = families.selectOne(new QueryWrapper<Family>().eq("id", familyId).last("FOR UPDATE"));
        User child = users.selectOne(new QueryWrapper<User>().eq("id", childId).last("FOR UPDATE"));
        if (family == null || child == null || !"CHILD".equals(child.getRole())
                || !"NORMAL".equals(child.getStatus())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        return family;
    }

    public FamilyMember lockChild(Long familyId, Long childId) {
        lockScope(familyId, childId);
        requireParent(familyId);
        FamilyMember member = members.selectOne(new QueryWrapper<FamilyMember>()
                .eq("family_id", familyId).eq("user_id", childId).eq("role", "CHILD").last("FOR UPDATE"));
        if (member == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        return member;
    }

    public void requireParent(Long familyId) {
        var ctx = UserContext.get();
        if (ctx == null || !"PARENT".equals(ctx.getRole()) || familyId == null
                || members.selectCount(new QueryWrapper<FamilyMember>().eq("family_id", familyId)
                .eq("user_id", ctx.getUserId()).eq("role", "PARENT").eq("bind_status", "BOUND")) != 1) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
    }

    public ConsentLog latest(FamilyMember member, String type) {
        return consents.selectOne(new QueryWrapper<ConsentLog>()
                .eq("family_id", member.getFamilyId()).eq("child_id", member.getUserId())
                .eq("user_id", UserContext.userId()).eq("apply_id", member.getId())
                .eq("application_version", member.getApplicationVersion()).eq("consent_type", type)
                .orderByDesc("id").last("LIMIT 1 FOR UPDATE"));
    }

    public ConsentLog requireConsent(FamilyMember member) {
        policy.requireCollection();
        ConsentLog latest = latest(member, "PROFILE");
        if (latest == null || !"GRANT".equals(latest.getAction())
                || !policy.getAgreementVersion().equals(latest.getVersion())
                || latest.getExpireAt() == null || latest.getExpireAt() <= System.currentTimeMillis()
                || !("VERIFIED".equals(latest.getGuardianStatus())
                || policy.isSelfAttestationAccepted() && "SELF_ATTESTED".equals(latest.getGuardianStatus()))) {
            throw new BizException(ResultCode.E010_CONSENT_REVOKED);
        }
        return latest;
    }
}
