package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建家庭请求。
 */
@Data
public class CreateFamilyReq {
    @NotBlank(message = "familyName 不能为空")
    @jakarta.validation.constraints.Size(max = 64)
    private String familyName;
}
