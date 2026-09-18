package cn.studykid.growthplanet.common.enums;

/**
 * 角色枚举。UNSELECTED 为选择角色前的过渡态，不能访问需要儿童或家长角色的接口。
 */
public enum RoleEnum {
    CHILD,
    PARENT,
    ADMIN,
    UNSELECTED
}
