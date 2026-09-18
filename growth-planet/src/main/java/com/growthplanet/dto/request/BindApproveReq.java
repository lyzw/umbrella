package com.growthplanet.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 绑定审批请求（家长审核儿童加入申请）。
 */
@Data
public class BindApproveReq {
    @NotNull(message = "applyId 不能为空")
    private Long applyId;

    private String relationLabel;

    /** true=通过，false=拒绝。 */
    private boolean approve;
}
