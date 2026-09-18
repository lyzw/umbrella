package com.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提交监护人同意书请求。
 */
@Data
public class ConsentReq {
    @NotBlank(message = "version 不能为空")
    private String version;

    /** 自报年龄。 */
    private Integer selfReportedAge;

    /** 是否同意。 */
    private boolean agreed;

    /** 关联儿童 ID（可选，缺省时取家庭内首个儿童）。 */
    private Long childId;
}
