package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 儿童档案表 usr_child_profile（allergies/dislikes/tastes 为 JSON 列，映射 List&lt;String&gt;）。
 */
@Data
@TableName(value = "usr_child_profile", autoResultMap = true)
public class ChildProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联儿童账户（唯一）。 */
    private Long userId;

    private Long familyId;

    private String nickname;

    private String grade;

    private String school;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> allergies;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> dislikes;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tastes;

    /** 档案状态：INCOMPLETE/COMPLETE。 */
    private String profileStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Long deleteAt = 0L;
}
