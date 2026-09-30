package cn.studykid.growthplanet.common.enums;

/**
 * 邀请码类型：生成时即锁定加入者的权限角色，加入者无法自行改变角色，杜绝越权成为家长。
 */
public enum InviteType {

    PARENT("家长邀请码"),
    CHILD("学生邀请码");

    private final String label;

    InviteType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 校验字符串是否合法枚举名（大小写不敏感、忽略首尾空格）。 */
    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim();
        for (InviteType e : values()) {
            if (e.name().equalsIgnoreCase(v)) {
                return true;
            }
        }
        return false;
    }
}
