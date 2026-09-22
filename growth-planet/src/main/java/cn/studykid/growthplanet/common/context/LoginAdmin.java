package cn.studykid.growthplanet.common.context;

import cn.studykid.growthplanet.common.enums.AdminRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 后台登录管理员值对象：由 {@code AdminJwtInterceptor} 从 admin token 解析后注入 {@link AdminUserContext}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAdmin {

    /** sys_admin_user 主键。 */
    private Long adminId;

    /** 登录账号。 */
    private String username;

    /** 姓名。 */
    private String name;

    /** 角色代码（SA/OP/CR/DC/CP/RA）。 */
    private String roleCode;

    /** 角色 ID。 */
    private Long roleId;

    /** 账号状态版本，用于单点失效（修改密码/禁用后旧 token 失效）。 */
    private Long tokenVersion;

    public AdminRole role() {
        return roleCode == null ? null : AdminRole.of(roleCode);
    }

    /** 是否为超级管理员。 */
    public boolean isSuperAdmin() {
        return AdminRole.SA.name().equals(roleCode);
    }

    /** 兼容占位（后台无家庭维度，保留接口一致性）。 */
    public List<Long> familyIds() {
        return List.of();
    }
}
