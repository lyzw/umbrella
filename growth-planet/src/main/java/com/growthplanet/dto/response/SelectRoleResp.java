package com.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 选择角色响应（重新签发 token）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectRoleResp {
    private String role;
    private String token;
    /** 下一步引导：child-profile / create-family。 */
    private String nextStep;
}
