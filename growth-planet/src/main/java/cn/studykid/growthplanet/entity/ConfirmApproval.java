package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import cn.studykid.growthplanet.dto.request.OrderLineReq;
import lombok.Data;

@Data
@TableName(value = "life_confirm_approval", autoResultMap = true)
public class ConfirmApproval {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long confirmId;
    private Long parentId;
    private String action;
    private String beforeStatus;
    private String afterStatus;
    private Boolean isOverLimit;
    private String reason;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<OrderLineReq> suggestedItems;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private Long deleteAt = 0L;
}
