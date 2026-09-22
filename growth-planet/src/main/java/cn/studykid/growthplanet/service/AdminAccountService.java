package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.constant.AdminResource;
import cn.studykid.growthplanet.common.context.AdminUserContext;
import cn.studykid.growthplanet.common.enums.AdminAction;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.AdminAccountUpsertReq;
import cn.studykid.growthplanet.dto.response.AdminAccountResp;
import cn.studykid.growthplanet.dto.response.AdminPermissionRow;
import cn.studykid.growthplanet.dto.response.AdminRoleResp;
import cn.studykid.growthplanet.dto.response.AdminWorkbenchResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import cn.studykid.growthplanet.entity.MenuConfirm;
import cn.studykid.growthplanet.entity.PrivacyRequest;
import cn.studykid.growthplanet.entity.SysAdminUser;
import cn.studykid.growthplanet.entity.SysRole;
import cn.studykid.growthplanet.entity.SysRolePermission;
import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.mapper.AdminUserMapper;
import cn.studykid.growthplanet.mapper.FamilyMapper;
import cn.studykid.growthplanet.mapper.MenuConfirmMapper;
import cn.studykid.growthplanet.mapper.PrivacyRequestMapper;
import cn.studykid.growthplanet.mapper.RoleMapper;
import cn.studykid.growthplanet.mapper.RolePermissionMapper;
import cn.studykid.growthplanet.mapper.UserMapper;
import cn.studykid.growthplanet.util.AdminPasswordEncoder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 后台账号与角色权限服务（M2）+ 运营工作台聚合（M0）。
 * 所有写操作先经 {@link AdminUserContext#requirePerm} 细粒度校验，再落审计。
 * 归属字段（adminId/roleId）由服务端派生，绝不信任客户端传入的身份字段。
 */
@Service
public class AdminAccountService {

    public static final String ACTION_ACCOUNT_CREATE = "ADMIN_ACCOUNT_CREATE";
    public static final String ACTION_ACCOUNT_UPDATE = "ADMIN_ACCOUNT_UPDATE";
    public static final String ACTION_ACCOUNT_STATUS = "ADMIN_ACCOUNT_STATUS";
    public static final String ACTION_ACCOUNT_PASSWORD = "ADMIN_ACCOUNT_PASSWORD";

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> VALID_STATUS = Set.of("ACTIVE", "DISABLED");

    private final AdminUserMapper adminUsers;
    private final RoleMapper roles;
    private final RolePermissionMapper rolePermissions;
    private final AdminPasswordEncoder passwordEncoder;
    private final AuditService audit;

    // 工作台聚合依赖
    private final FamilyMapper families;
    private final UserMapper users;
    private final MenuConfirmMapper confirms;
    private final PrivacyRequestMapper privacyRequests;

    public AdminAccountService(AdminUserMapper adminUsers, RoleMapper roles,
                               RolePermissionMapper rolePermissions, AdminPasswordEncoder passwordEncoder,
                               AuditService audit, FamilyMapper families, UserMapper users,
                               MenuConfirmMapper confirms, PrivacyRequestMapper privacyRequests) {
        this.adminUsers = adminUsers;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.families = families;
        this.users = users;
        this.confirms = confirms;
        this.privacyRequests = privacyRequests;
    }

    // ==================== M2：账号管理 ====================

    /** 账号列表（按 登录账号/姓名 关键字模糊，分页）。 */
    public PageResp<AdminAccountResp> listAccounts(String keyword, int page, int pageSize) {
        AdminUserContext.requirePerm(AdminResource.ACCOUNT, AdminAction.VIEW.code());
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "分页参数非法");
        }
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        LambdaQueryWrapper<SysAdminUser> query = new LambdaQueryWrapper<>();
        if (kw != null) {
            query.and(w -> w.like(SysAdminUser::getUsername, kw).or().like(SysAdminUser::getName, kw));
        }
        query.orderByAsc(SysAdminUser::getId);

        long total = adminUsers.selectCount(query);
        long offset = (long) (page - 1) * pageSize;
        query.last("LIMIT " + offset + ", " + pageSize);
        List<SysAdminUser> rows = adminUsers.selectList(query);

        Map<Long, SysRole> roleMap = roles.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(SysRole::getId, Function.identity()));
        List<AdminAccountResp> items = rows.stream()
                .map(u -> AdminAccountResp.from(u, roleMap.get(u.getRoleId())))
                .toList();
        return new PageResp<>(items, total, page, pageSize);
    }

    /** 新建账号。 */
    @Transactional
    public AdminAccountResp createAccount(AdminAccountUpsertReq req) {
        AdminUserContext.requirePerm(AdminResource.ACCOUNT, AdminAction.CREATE.code());
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "新建账号必须设置密码");
        }
        requireUsernameFree(req.getUsername(), null);
        SysRole role = requireRoleByCode(req.getRoleCode());

        String status = normalizeStatus(req.getStatus(), "ACTIVE");
        String salt = passwordEncoder.genSalt();
        SysAdminUser user = new SysAdminUser();
        user.setUsername(req.getUsername().trim());
        user.setName(req.getName().trim());
        user.setSalt(salt);
        user.setPassword(passwordEncoder.encode(req.getPassword(), salt));
        user.setRoleId(role.getId());
        user.setStatus(status);
        user.setTokenVersion(0L);
        user.setCreatorId(AdminUserContext.adminId());
        adminUsers.insert(user);

        audit.record(ACTION_ACCOUNT_CREATE, AdminUserContext.adminId(), null, "sys_admin_user",
                user.getId(), null, "username=" + user.getUsername() + ";role=" + role.getCode());
        return AdminAccountResp.from(user, role);
    }

    /** 编辑账号（密码留空表示不改；改密会递增 token_version 使旧登录失效）。 */
    @Transactional
    public AdminAccountResp updateAccount(Long id, AdminAccountUpsertReq req) {
        AdminUserContext.requirePerm(AdminResource.ACCOUNT, AdminAction.EDIT.code());
        SysAdminUser existed = requireAccount(id);
        requireUsernameFree(req.getUsername(), id);
        SysRole role = requireRoleByCode(req.getRoleCode());

        SysAdminUser update = new SysAdminUser();
        update.setId(id);
        update.setUsername(req.getUsername().trim());
        update.setName(req.getName().trim());
        update.setRoleId(role.getId());
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            update.setStatus(normalizeStatus(req.getStatus(), existed.getStatus()));
        }
        boolean passwordChanged = false;
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            String salt = passwordEncoder.genSalt();
            update.setSalt(salt);
            update.setPassword(passwordEncoder.encode(req.getPassword(), salt));
            update.setTokenVersion(nextVersion(existed));
            passwordChanged = true;
        }
        adminUsers.updateById(update);

        audit.record(ACTION_ACCOUNT_UPDATE, AdminUserContext.adminId(), null, "sys_admin_user", id, null,
                "username=" + update.getUsername() + ";role=" + role.getCode()
                        + (passwordChanged ? ";passwordChanged=true" : ""));
        return AdminAccountResp.from(adminUsers.selectById(id), role);
    }

    /** 启用/禁用账号（禁用会递增 token_version，立即失效既有登录态）。 */
    @Transactional
    public AdminAccountResp changeStatus(Long id, String status) {
        AdminUserContext.requirePerm(AdminResource.ACCOUNT, AdminAction.EDIT.code());
        String normalized = normalizeStatus(status, null);
        if (id.equals(AdminUserContext.adminId()) && "DISABLED".equals(normalized)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "不能禁用自己的账号");
        }
        SysAdminUser existed = requireAccount(id);
        SysAdminUser update = new SysAdminUser();
        update.setId(id);
        update.setStatus(normalized);
        if ("DISABLED".equals(normalized)) {
            update.setTokenVersion(nextVersion(existed));
        }
        adminUsers.updateById(update);

        audit.record(ACTION_ACCOUNT_STATUS, AdminUserContext.adminId(), null, "sys_admin_user", id, null,
                "status=" + normalized);
        return AdminAccountResp.from(adminUsers.selectById(id), roles.selectById(existed.getRoleId()));
    }

    /** 重置密码（递增 token_version 使旧登录失效）。 */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        AdminUserContext.requirePerm(AdminResource.ACCOUNT, AdminAction.EDIT.code());
        if (newPassword == null || newPassword.isBlank()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "新密码不能为空");
        }
        SysAdminUser existed = requireAccount(id);
        String salt = passwordEncoder.genSalt();
        SysAdminUser update = new SysAdminUser();
        update.setId(id);
        update.setSalt(salt);
        update.setPassword(passwordEncoder.encode(newPassword, salt));
        update.setTokenVersion(nextVersion(existed));
        adminUsers.updateById(update);

        audit.record(ACTION_ACCOUNT_PASSWORD, AdminUserContext.adminId(), null, "sys_admin_user", id, null,
                "passwordReset=true");
    }

    // ==================== M2：角色与权限矩阵 ====================

    /** 角色列表（含权限点数量）。 */
    public List<AdminRoleResp> listRoles() {
        AdminUserContext.requirePerm(AdminResource.ROLE, AdminAction.VIEW.code());
        List<SysRole> all = roles.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
        Map<Long, Long> counts = rolePermissions.selectList(new LambdaQueryWrapper<SysRolePermission>())
                .stream()
                .collect(Collectors.groupingBy(SysRolePermission::getRoleId, Collectors.counting()));
        List<AdminRoleResp> items = new ArrayList<>();
        for (SysRole role : all) {
            items.add(AdminRoleResp.of(role, counts.getOrDefault(role.getId(), 0L)));
        }
        return items;
    }

    /** 某角色的「资源 × 操作」权限矩阵（SA 返回全量授权）。 */
    public List<AdminPermissionRow> permissionMatrix(String roleCode) {
        AdminUserContext.requirePerm(AdminResource.ROLE, AdminAction.VIEW.code());
        SysRole role = requireRoleByCode(roleCode);

        Set<String> granted;
        boolean allGranted = "SA".equals(role.getCode());
        if (allGranted) {
            granted = Set.of();
        } else {
            granted = rolePermissions.selectList(new LambdaQueryWrapper<SysRolePermission>()
                            .eq(SysRolePermission::getRoleId, role.getId()))
                    .stream()
                    .map(p -> p.getResource() + ":" + p.getAction())
                    .collect(Collectors.toSet());
        }

        List<AdminPermissionRow> rows = new ArrayList<>();
        for (String resource : AdminResource.ALL) {
            AdminPermissionRow row = new AdminPermissionRow(resource);
            for (AdminAction action : AdminAction.values()) {
                boolean ok = allGranted || granted.contains(resource + ":" + action.code());
                row.put(action.code(), ok);
            }
            rows.add(row);
        }
        return rows;
    }

    // ==================== M0：运营工作台 ====================

    /** 工作台概览 KPI + 待办入口。 */
    public AdminWorkbenchResp workbench() {
        AdminUserContext.requirePerm(AdminResource.WORKBENCH, AdminAction.VIEW.code());

        long familyTotal = families.selectCount(new LambdaQueryWrapper<>());
        long childTotal = users.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "CHILD"));
        long adminAccountTotal = adminUsers.selectCount(new LambdaQueryWrapper<>());
        long pendingConfirmTotal = confirms.selectCount(
                new LambdaQueryWrapper<MenuConfirm>().eq(MenuConfirm::getStatus, "PENDING"));
        long pendingPrivacyTotal = privacyRequests.selectCount(
                new LambdaQueryWrapper<PrivacyRequest>().in(PrivacyRequest::getStatus, "RECEIVED", "PROCESSING"));

        AdminWorkbenchResp.Kpis kpis = new AdminWorkbenchResp.Kpis(
                familyTotal, childTotal, adminAccountTotal, pendingConfirmTotal, pendingPrivacyTotal);

        List<AdminWorkbenchResp.TodoItem> todos = new ArrayList<>();
        todos.add(new AdminWorkbenchResp.TodoItem("M2", "后台账号总数", adminAccountTotal, "/accounts"));
        todos.add(new AdminWorkbenchResp.TodoItem("M4", "待处理确认单", pendingConfirmTotal, "/bizdata/confirm"));
        todos.add(new AdminWorkbenchResp.TodoItem("M5", "待处理隐私工单", pendingPrivacyTotal, "/privacy/tickets"));
        return AdminWorkbenchResp.of(kpis, todos);
    }

    // ==================== 内部工具 ====================

    private SysAdminUser requireAccount(Long id) {
        SysAdminUser user = adminUsers.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.E404_NOT_FOUND, "后台账号不存在");
        }
        return user;
    }

    private SysRole requireRoleByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "角色不能为空");
        }
        SysRole role = roles.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code.trim()));
        if (role == null) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "角色不存在：" + code);
        }
        return role;
    }

    private void requireUsernameFree(String username, Long selfId) {
        if (username == null || username.isBlank()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "登录账号不能为空");
        }
        SysAdminUser existed = adminUsers.selectOne(new LambdaQueryWrapper<SysAdminUser>()
                .eq(SysAdminUser::getUsername, username.trim()));
        if (existed != null && !existed.getId().equals(selfId)) {
            throw new BizException(ResultCode.E007_CONCURRENCY_CONFLICT, "登录账号已存在");
        }
    }

    private String normalizeStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            if (fallback == null) {
                throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "状态不能为空");
            }
            return fallback;
        }
        String s = status.trim().toUpperCase();
        if (!VALID_STATUS.contains(s)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "非法状态：" + status);
        }
        return s;
    }

    private long nextVersion(SysAdminUser user) {
        return (user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1;
    }
}
