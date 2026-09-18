package com.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_notice")
public class Notice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventKey;
    private Long receiverId;
    private Long familyId;
    private Long childId;
    private String channel;
    private String status;
    private String eventType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private Long deleteAt = 0L;
}
