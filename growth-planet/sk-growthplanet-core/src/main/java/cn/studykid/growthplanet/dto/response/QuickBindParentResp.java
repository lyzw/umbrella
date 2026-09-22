package cn.studykid.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开发/测试：儿童一键绑定家长响应（仅 dev/test 端点使用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickBindParentResp {
    /** 重新签发的儿童 token（已携带最新 family_ids）。 */
    private String token;
    private String role;
    private Long expiresIn;
    private Long familyId;
    private String bindStatus;
}
