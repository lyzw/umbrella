package com.growthplanet.common.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录用户值对象：从 JWT 解析后注入 {@link UserContext}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    /** 用户 ID。 */
    private Long userId;

    /** 角色字符串：CHILD / PARENT / ADMIN / UNSET。 */
    private String role;

    /** 所属家庭 ID 列表（Sprint 1 单家庭，取第一个）。 */
    private List<Long> familyIds;

    /** JWT 唯一标识，用于黑名单（撤回降级）。 */
    private String jti;

    /** 取第一个家庭 ID（单家庭假设）。 */
    public Long firstFamilyId() {
        if (familyIds == null || familyIds.isEmpty()) {
            return null;
        }
        return familyIds.get(0);
    }
}
