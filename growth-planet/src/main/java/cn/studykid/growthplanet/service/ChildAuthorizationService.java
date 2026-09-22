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
        return verifyScope(familyId, childId, true);
    }

    /** 只读版 scope 校验：与 lockScope 判定完全一致，但不加任何行锁（纯展示路径用）。 */
    public Family readScope(Long familyId, Long childId) {
        return verifyScope(familyId, childId, false);
    }

    private Family verifyScope(Long familyId, Long childId, boolean lock) {
        QueryWrapper<Family> familyQuery = new QueryWrapper<Family>().eq("id", familyId);
        QueryWrapper<User> childQuery = new QueryWrapper<User>().eq("id", childId);
        Family family = families.selectOne(lock ? familyQuery.last("FOR UPDATE") : familyQuery);
        User child = users.selectOne(lock ? childQuery.last("FOR UPDATE") : childQuery);
        if (family == null || child == null || !"CHILD".equals(child.getRole())
                || !"NORMAL".equals(child.getStatus())) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        return family;
    }

    public FamilyMember lockChild(Long familyId, Long childId) {
        lockScope(familyId, childId);
        requireParent(familyId);
        return childMember(familyId, childId, true);
    }

    /** 只读版：与 lockChild 判定完全一致，但不加行锁（纯展示路径用）。 */
    public FamilyMember readChild(Long familyId, Long childId) {
        readScope(familyId, childId);
        requireParent(familyId);
        return childMember(familyId, childId, false);
    }

    private FamilyMember childMember(Long familyId, Long childId, boolean lock) {
        QueryWrapper<FamilyMember> query = new QueryWrapper<FamilyMember>()
                .eq("family_id", familyId).eq("user_id", childId).eq("role", "CHILD");
        FamilyMember member = members.selectOne(lock ? query.last("FOR UPDATE") : query);
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

    /**
     * 只读版：与 lockBoundChild 校验完全一致（角色、绑定、家庭归属、家长成员关系），
     * 但全程不加任何行锁。供纯展示路径（每日菜单、想吃清单等）使用，避免读请求持锁阻塞写事务。
     */
    public FamilyMember boundChild(Long childId) {
        var ctx = UserContext.get();
        if (ctx == null || childId == null || childId <= 0) {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        FamilyMember member;
        if ("PARENT".equals(ctx.getRole())) {
            member = readChild(ctx.firstFamilyId(), childId);
        } else if ("CHILD".equals(ctx.getRole()) && childId.equals(ctx.getUserId())) {
            member = members.selectOne(new QueryWrapper<FamilyMember>()
                    .eq("user_id", childId).eq("role", "CHILD").eq("bind_status", "BOUND"));
        } else {
            throw new BizException(ResultCode.E009_FORBIDDEN);
        }
        if (member == null || !"BOUND".equals(member.getBindStatus())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "儿童尚未完成绑定");
        }
        return member;
    }

    public ConsentLog latest(FamilyMember member, String type) {
        return latest(member, type, UserContext.userId(), true);
    }

    private ConsentLog latest(FamilyMember member, String type, Long grantorId, boolean lock) {
        QueryWrapper<ConsentLog> query = new QueryWrapper<ConsentLog>()
                .eq("family_id", member.getFamilyId()).eq("child_id", member.getUserId())
                .eq("user_id", grantorId).eq("apply_id", member.getId())
                .eq("application_version", member.getApplicationVersion()).eq("consent_type", type)
                .orderByDesc("id");
        return consents.selectOne(lock ? query.last("LIMIT 1 FOR UPDATE") : query.last("LIMIT 1"));
    }

    public ConsentLog requireConsent(FamilyMember member) {
        return requireConsent(member, true);
    }

    /** 只读版：与 requireConsent 判定完全一致，但不锁同意记录（纯展示路径用）。 */
    public ConsentLog requireConsentReadOnly(FamilyMember member) {
        return requireConsent(member, false);
    }

    private ConsentLog requireConsent(FamilyMember member, boolean lock) {
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
        ConsentLog latest = latest(member, "PROFILE", grantorId, lock);
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
