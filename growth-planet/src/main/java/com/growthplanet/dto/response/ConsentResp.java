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
    /** GET：当前状态（NONE/PENDING/APPROVED/REVOKED）。 */
    private String currentStatus;
    /** POST：提交后的监护人状态。 */
    private String guardianStatus;
}
