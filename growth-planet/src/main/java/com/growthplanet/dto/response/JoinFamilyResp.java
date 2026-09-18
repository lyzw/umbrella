package com.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 加入家庭响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinFamilyResp {
    private Long applyId;
    private String status;
}
