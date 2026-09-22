package cn.studykid.growthplanet.dto.response;

import java.time.LocalDateTime;

public class AdminAccountResp {

    private Long id;
    private String username;
    private String name;
    private String roleCode;
    private String roleName;
    private String status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;

    public static AdminAccountResp from(cn.studykid.growthplanet.entity.SysAdminUser u,
                                        cn.studykid.growthplanet.entity.SysRole role) {
        AdminAccountResp r = new AdminAccountResp();
        r.id = u.getId();
        r.username = u.getUsername();
        r.name = u.getName();
        r.roleCode = role == null ? null : role.getCode();
        r.roleName = role == null ? null : role.getName();
        r.status = u.getStatus();
        r.lastLoginTime = u.getLastLoginTime();
        r.createTime = u.getCreateTime();
        return r;
    }

    public Long getId() {
        return id;
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

    public String getStatus() {
        return status;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }
}
