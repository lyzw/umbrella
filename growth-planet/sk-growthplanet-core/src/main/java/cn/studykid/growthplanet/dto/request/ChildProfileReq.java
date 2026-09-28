package cn.studykid.growthplanet.dto.request;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 儿童档案提交请求。
 */
@Data
public class ChildProfileReq {
    @NotNull @Positive
    private Long childId;
    @NotBlank @Size(max = 64)
    private String nickname;
    @NotBlank @Size(max = 32)
    private String grade;
    @NotBlank @Size(max = 128)
    private String school;
    @Min(value = 1950, message = "出生年不早于1950")
    @Max(value = 2100, message = "出生年非法")
    private Integer birthYear;
    @Min(value = 1, message = "出生月非法")
    @Max(value = 12, message = "出生月非法")
    private Integer birthMonth;
    @NotNull @Size(max = 20)
    private List<@NotBlank @Size(max = 64) String> allergies;
    @NotNull @Size(max = 20)
    private List<@NotBlank @Size(max = 64) String> dislikes;
    @NotNull @Size(max = 20)
    private List<@NotBlank @Size(max = 64) String> tastes;
}
