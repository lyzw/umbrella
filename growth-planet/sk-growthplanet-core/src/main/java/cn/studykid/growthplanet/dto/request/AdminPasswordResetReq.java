package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 后台账号重置密码请求。 */
public class AdminPasswordResetReq {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需为 6-64 位")
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
