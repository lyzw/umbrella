package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 隐私核验记录运营视图行（M5，资源=核验记录，仅 CP/SA/RA 可见）。 */
@Data
@Builder
public class AdminVerificationResp {
    private Long id;
    /** 核验码哈希（脱敏：仅回显尾 8 位，完整值不落响应）。 */
    private String codeHashMasked;
    private Long requesterId;
    private Long requestId;
    private LocalDateTime verifiedAt;
}
