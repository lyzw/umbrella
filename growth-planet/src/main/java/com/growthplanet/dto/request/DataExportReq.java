package com.growthplanet.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据导出请求。
 */
@Data
public class DataExportReq {
    @NotNull(message = "childId 不能为空")
    private Long childId;
}
