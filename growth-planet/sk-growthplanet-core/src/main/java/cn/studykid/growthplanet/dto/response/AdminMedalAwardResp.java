package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 勋章发放运营视图行（M4，life_medal_award 只读 + 人工补发写入口）。孩子姓名默认脱敏。 */
@Data
@Builder
public class AdminMedalAwardResp {
    private Long id;
    private Long definitionId;
    private String medalCode;
    private String medalName;
    private Long familyId;
    private Long childId;
    private String childName;
    /** 发放时间（epoch 毫秒，与 C 端勋章墙口径一致）。 */
    private Long awardedAt;
    private Integer consecutiveCount;
    /** 幂等键：业务实例 id；0 = 人工补发通道。 */
    private Long refId;
    private LocalDateTime createTime;
}
