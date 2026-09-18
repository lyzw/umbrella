package com.growthplanet.common.annotation;

import com.growthplanet.common.enums.RoleEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级角色校验注解。被 {@code JwtInterceptor} 读取，若登录用户角色不在集合内则抛 E-009(403)。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    RoleEnum[] value();
}
