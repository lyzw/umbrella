package cn.studykid.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邀请码响应（生成 / 列表 / 撤销通用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyInviteResp {

    private Long id;
    private String inviteCode;
    private String inviteType;
    private String status;
    private Long expireAt;
    private Integer maxUse;
    private Integer usedCount;
    private Long creatorUserId;
}
