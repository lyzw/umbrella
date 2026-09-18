package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class DataDeleteReq {
    @NotNull @Positive
    private Long childId;
    @NotNull @AssertTrue
    private Boolean confirmed;
    @NotBlank @Size(max = 128)
    @lombok.ToString.Exclude
    private String code;
}
