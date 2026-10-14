package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 生成邀请码请求。邀请码类型在生成时即锁定加入角色。
 */
@Data
public class GenerateInviteCodeReq {

    /** 邀请码类型：PARENT（家长邀请码，免审）/ CHILD（学生邀请码，需审批）。 */
    @NotBlank(message = "inviteType 不能为空")
    private String inviteType;

    /** 有效期（小时），默认 7*24。 */
    private Long expireHours;

    /** 最大使用次数，0 = 不限制，默认 0。 */
    private Integer maxUse;
}
