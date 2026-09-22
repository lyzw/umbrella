package cn.studykid.growthplanet.common.enums;

import lombok.Getter;

/**
 * 后台运营角色（独立于小程序 C 端 RoleEnum）。代码与详细设计文档 §3.1 一致。
 */
@Getter
public enum AdminRole {

    SA("超级管理员", "系统最高权限：角色与权限维护、全局配置、所有模块读写、审计查看"),
    OP("运营管理员", "日常内容配置、业务数据查询与人工干预、看板查看"),
    CR("内容审核员", "UGC 机审+人审、敏感词/图像风控处置"),
    DC("数据运营/客服", "看板报表、家庭/孩子工单协助、通知统计"),
    CP("合规/隐私专员", "监护人同意核验、隐私工单闭环、合规清单（隔离）"),
    RA("只读审计员", "仅查看审计/日志/看板，无写权限");

    private final String label;
    private final String description;

    AdminRole(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public static AdminRole of(String code) {
        for (AdminRole role : values()) {
            if (role.name().equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知后台角色: " + code);
    }
}
