package cn.studykid.growthplanet.dto.response;

public class AdminRoleResp {

    private Long id;
    private String code;
    private String name;
    private String remark;
    private long permCount;

    public static AdminRoleResp of(cn.studykid.growthplanet.entity.SysRole role, long permCount) {
        AdminRoleResp r = new AdminRoleResp();
        r.id = role.getId();
        r.code = role.getCode();
        r.name = role.getName();
        r.remark = role.getRemark();
        r.permCount = permCount;
        return r;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getRemark() {
        return remark;
    }

    public long getPermCount() {
        return permCount;
    }
}
