package com.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 加入家庭请求。
 */
@Data
public class JoinFamilyReq {
    @NotBlank(message = "inviteCode 不能为空")
    private String inviteCode;
}
