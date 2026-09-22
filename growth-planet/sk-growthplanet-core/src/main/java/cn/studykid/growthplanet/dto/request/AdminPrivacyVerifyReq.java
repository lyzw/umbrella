package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 隐私工单 CP 核验请求（M5，详设 §3.4 隐私工单 approve 列 = CP）。
 * <p>CP 完成线下身份核验后，提交核验码；后端对其做 SHA-256 哈希后写入
 * {@code sys_privacy_verification.code_hash}，并将工单由 RECEIVED 推进至 PROCESSING。
 * 核验码本身不持久化明文（符合数据最小化）。</p>
 */
@Data
public class AdminPrivacyVerifyReq {

    /** 线下核验码（如人工核身凭证号），1–128 位可见字符。 */
    @NotBlank
    @Size(min = 1, max = 128)
    private String code;
}
