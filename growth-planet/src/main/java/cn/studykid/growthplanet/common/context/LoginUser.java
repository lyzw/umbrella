package cn.studykid.growthplanet.common.context;

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

    /** 角色字符串：CHILD / PARENT / ADMIN / UNSELECTED。 */
    private String role;

    /** 所属家庭 ID 列表（Sprint 1 单家庭，取第一个）。 */
    private List<Long> familyIds;

    /** JWT 唯一标识；撤回授权由同意记录决定，不依赖此标识。 */
    private String jti;

    @Builder.Default
    private Long tokenVersion = 0L;

    public LoginUser(Long userId, String role, List<Long> familyIds, String jti) {
        this(userId, role, familyIds, jti, 0L);
    }

    /** 取第一个家庭 ID（单家庭假设）。 */
    public Long firstFamilyId() {
        if (familyIds == null || familyIds.isEmpty()) {
            return null;
        }
        return familyIds.get(0);
    }
}
