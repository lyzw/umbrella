package com.growthplanet.interceptor;

import com.growthplanet.common.annotation.RequireRole;
import com.growthplanet.common.context.LoginUser;
import com.growthplanet.common.context.UserContext;
import com.growthplanet.common.enums.RoleEnum;
import com.growthplanet.common.exception.BizException;
import com.growthplanet.common.result.ResultCode;
import com.growthplanet.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器：
 * <ol>
 *   <li>解析 Authorization: Bearer &lt;token&gt;，失败抛 E-001(401)</li>
 *   <li>查 Redis 黑名单，命中抛 E-001（撤回即时降级；Redis 不可用时 fail-open）</li>
 *   <li>注入 {@link UserContext}</li>
 *   <li>校验 {@link RequireRole}，不符抛 E-009(403)</li>
 *   <li>afterCompletion 清除上下文</li>
 * </ol>
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    public JwtInterceptor(JwtUtil jwtUtil, RedisTemplate<String, String> redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
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

        Claims claims = jwtUtil.parse(token);

        String jti = jwtUtil.getJti(claims);
        if (jti != null && isBlacklisted(jti)) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "token 已被撤回");
        }

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(jwtUtil.getUserId(claims));
        loginUser.setRole(jwtUtil.getRole(claims));
        loginUser.setFamilyIds(jwtUtil.getFamilyIds(claims));
        loginUser.setJti(jti);
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

    private boolean isBlacklisted(String jti) {
        try {
            Boolean has = redisTemplate.hasKey(BLACKLIST_PREFIX + jti);
            return Boolean.TRUE.equals(has);
        } catch (Exception e) {
            // fail-open：Redis 不可用时不阻断链路
            return false;
        }
    }
}
