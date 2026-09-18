package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.entity.ConsentLog;
import cn.studykid.growthplanet.entity.Family;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.mapper.ConsentLogMapper;
import cn.studykid.growthplanet.mapper.FamilyMapper;
import cn.studykid.growthplanet.mapper.FamilyMemberMapper;
import cn.studykid.growthplanet.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.entity.*;
import cn.studykid.growthplanet.mapper.*;
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

    public FamilyMember lockBoundChild(Long childId) {
        var ctx = UserContext.get();
        if (ctx == null || childId == null || childId <= 0) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        FamilyMember member;
        if ("PARENT".equals(ctx.getRole())) {
            member = lockChild(ctx.firstFamilyId(), childId);
        } else if ("CHILD".equals(ctx.getRole()) && childId.equals(ctx.getUserId())) {
            // 仅用预查询定位锁范围；授权状态必须在取得家庭/儿童锁后重新读取。
            member = members.selectOne(new QueryWrapper<FamilyMember>()
                    .eq("user_id", childId).eq("role", "CHILD").eq("bind_status", "BOUND"));
            if (member == null) {
                throw new BizException(ResultCode.E009_FORBIDDEN);
            }
            lockScope(member.getFamilyId(), childId);
            member = members.selectOne(new QueryWrapper<FamilyMember>()
                    .eq("id", member.getId()).eq("user_id", childId).eq("role", "CHILD").last("FOR UPDATE"));
        } else {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        if (member == null || !"BOUND".equals(member.getBindStatus())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "儿童尚未完成绑定");
        }
        return member;
    }

    public ConsentLog latest(FamilyMember member, String type) {
        return latest(member, type, UserContext.userId());
    }

    private ConsentLog latest(FamilyMember member, String type, Long grantorId) {
        return consents.selectOne(new QueryWrapper<ConsentLog>()
                .eq("family_id", member.getFamilyId()).eq("child_id", member.getUserId())
                .eq("user_id", grantorId).eq("apply_id", member.getId())
                .eq("application_version", member.getApplicationVersion()).eq("consent_type", type)
                .orderByDesc("id").last("LIMIT 1 FOR UPDATE"));
    }

    public ConsentLog requireConsent(FamilyMember member) {
        policy.requireCollection();
        Long grantorId = UserContext.userId();
        if ("CHILD".equals(UserContext.get().getRole())) {
            if (!grantorId.equals(member.getUserId()) || !"BOUND".equals(member.getBindStatus())) {
                throw new BizException(ResultCode.E009_FORBIDDEN);
            }
            // Sprint 1 仅家庭创建家长可授予同意；儿童不被视为同意人。
            Family family = families.selectById(member.getFamilyId());
            User guardian = family == null ? null : users.selectById(family.getOwnerUserId());
            if (guardian == null || !"PARENT".equals(guardian.getRole()) || !"NORMAL".equals(guardian.getStatus())
                    || members.selectCount(new QueryWrapper<FamilyMember>().eq("family_id", member.getFamilyId())
                    .eq("user_id", guardian.getId()).eq("role", "PARENT").eq("bind_status", "BOUND")) != 1) {
                throw new BizException(ResultCode.E009_FORBIDDEN);
            }
            grantorId = guardian.getId();
        }
        ConsentLog latest = latest(member, "PROFILE", grantorId);
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
