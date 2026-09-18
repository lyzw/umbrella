package com.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信登录请求。
 */
@Data
public class WxLoginReq {
    @NotBlank(message = "code 不能为空")
    private String code;
}
