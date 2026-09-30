package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 加入家庭请求。角色由邀请码类型（家长/学生）锁定，加入者无法自行改变。
 */
@Data
public class JoinFamilyReq {
    @NotBlank(message = "inviteCode 不能为空")
    @jakarta.validation.constraints.Pattern(regexp = "[A-Z0-9]{6}")
    private String inviteCode;

    /** 关系标签（可选）：父亲/母亲/爷爷/奶奶/外公/外婆/亲友/孩子。不传则按邀请码类型取默认值。 */
    @jakarta.validation.constraints.Size(max = 32)
    private String relationLabel;
}
