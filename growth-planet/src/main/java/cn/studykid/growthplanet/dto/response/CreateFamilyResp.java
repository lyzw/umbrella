package cn.studykid.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建家庭响应（含重新签发的 token，写入 family_ids）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFamilyResp {
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private Long familyId;
    private String inviteCode;
    private Long expireAt;
    /** 创建成功后重新签发的 token（已写入 family_ids）。 */
    private String token;
}
