package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_privacy_request")
public class PrivacyRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requesterId;
    private Long childId;
    private Long familyId;
    private String requestType;
    private String idempotencyKey;
    private String requestHash;
    private String status;
    private Long dueAt;
    private Long verifiedAt;
    private String resultRef;
    private Long expiresAt;
    private String errorCode;
    private Long operatorId;
    private String evidenceRef;
    private Integer version = 0;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
