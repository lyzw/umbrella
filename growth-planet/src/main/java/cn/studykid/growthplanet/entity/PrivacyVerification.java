package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_privacy_verification")
public class PrivacyVerification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String codeHash;
    private Long requesterId;
    private Long requestId;
    private Long verifiedAt;
}
