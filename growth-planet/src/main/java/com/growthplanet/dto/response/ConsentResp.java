package com.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同意书查询/提交响应（合并 GET 与 POST 字段，按需填充）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentResp {
    /** GET：协议正文。 */
    private String agreementText;
    /** 协议版本。 */
    private String version;
    /** 当前同意状态：NONE/GRANTED/REVOKED/EXPIRED/VERSION_CHANGED。 */
    private String currentStatus;
    /** 声明/核验状态，与同意是否有效分离。 */
    private String guardianStatus;
}
