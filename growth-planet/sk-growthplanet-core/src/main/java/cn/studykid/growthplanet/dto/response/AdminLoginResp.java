package cn.studykid.growthplanet.dto.response;

import java.util.Set;

public class AdminLoginResp {

    private String token;
    private Long adminId;
    private String username;
    private String name;
    private String roleCode;
    private String roleName;
    private Set<String> permissions;

    public static AdminLoginResp of(String token, Long adminId, String username, String name,
                                   String roleCode, String roleName, Set<String> permissions) {
        AdminLoginResp r = new AdminLoginResp();
        r.token = token;
        r.adminId = adminId;
        r.username = username;
        r.name = name;
        r.roleCode = roleCode;
        r.roleName = roleName;
        r.permissions = permissions;
        return r;
    }

    public String getToken() {
        return token;
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

    public Set<String> getPermissions() {
        return permissions;
    }
}
