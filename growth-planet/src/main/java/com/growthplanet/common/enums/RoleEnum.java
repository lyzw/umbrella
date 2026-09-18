package com.growthplanet.common.enums;

/**
 * 角色枚举。UNSET 为选择角色前的过渡态，带 UNSET 的 token 访问需角色的接口将被拦截器拒为 403。
 */
public enum RoleEnum {
    CHILD,
    PARENT,
    ADMIN,
    UNSET
}
