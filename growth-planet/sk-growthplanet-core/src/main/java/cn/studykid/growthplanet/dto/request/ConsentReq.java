package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 提交监护人同意书请求。
 */
@Data
public class ConsentReq {
    @NotBlank(message = "version 不能为空")
    @Size(max = 16)
    private String version;

    /** 自报年龄。 */
    @NotNull @Min(18) @Max(120)
    private Integer selfReportedAge;

    /** 是否同意。 */
    @NotNull @AssertTrue
    private Boolean agreed;

    /** 必须显式指定申请关联的儿童 ID。 */
    @NotNull @Positive
    private Long childId;

    @NotNull @Positive
    private Long applyId;

    @NotBlank @Pattern(regexp = "PROFILE")
    private String consentType;
}
