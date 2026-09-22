package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 开发/测试：儿童一键绑定家长请求（仅 dev/test 端点使用）。
 */
@Data
public class QuickBindParentReq {
    @NotBlank(message = "parentAccount 不能为空")
    @Size(max = 51, message = "账号长度须为1至51")
    @Pattern(regexp = "[a-zA-Z0-9_-]{1,51}", message = "账号仅支持字母、数字、下划线或短横线")
    private String parentAccount;
}
