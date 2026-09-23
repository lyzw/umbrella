package cn.studykid.growthplanet.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 申请客户端直传凭证：业务类型 + 原始文件名（仅用于判定媒体类型与后缀）。 */
@Data
public class AdminUploadTokenReq {

    /** 业务类型白名单：dish / medal / ugc / banner。 */
    @NotBlank
    @Size(max = 32)
    private String bizType;

    /** 原始文件名，用于推断后缀与媒体类型（如 cover.png、clip.mp4）。 */
    @NotBlank
    @Size(max = 255)
    private String fileName;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported upload-token field");
    }
}
