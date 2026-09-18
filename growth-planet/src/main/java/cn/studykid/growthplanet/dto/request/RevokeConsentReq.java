package cn.studykid.growthplanet.dto.request;

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

    @NotBlank
    @jakarta.validation.constraints.Size(max = 16)
    private String version;

    @NotNull(message = "childId 不能为空")
    @jakarta.validation.constraints.Positive
    private Long childId;
}
