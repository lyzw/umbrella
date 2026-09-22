package cn.studykid.growthplanet.interceptor;

import cn.studykid.growthplanet.common.annotation.AdminRequireRole;
import cn.studykid.growthplanet.common.context.AdminUserContext;
import cn.studykid.growthplanet.common.context.LoginAdmin;
import cn.studykid.growthplanet.common.enums.AdminRole;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.entity.SysAdminUser;
import cn.studykid.growthplanet.entity.SysRole;
import cn.studykid.growthplanet.entity.SysRolePermission;
import cn.studykid.growthplanet.mapper.AdminUserMapper;
import cn.studykid.growthplanet.mapper.RoleMapper;
import cn.studykid.growthplanet.mapper.RolePermissionMapper;
import cn.studykid.growthplanet.util.AdminJwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台运营端 JWT 鉴权拦截器（仅作用于 {@code /api/console/**}）：
 * <ol>
 *   <li>校验后台独立 token（独立密钥/issuer），失败抛 E-001(401)</li>
 *   <li>校验账号状态 ACTIVE 与 token_version（改密/禁用后旧 token 失效）</li>
 *   <li>加载该角色的细粒度权限点集合，注入 {@link AdminUserContext}</li>
 *   <li>校验 {@link AdminRequireRole} 角色白名单，不符抛 E-009(403)</li>
 *   <li>afterCompletion 清除上下文</li>
 * </ol>
 */
@Component
public class AdminJwtInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminJwtUtil jwt;
    private final AdminUserMapper adminUsers;
    private final RoleMapper roles;
    private final RolePermissionMapper permissions;

    public AdminJwtInterceptor(AdminJwtUtil jwt, AdminUserMapper adminUsers,
                              RoleMapper roles, RolePermissionMapper permissions) {
        this.jwt = jwt;
        this.adminUsers = adminUsers;
        this.roles = roles;
        this.permissions = permissions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "缺少后台 Authorization 头");
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        Claims claims = jwt.parse(token);
        Long adminId = jwt.getAdminId(claims);
        SysAdminUser user = adminUsers.selectById(adminId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "后台账号不可用");
        }
        Long tokenVersion = jwt.getTokenVersion(claims);
        if (!Objects.equals(user.getTokenVersion(), tokenVersion)) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "后台登录态已失效，请重新登录");
        }

        SysRole role = roles.selectById(user.getRoleId());
        if (role == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "角色缺失");
        }

        Set<String> perms = permissions.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysRolePermission>()
                                .eq("role_id", role.getId()).eq("delete_at", 0))
                .stream()
                .map(p -> p.getResource() + ":" + p.getAction())
                .collect(Collectors.toSet());

        LoginAdmin admin = LoginAdmin.builder()
                .adminId(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .roleCode(role.getCode())
                .roleId(role.getId())
                .tokenVersion(user.getTokenVersion())
                .build();
        AdminUserContext.set(admin, perms);

        AdminRequireRole requireRole = handlerMethod.getMethodAnnotation(AdminRequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(AdminRequireRole.class);
        }
        if (requireRole != null) {
            boolean matched = false;
            for (AdminRole r : requireRole.value()) {
                if (r.name().equals(role.getCode())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                throw new BizException(ResultCode.E009_FORBIDDEN, "后台角色不符");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) {
        AdminUserContext.clear();
    }
}
