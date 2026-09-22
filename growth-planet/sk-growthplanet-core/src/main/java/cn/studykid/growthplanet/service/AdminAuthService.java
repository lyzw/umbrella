package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.context.AdminUserContext;
import cn.studykid.growthplanet.common.context.LoginAdmin;
import cn.studykid.growthplanet.common.context.RequestContext;
import cn.studykid.growthplanet.common.enums.AdminRole;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.AdminLoginReq;
import cn.studykid.growthplanet.dto.response.AdminLoginResp;
import cn.studykid.growthplanet.dto.response.AdminMeResp;
import cn.studykid.growthplanet.entity.SysAdminLoginLog;
import cn.studykid.growthplanet.entity.SysAdminUser;
import cn.studykid.growthplanet.entity.SysRole;
import cn.studykid.growthplanet.entity.SysRolePermission;
import cn.studykid.growthplanet.mapper.AdminLoginLogMapper;
import cn.studykid.growthplanet.mapper.AdminUserMapper;
import cn.studykid.growthplanet.mapper.RoleMapper;
import cn.studykid.growthplanet.mapper.RolePermissionMapper;
import cn.studykid.growthplanet.util.AdminJwtUtil;
import cn.studykid.growthplanet.util.AdminPasswordEncoder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台运营端认证服务：账号密码登录（PBKDF2 校验）→ 签发独立 admin token，
 * 并落 sys_admin_login_log（成功/失败）与 sys_audit_log；登出以递增 token_version 单点失效。
 *
 * <p>与 C 端 {@code AuthService} 完全隔离，不共享会话/账号/密钥。</p>
 */
@Service
public class AdminAuthService {

    /** 审计动作：后台登录。 */
    public static final String ACTION_ADMIN_LOGIN = "ADMIN_LOGIN";
    /** 审计动作：后台登出。 */
    public static final String ACTION_ADMIN_LOGOUT = "ADMIN_LOGOUT";

    private final AdminUserMapper adminUsers;
    private final RoleMapper roles;
    private final RolePermissionMapper rolePermissions;
    private final AdminLoginLogMapper loginLogs;
    private final AdminJwtUtil jwt;
    private final AdminPasswordEncoder passwordEncoder;
    private final AuditService audit;

    public AdminAuthService(AdminUserMapper adminUsers, RoleMapper roles,
                            RolePermissionMapper rolePermissions, AdminLoginLogMapper loginLogs,
                            AdminJwtUtil jwt, AdminPasswordEncoder passwordEncoder,
                            AuditService audit) {
        this.adminUsers = adminUsers;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.loginLogs = loginLogs;
        this.jwt = jwt;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    /** 账号密码登录。 */
    public AdminLoginResp login(AdminLoginReq req) {
        String ip = RequestContext.ip();
        SysAdminUser user = adminUsers.selectOne(new LambdaQueryWrapper<SysAdminUser>()
                .eq(SysAdminUser::getUsername, req.getUsername()));

        if (user == null) {
            recordLoginLog(null, req.getUsername(), ip, "FAILURE", "账号不存在");
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "账号或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            recordLoginLog(user.getId(), user.getUsername(), ip, "FAILURE", "账号已禁用");
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "账号已禁用");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getSalt(), user.getPassword())) {
            recordLoginLog(user.getId(), user.getUsername(), ip, "FAILURE", "密码错误");
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "账号或密码错误");
        }

        SysRole role = roles.selectById(user.getRoleId());
        if (role == null) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "角色缺失");
        }

        long tokenVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        LoginAdmin admin = LoginAdmin.builder()
                .adminId(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .roleCode(role.getCode())
                .roleId(role.getId())
                .tokenVersion(tokenVersion)
                .build();
        String token = jwt.generateToken(admin);

        Set<String> perms = loadPermissions(role.getId());

        // 更新最后登录信息（不触碰 token_version）
        SysAdminUser touch = new SysAdminUser();
        touch.setId(user.getId());
        touch.setLastLoginIp(ip);
        touch.setLastLoginTime(LocalDateTime.now());
        adminUsers.updateById(touch);

        recordLoginLog(user.getId(), user.getUsername(), ip, "SUCCESS", null);
        audit.record(ACTION_ADMIN_LOGIN, user.getId(), null, "sys_admin_user", user.getId(), ip,
                "后台登录成功;role=" + role.getCode());

        return AdminLoginResp.of(token, user.getId(), user.getUsername(), user.getName(),
                role.getCode(), role.getName(), perms);
    }

    /** 登出：递增 token_version，使已签发的 admin token 立即失效（单点失效语义）。 */
    public void logout() {
        Long adminId = AdminUserContext.adminId();
        if (adminId == null) {
            return;
        }
        SysAdminUser user = adminUsers.selectById(adminId);
        if (user == null) {
            return;
        }
        long next = (user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1;
        SysAdminUser bump = new SysAdminUser();
        bump.setId(adminId);
        bump.setTokenVersion(next);
        adminUsers.updateById(bump);
        audit.record(ACTION_ADMIN_LOGOUT, adminId, null, "sys_admin_user", adminId, RequestContext.ip(),
                "后台登出");
    }

    /** 当前登录管理员信息（含权限点，供前端按钮级鉴权）。 */
    public AdminMeResp me() {
        LoginAdmin admin = AdminUserContext.get();
        if (admin == null) {
            throw new BizException(ResultCode.E001_NO_WX_AUTH);
        }
        AdminRole role = admin.role();
        return AdminMeResp.of(admin.getAdminId(), admin.getUsername(), admin.getName(),
                admin.getRoleCode(), role == null ? null : role.getLabel(),
                admin.isSuperAdmin(), AdminUserContext.permissions());
    }

    /** 加载某角色的权限点集合（resource:action）。 */
    public Set<String> loadPermissions(Long roleId) {
        return rolePermissions.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, roleId))
                .stream()
                .map(p -> p.getResource() + ":" + p.getAction())
                .collect(Collectors.toSet());
    }

    private void recordLoginLog(Long adminId, String username, String ip, String result, String failReason) {
        SysAdminLoginLog log = new SysAdminLoginLog();
        log.setAdminId(adminId);
        log.setUsername(username);
        log.setIp(ip);
        log.setResult(result);
        log.setFailReason(failReason);
        log.setLoginTime(LocalDateTime.now());
        loginLogs.insert(log);
    }
}
