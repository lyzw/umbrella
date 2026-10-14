package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 转让 owner 请求：仅当前 owner 可发起，目标须为本家庭内已绑定的 PARENT。
 */
@Data
public class TransferOwnerReq {
    /** 受让方用户 ID（须为本家庭内 BOUND 的 PARENT）。 */
    @NotNull(message = "targetUserId 不能为空")
    private Long targetUserId;
}
