package com.growthplanet.interceptor;

import com.growthplanet.common.annotation.RequireRole;
import com.growthplanet.common.context.LoginUser;
import com.growthplanet.common.context.UserContext;
import com.growthplanet.common.enums.RoleEnum;
import com.growthplanet.common.exception.BizException;
import com.growthplanet.common.result.ResultCode;
import com.growthplanet.service.SessionService;
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

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

}
