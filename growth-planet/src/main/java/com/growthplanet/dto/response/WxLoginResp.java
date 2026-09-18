package com.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信登录响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WxLoginResp {
    private String token;
    private String openid;
    private String role;
    private boolean isNew;
}
