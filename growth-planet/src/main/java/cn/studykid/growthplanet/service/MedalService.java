package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.response.ChildMedalResp;
import cn.studykid.growthplanet.dto.response.MedalDefinitionResp;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.entity.MedalAward;
import cn.studykid.growthplanet.entity.MedalDefinition;
import cn.studykid.growthplanet.mapper.MedalAwardMapper;
import cn.studykid.growthplanet.mapper.MedalDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MedalService {
    private final MedalDefinitionMapper definitions;
    private final MedalAwardMapper awards;
    private final ChildAuthorizationService authorization;
    private final AuditService audit;
    private final BusinessTime time;

    public MedalService(MedalDefinitionMapper definitions, MedalAwardMapper awards,
            ChildAuthorizationService authorization, AuditService audit, BusinessTime time) {
        this.definitions = definitions;
        this.awards = awards;
        this.authorization = authorization;
        this.audit = audit;
        this.time = time;
    }

    /** 勋章目录（全局，儿童与家长均可查看）。 */
    public List<MedalDefinitionResp> listDefinitions() {
        return definitions.selectList(new QueryWrapper<MedalDefinition>()
                .eq("status", "NORMAL").orderByAsc("sort_order")).stream()
                .map(this::toDefinition).toList();
    }

    /** 某儿童的勋章墙：含是否已获得、发放时间与进度（COUNT 按获得次数，STREAK 按最长连续）。 */
    public List<ChildMedalResp> listAwards(Long childId) {
        FamilyMember member = authorization.lockBoundChild(childId);
        authorization.requireConsent(member);
        List<MedalDefinition> defs = definitions.selectList(new QueryWrapper<MedalDefinition>()
                .eq("status", "NORMAL").orderByAsc("sort_order"));
        List<MedalAward> earned = awards.selectList(new QueryWrapper<MedalAward>()
                .eq("child_id", childId).eq("delete_at", 0L));
        return defs.stream().map(def -> {
            MedalAward award = earned.stream()
                    .filter(a -> a.getDefinitionId().equals(def.getId())).findFirst().orElse(null);
            int progress = switch (def.getConditionType()) {
                case "COUNT" -> (int) earned.stream()
                        .filter(a -> a.getDefinitionId().equals(def.getId())).count();
                case "STREAK" -> earned.stream()
                        .filter(a -> a.getDefinitionId().equals(def.getId()))
                        .mapToInt(MedalAward::getConsecutiveCount).max().orElse(0);
                default -> award == null ? 0 : 1;
            };
            return new ChildMedalResp(toDefinition(def), award != null,
                    award == null ? null : String.valueOf(award.getAwardedAt()),
                    award == null ? 0 : award.getConsecutiveCount(), progress, def.getThreshold());
        }).toList();
    }

    /**
     * 发放勋章（幂等）。同一 (definition_id, child_id, ref_id) 仅发放一次：
     * EVENT 类 ref_id 用业务实例 id；COUNT/STREAK 类用阈值本身，保证每人每阈值仅一次。
     * 必须在已持有相关行锁的事务内调用（MANDATORY）。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MedalAward award(Long childId, Long familyId, String code, Long refId, int consecutiveCount) {
        MedalDefinition def = definitions.selectOne(new QueryWrapper<MedalDefinition>()
                .eq("code", code).eq("status", "NORMAL").last("FOR UPDATE"));
        if (def == null) {
            return null;
        }
        MedalAward existing = awards.selectOne(new QueryWrapper<MedalAward>()
                .eq("definition_id", def.getId()).eq("child_id", childId).eq("ref_id", refId)
                .eq("delete_at", 0L).last("FOR UPDATE"));
        if (existing != null) {
            return existing;
        }
        MedalAward award = new MedalAward();
        award.setDefinitionId(def.getId());
        award.setFamilyId(familyId);
        award.setChildId(childId);
        award.setAwardedAt(time.now().atZone(BusinessTime.ZONE).toInstant().toEpochMilli());
        award.setConsecutiveCount(consecutiveCount);
        award.setRefId(refId);
        awards.insert(award);
        audit.record("MEDAL_AWARD", UserContext.userId(), familyId, "MEDAL", award.getId(),
                null, "code=" + code + ";childId=" + childId + ";refId=" + refId
                        + ";consecutive=" + consecutiveCount);
        return award;
    }

    private MedalDefinitionResp toDefinition(MedalDefinition def) {
        return new MedalDefinitionResp(def.getId().toString(), def.getCode(), def.getName(),
                def.getDescription(), def.getIcon(), def.getCategory(), def.getConditionType(),
                def.getThreshold(), def.getSortOrder());
    }
}
