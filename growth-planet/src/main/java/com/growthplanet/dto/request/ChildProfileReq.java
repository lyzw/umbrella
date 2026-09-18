package com.growthplanet.dto.request;

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
    @NotNull @Size(max = 20)
    private List<@NotBlank @Size(max = 64) String> allergies;
    @NotNull @Size(max = 20)
    private List<@NotBlank @Size(max = 64) String> dislikes;
    @NotNull @Size(max = 20)
    private List<@NotBlank @Size(max = 64) String> tastes;
}
