package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 选择角色请求（UNSELECTED -> CHILD / PARENT）。
 */
@Data
public class SelectRoleReq {
    @NotBlank(message = "role 不能为空")
    private String role;
}
