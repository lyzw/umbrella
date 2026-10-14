package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 撤销邀请码请求。
 */
@Data
public class RevokeInviteCodeReq {

    @NotNull(message = "inviteId 不能为空")
    @Positive
    private Long inviteId;
}
