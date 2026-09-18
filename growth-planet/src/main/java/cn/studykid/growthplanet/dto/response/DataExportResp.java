package cn.studykid.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导出申请受理/查询响应，不包含导出文件或儿童资料。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataExportResp {
    private String taskId;
    private String status;
    private Long dueAt;
    private String errorCode;
    private boolean downloadAvailable;
    private String requestType;
    private Long expiresAt;
    private Integer version;
}
