package com.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 加入家庭请求。
 */
@Data
public class JoinFamilyReq {
    @NotBlank(message = "inviteCode 不能为空")
    @jakarta.validation.constraints.Pattern(regexp = "[A-Z0-9]{6}")
    private String inviteCode;
}
