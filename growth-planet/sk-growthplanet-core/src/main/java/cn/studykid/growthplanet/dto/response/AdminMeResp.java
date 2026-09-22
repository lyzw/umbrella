package cn.studykid.growthplanet.dto.response;

import java.util.Set;

/**
 * 当前登录后台管理员信息（GET /api/console/auth/me）。
 * 不重复签发 token，仅回显身份与权限点，供前端做按钮级鉴权与菜单渲染。
 */
public class AdminMeResp {

    private Long adminId;
    private String username;
    private String name;
    private String roleCode;
    private String roleName;
    private boolean superAdmin;
    private Set<String> permissions;

    public static AdminMeResp of(Long adminId, String username, String name,
                                 String roleCode, String roleName, boolean superAdmin,
                                 Set<String> permissions) {
        AdminMeResp r = new AdminMeResp();
        r.adminId = adminId;
        r.username = username;
        r.name = name;
        r.roleCode = roleCode;
        r.roleName = roleName;
        r.superAdmin = superAdmin;
        r.permissions = permissions;
        return r;
    }

    public Long getAdminId() {
        return adminId;
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public boolean isSuperAdmin() {
        return superAdmin;
    }

    public Set<String> getPermissions() {
        return permissions;
    }
}
