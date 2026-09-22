package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 超额确认单人工复核请求（M4，详设 §3.4 确认单 approve 列）。
 * <p>里程碑 A 按 D3 两级简化：复核结论仅落 sys_audit_log（L4 前端二次确认 + 审计留痕），
 * <b>不写</b> {@code life_confirm_approval}（该表 uk_approval_confirm 唯一键由 C 端家长审批独占，
 * 管理侧写入会导致家长后续 approve 撞唯一键 500）、不改确认单状态、不动钱包余额。</p>
 */
@Data
public class AdminConfirmReviewReq {

    /** 复核结论：RESOLVED（关注无风险/已跟进闭环）| FOLLOW_UP（需继续跟进）。 */
    @NotBlank
    private String decision;

    /** 复核说明（必填，进审计明细）。 */
    @NotBlank
    private String note;
}
