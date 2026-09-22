package cn.studykid.growthplanet.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ChildPreferencesReq {
    @NotNull @Size(max = 20)
    private List<@NotBlank @Size(max = 64) String> dislikes;
    @NotNull @Size(max = 20)
    private List<@NotBlank @Size(max = 64) String> tastes;

    // 限定此入口的字段白名单，避免把安全字段或目标身份的注入静默当作成功。
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported preference field");
    }
}
