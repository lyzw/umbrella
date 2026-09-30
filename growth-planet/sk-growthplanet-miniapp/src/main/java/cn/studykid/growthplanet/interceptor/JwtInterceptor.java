package cn.studykid.growthplanet.interceptor;

import cn.studykid.growthplanet.common.annotation.RequireRole;
import cn.studykid.growthplanet.common.context.LoginUser;
import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器：
 * <ol>
 *   <li>解析 Authorization: Bearer &lt;token&gt;，失败抛 E-001(401)</li>
 *   <li>校验数据库账号、角色及会话版本，依赖不可用时拒绝处理</li>
 *   <li>注入 {@link UserContext}</li>
 *   <li>校验 {@link RequireRole}，不符抛 E-009(403)</li>
 *   <li>afterCompletion 清除上下文</li>
 * </ol>
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    /** 多家庭：客户端在请求头携带当前激活家庭 ID。 */
    private static final String FAMILY_HEADER = "X-Family-Id";
    private final SessionService sessions;

    public JwtInterceptor(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "缺少或非法 Authorization 头");
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        LoginUser loginUser = sessions.authenticate(token);
        // 已完成认证后保留操作者，角色拒绝也可审计；最外层过滤器负责最终清理。
        UserContext.set(loginUser);
        UserContext.setToken(token);

        // 多家庭：解析 X-Family-Id 并校验其属于当前用户所属家庭，写入激活家庭。
        resolveActiveFamily(request, loginUser);

        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole != null) {
            boolean matched = false;
            for (RoleEnum role : requireRole.value()) {
                if (role.name().equals(loginUser.getRole())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                throw new BizException(ResultCode.E009_FORBIDDEN, "角色不符");
            }
        }
        return true;
    }

    /**
     * 解析并校验 {@code X-Family-Id}：必须 ∈ token 的 familyIds，跨家庭 100% 隔离。
     * 未携带该头时回落首个家庭（兼容单家庭旧调用）。
     */
    private void resolveActiveFamily(HttpServletRequest request, LoginUser loginUser) {
        String raw = request.getHeader(FAMILY_HEADER);
        if (raw == null || raw.isBlank()) {
            return;
        }
        Long familyId;
        try {
            familyId = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "X-Family-Id 格式非法");
        }
        if (loginUser.getFamilyIds() == null || !loginUser.getFamilyIds().contains(familyId)) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "X-Family-Id 不属于当前用户所属家庭");
        }
        loginUser.setActiveFamilyId(familyId);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

}
