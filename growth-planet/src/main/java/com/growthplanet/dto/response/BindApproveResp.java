package com.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 绑定审批响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BindApproveResp {
    private String bindStatus;
}
