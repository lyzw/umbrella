package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 隐私工单驳回请求（M5，详设 §3.4 隐私工单 approve 列 = CP）。
 * <p>将工单由 RECEIVED/PROCESSING 置为 REJECTED，驳回原因写入 {@code error_code} 字段并落审计。</p>
 */
@Data
public class AdminPrivacyRejectReq {

    /** 驳回原因（必填，进审计与 error_code）。 */
    @NotBlank
    @Size(min = 1, max = 255)
    private String reason;
}
