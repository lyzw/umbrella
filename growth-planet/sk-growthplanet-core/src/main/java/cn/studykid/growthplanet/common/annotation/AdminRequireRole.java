package cn.studykid.growthplanet.common.annotation;

import cn.studykid.growthplanet.common.enums.AdminRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 后台方法/控制器级角色白名单注解。由 {@code AdminJwtInterceptor} 读取，
 * 若当前后台管理员角色不在集合内则抛 E-009(403)。细粒度资源权限请用 {@code AdminUserContext.requirePerm}。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminRequireRole {
    AdminRole[] value();
}
