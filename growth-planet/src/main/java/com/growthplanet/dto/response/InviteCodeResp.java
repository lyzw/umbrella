package com.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邀请码响应。qrBase64 本期返回 null（前端自行渲染）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteCodeResp {
    private String inviteCode;
    private Long expireAt;
    private String qrBase64;
}
