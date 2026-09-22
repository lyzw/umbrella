package cn.studykid.growthplanet.entity;

import cn.studykid.growthplanet.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色×资源×操作授权点 sys_role_permission。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_permission")
public class SysRolePermission extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;
    private String resource;  // 见 AdminResource 常量
    private String action;    // view/create/edit/delete/export/approve/config
}
