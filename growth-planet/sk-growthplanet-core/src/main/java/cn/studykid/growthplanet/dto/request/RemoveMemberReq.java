package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 移除家庭成员请求：移除本家庭内指定成员（不可移除 owner）。
 */
@Data
public class RemoveMemberReq {
    /** 待移除成员在 usr_family_member 中的主键 ID。 */
    @NotNull(message = "memberId 不能为空")
    private Long memberId;
}
