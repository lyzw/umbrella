package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 勋章人工补发请求（M4，详设 §3.4 勋章发放 edit 列 = SA,OP）。
 * <p>L4 敏感操作：前端二次确认 + 审计留痕（MEDAL_REISSUE）。不改余额、不发通知。</p>
 */
@Data
public class AdminMedalReissueReq {

    /** 补发对象孩子（C 端用户 id，role=CHILD）。 */
    @NotNull
    private Long childId;

    /** 勋章 code（life_medal_definition.code，须为 NORMAL）。 */
    @NotBlank
    private String code;

    /**
     * 幂等键：同一 (definition_id, child_id, ref_id) 仅发一次。缺省 0 = 人工补发通道
     * （每孩子每勋章仅补发一次）；指定 ref_id 时语义同 C 端业务实例 id。
     */
    private Long refId;

    /** 补发原因（必填，进审计明细）。 */
    @NotBlank
    private String reason;
}
