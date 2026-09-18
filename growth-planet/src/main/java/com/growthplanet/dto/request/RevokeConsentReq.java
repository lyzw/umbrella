package com.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 撤回同意书请求。
 */
@Data
public class RevokeConsentReq {
    @NotBlank(message = "consentType 不能为空")
    private String consentType;

    @NotNull(message = "childId 不能为空")
    private Long childId;
}
