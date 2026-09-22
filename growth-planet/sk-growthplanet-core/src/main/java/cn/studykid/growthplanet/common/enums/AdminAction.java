package cn.studykid.growthplanet.common.enums;

import lombok.Getter;

/**
 * 后台细粒度权限操作枚举（资源 × 操作 授权点的 action 维度）。
 * 与详细设计文档 §3.4 矩阵列一致。
 */
@Getter
public enum AdminAction {

    VIEW("查看"),
    CREATE("新建"),
    EDIT("编辑"),
    DELETE("删除"),
    EXPORT("导出"),
    APPROVE("审批"),
    CONFIG("配置");

    private final String label;

    AdminAction(String label) {
        this.label = label;
    }

    public String code() {
        return name().toLowerCase();
    }
}
