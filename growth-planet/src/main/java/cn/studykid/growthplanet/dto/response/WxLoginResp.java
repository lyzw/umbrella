package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信登录响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WxLoginResp {
    private String token;
    private String openid;
    private String role;
    @JsonProperty("isNew")
    private boolean isNew;
    private long expiresIn;

    @JsonProperty("isNew")
    public boolean isNew() {
        return isNew;
    }
}
