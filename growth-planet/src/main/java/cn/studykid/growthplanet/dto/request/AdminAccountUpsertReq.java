package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AdminAccountUpsertReq {

    @NotBlank(message = "登录账号不能为空")
    private String username;

    @NotBlank(message = "姓名不能为空")
    private String name;

    /** 新建必填；编辑时为空表示不修改密码。 */
    private String password;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "SA|OP|CR|DC|CP|RA", message = "角色代码非法")
    private String roleCode;

    /** 新建默认 ACTIVE；编辑时可传 ACTIVE/DISABLED。 */
    private String status;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
