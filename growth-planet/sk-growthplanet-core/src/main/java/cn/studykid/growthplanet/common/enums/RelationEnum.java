package cn.studykid.growthplanet.common.enums;

/**
 * 家庭成员关系标签：仅用于展示称呼，与权限角色（PARENT/CHILD）完全解耦，无任何权限差异。
 * 父母、祖辈（爷爷奶奶 / 外公外婆）、亲友均作为「家长」加入，仅此处标签不同。
 */
public enum RelationEnum {

    FATHER("父亲"),
    MOTHER("母亲"),
    PATERNAL_GF("爷爷"),
    PATERNAL_GM("奶奶"),
    MATERNAL_GF("外公"),
    MATERNAL_GM("外婆"),
    OTHER_GUARDIAN("亲友"),
    CHILD("孩子");

    private final String label;

    RelationEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 校验字符串是否合法枚举名（大小写不敏感、忽略首尾空格）。 */
    public static boolean isValid(String value) {
        return resolveLabel(value) != null;
    }

    /**
     * 将输入解析为标准中文标签：支持枚举名（如 PATERNAL_GF）或中文标签（如 爷爷）。
     * 输入为空 / 非法时返回 null。
     */
    public static String resolveLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        for (RelationEnum e : values()) {
            if (e.name().equalsIgnoreCase(v) || e.getLabel().equals(v)) {
                return e.getLabel();
            }
        }
        return null;
    }
}
