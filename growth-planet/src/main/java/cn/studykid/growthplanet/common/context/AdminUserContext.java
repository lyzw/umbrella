package cn.studykid.growthplanet.common.context;

import cn.studykid.growthplanet.common.enums.AdminRole;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;

import java.util.Set;

/**
 * 后台管理员上下文（ThreadLocal）。由 {@code AdminJwtInterceptor#preHandle} 注入，
 * 由 {@code #afterCompletion} 清除，避免线程复用串号。
 */
public final class AdminUserContext {

    private static final ThreadLocal<LoginAdmin> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> PERMS = new ThreadLocal<>();

    private AdminUserContext() {
    }

    public static void set(LoginAdmin admin, Set<String> permissions) {
        CURRENT.set(admin);
        PERMS.set(permissions);
    }

    public static LoginAdmin get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
        PERMS.remove();
    }

    public static Long adminId() {
        LoginAdmin a = get();
        return a == null ? null : a.getAdminId();
    }

    public static String roleCode() {
        LoginAdmin a = get();
        return a == null ? null : a.getRoleCode();
    }

    public static boolean isSuperAdmin() {
        LoginAdmin a = get();
        return a != null && a.isSuperAdmin();
    }

    public static AdminRole role() {
        LoginAdmin a = get();
        return a == null ? null : a.role();
    }

    /** 当前登录管理员所持权限点集合（只读，可能为空）。 */
    public static Set<String> permissions() {
        Set<String> perms = PERMS.get();
        return perms == null ? Set.of() : perms;
    }

    /** 是否持有某资源的某操作权限（权限点格式 resource:action）。 */
    public static boolean hasPerm(String resource, String action) {
        Set<String> perms = PERMS.get();
        return perms != null && perms.contains(resource + ":" + action);
    }

    /** 校验权限，不通过抛 E-009(403)。SA 默认拥有全部权限。 */
    public static void requirePerm(String resource, String action) {
        if (isSuperAdmin()) {
            return;
        }
        if (!hasPerm(resource, action)) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "无权限：" + resource + "/" + action);
        }
    }
}
