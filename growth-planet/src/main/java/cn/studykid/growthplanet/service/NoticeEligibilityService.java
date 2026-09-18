package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.entity.*;
import cn.studykid.growthplanet.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = BizException.class)
public class NoticeEligibilityService {
    private final FamilyMapper families;
    private final FamilyMemberMapper members;
    private final UserMapper users;
    private final ConsentLogMapper consents;
    private final ComplianceProperties policy;

    public NoticeEligibilityService(FamilyMapper families, FamilyMemberMapper members,
            UserMapper users, ConsentLogMapper consents, ComplianceProperties policy) {
        this.families = families;
        this.members = members;
        this.users = users;
        this.consents = consents;
        this.policy = policy;
    }

    public Family lockScope(Notice notice) {
        Family family = families.selectOne(new QueryWrapper<Family>()
                .eq("id", notice.getFamilyId()).last("FOR UPDATE"));
        User child = users.selectOne(new QueryWrapper<User>().eq("id", notice.getChildId()).last("FOR UPDATE"));
        if (family == null || child == null || !"CHILD".equals(child.getRole())
                || !"NORMAL".equals(child.getStatus())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        return family;
    }

    // Background workers have no UserContext. Recheck the actual owner, relation and
    // consent under the same family/child locks used by revoke, without impersonation.
    public Eligibility requireEligible(Notice notice) {
        Family family = lockScope(notice);
        policy.requireCollection();
        FamilyMember member = members.selectOne(new QueryWrapper<FamilyMember>()
                .eq("family_id", family.getId()).eq("user_id", notice.getChildId())
                .eq("role", "CHILD").last("FOR UPDATE"));
        User guardian = users.selectById(family.getOwnerUserId());
        User receiver = users.selectById(notice.getReceiverId());
        if (member == null || !"BOUND".equals(member.getBindStatus())
                || guardian == null || !"PARENT".equals(guardian.getRole())
                || !"NORMAL".equals(guardian.getStatus())
                || members.selectCount(new QueryWrapper<FamilyMember>().eq("family_id", family.getId())
                .eq("user_id", guardian.getId()).eq("role", "PARENT").eq("bind_status", "BOUND")) != 1
                || receiver == null || !"NORMAL".equals(receiver.getStatus())
                || !(notice.getReceiverId().equals(guardian.getId())
                || notice.getReceiverId().equals(member.getUserId()))) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        ConsentLog consent = consents.selectOne(new QueryWrapper<ConsentLog>()
                .eq("family_id", family.getId()).eq("child_id", member.getUserId())
                .eq("user_id", guardian.getId()).eq("apply_id", member.getId())
                .eq("application_version", member.getApplicationVersion()).eq("consent_type", "PROFILE")
                .orderByDesc("id").last("LIMIT 1 FOR UPDATE"));
        if (consent == null || !"GRANT".equals(consent.getAction())
                || !policy.getAgreementVersion().equals(consent.getVersion())
                || consent.getExpireAt() == null || consent.getExpireAt() <= System.currentTimeMillis()
                || !("VERIFIED".equals(consent.getGuardianStatus())
                || policy.isSelfAttestationAccepted() && "SELF_ATTESTED".equals(consent.getGuardianStatus()))) {
            throw new BizException(ResultCode.E010_CONSENT_REVOKED);
        }
        return new Eligibility(consent.getId(), receiver.getOpenid());
    }

    public record Eligibility(Long consentId, String openid) {
    }
}
