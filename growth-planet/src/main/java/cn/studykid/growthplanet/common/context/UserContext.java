package cn.studykid.growthplanet.common.context;

import cn.studykid.growthplanet.common.enums.RoleEnum;

import java.util.List;

/**
 * 登录用户上下文（ThreadLocal）。由 {@code JwtInterceptor#preHandle} 注入，
 * 由 {@code #afterCompletion} 清除，避免线程复用串号。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TOKEN = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser loginUser) {
        CURRENT.set(loginUser);
    }

    public static LoginUser get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
        CURRENT_TOKEN.remove();
    }

    /** 设置当前请求原始 token；仅在请求生命周期内保存，不写日志。 */
    public static void setToken(String token) {
        CURRENT_TOKEN.set(token);
    }

    public static String currentToken() {
        return CURRENT_TOKEN.get();
    }

    public static Long userId() {
        LoginUser u = get();
        return u == null ? null : u.getUserId();
    }

    public static String role() {
        LoginUser u = get();
        return u == null ? null : u.getRole();
    }

    public static List<Long> familyIds() {
        LoginUser u = get();
        return u == null ? null : u.getFamilyIds();
    }

    /** 当前家庭 ID（单家庭取第一个）。 */
    public static Long familyId() {
        LoginUser u = get();
        return u == null ? null : u.firstFamilyId();
    }

    public static String jti() {
        LoginUser u = get();
        return u == null ? null : u.getJti();
    }

    public static boolean hasRole(RoleEnum role) {
        return role != null && role.name().equals(role());
    }
}
