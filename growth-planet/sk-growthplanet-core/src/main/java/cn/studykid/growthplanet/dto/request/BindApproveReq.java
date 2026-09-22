package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 绑定审批请求（家长审核儿童加入申请）。
 */
@Data
public class BindApproveReq {
    @NotNull(message = "applyId 不能为空")
    @jakarta.validation.constraints.Positive
    private Long applyId;

    @jakarta.validation.constraints.Size(max = 32)
    private String relationLabel;

    /** true=通过，false=拒绝。 */
    @NotNull
    private Boolean approve;
}
