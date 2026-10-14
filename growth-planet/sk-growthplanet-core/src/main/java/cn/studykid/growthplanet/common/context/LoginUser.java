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

    /** 所属家庭 ID 列表（多家庭：可包含多个）。 */
    private List<Long> familyIds;

    /**
     * 当前激活家庭 ID。由 {@code JwtInterceptor} 从请求头 {@code X-Family-Id} 解析并校验后写入；
     * 未设置（无头）时回落 {@link #firstFamilyId()}，兼容单家庭旧调用。
     */
    private Long activeFamilyId;

    /** JWT 唯一标识；撤回授权由同意记录决定，不依赖此标识。 */
    private String jti;

    @Builder.Default
    private Long tokenVersion = 0L;

    /** 取第一个家庭 ID（无激活家庭时的回落值）。 */
    public Long firstFamilyId() {
        if (familyIds == null || familyIds.isEmpty()) {
            return null;
        }
        return familyIds.get(0);
    }

    /**
     * 取当前激活家庭 ID：优先用请求头指定的 {@link #activeFamilyId}，未指定则回落首个家庭。
     * 多家庭模型下，所有业务均通过此方法获取「当前正在操作的家庭」。
     */
    public Long activeFamilyId() {
        return activeFamilyId != null ? activeFamilyId : firstFamilyId();
    }
}
