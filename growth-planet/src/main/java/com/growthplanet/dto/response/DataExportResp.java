package com.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据导出响应（内联返回 JSON 字符串）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataExportResp {
    private String taskId;
    private String status;
    /** 导出的数据（JSON 字符串）。 */
    private String data;
}
