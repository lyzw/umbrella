package cn.studykid.growthplanet.config;

import cn.studykid.growthplanet.common.constant.AdminResource;
import cn.studykid.growthplanet.entity.SysAdminUser;
import cn.studykid.growthplanet.entity.SysRole;
import cn.studykid.growthplanet.entity.SysRolePermission;
import cn.studykid.growthplanet.mapper.AdminUserMapper;
import cn.studykid.growthplanet.mapper.RoleMapper;
import cn.studykid.growthplanet.mapper.RolePermissionMapper;
import cn.studykid.growthplanet.util.AdminPasswordEncoder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 后台运营端数据初始化（幂等，应用启动时执行一次）：
 * <ol>
 *   <li>播种 6 个角色 sys_role（按 code 幂等）；</li>
 *   <li>按详细设计 §3.4「细粒度权限点矩阵」播种 sys_role_permission（仅当该角色尚无权限点时播种，
 *       以保留后续在控制台中的手工调整；SA 不落库，运行时以 {@code isSuperAdmin()} 旁路，矩阵接口回显全量授权）；</li>
 *   <li>当系统尚无任何后台账号时，创建首个超级管理员（账号/密码来自 console.bootstrap.*，
 *       生产环境若沿用默认密码则打印 WARN 提醒立即修改）。</li>
 * </ol>
 */
@Component
public class AdminDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminDataInitializer.class);
    private static final String DEFAULT_PASSWORD = "admin123";

    private final RoleMapper roles;
    private final RolePermissionMapper rolePermissions;
    private final AdminUserMapper adminUsers;
    private final AdminPasswordEncoder passwordEncoder;
    private final Environment environment;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    private final String bootstrapUsername;
    private final String bootstrapPassword;
    private final String bootstrapName;

    public AdminDataInitializer(RoleMapper roles, RolePermissionMapper rolePermissions,
                                AdminUserMapper adminUsers, AdminPasswordEncoder passwordEncoder,
                                Environment environment,
                                org.springframework.jdbc.core.JdbcTemplate jdbc,
                                @Value("${console.bootstrap.username:admin.zhou}") String bootstrapUsername,
                                @Value("${console.bootstrap.password:admin123}") String bootstrapPassword,
                                @Value("${console.bootstrap.name:超级管理员}") String bootstrapName) {
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.adminUsers = adminUsers;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.jdbc = jdbc;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
        this.bootstrapName = bootstrapName;
    }

    @Override
    public void run(String... args) {
        // sys_role 表不存在时跳过（例如仅装载 legacy 基础表结构的迁移回归容器）：
        // 初始化器只对已建后台表结构的库负责，避免这类上下文启动即失败。
        if (!adminSchemaPresent()) {
            log.warn("[admin-init] 未检测到 sys_role 表，跳过后台数据播种（请确认已执行 v008_admin.sql）");
            return;
        }
        Map<String, SysRole> roleMap = ensureRoles();
        seedPermissionMatrix(roleMap);
        ensureBootstrapSuperAdmin(roleMap.get("SA"));
        seedComplianceChecklist();
    }

    private boolean adminSchemaPresent() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role'",
                Integer.class);
        return count != null && count > 0;
    }

    // ==================== 角色 ====================

    private Map<String, SysRole> ensureRoles() {
        Map<String, SysRole> map = new LinkedHashMap<>();
        for (cn.studykid.growthplanet.common.enums.AdminRole def
                : cn.studykid.growthplanet.common.enums.AdminRole.values()) {
            SysRole existed = roles.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, def.name()));
            if (existed == null) {
                SysRole role = new SysRole();
                role.setCode(def.name());
                role.setName(def.getLabel());
                role.setRemark(def.getDescription());
                roles.insert(role);
                map.put(def.name(), role);
                log.info("[admin-init] 已创建角色 {} ({})", def.name(), def.getLabel());
            } else {
                map.put(def.name(), existed);
            }
        }
        return map;
    }

    // ==================== 权限矩阵（§3.4） ====================

    private void seedPermissionMatrix(Map<String, SysRole> roleMap) {
        // 一次性预判各角色是否已播种（幂等按「角色」粒度，而非按单次 grant 调用），
        // 避免后续新增资源时覆盖控制台中的手工调整。
        Set<String> seeded = new HashSet<>();
        for (Map.Entry<String, SysRole> e : roleMap.entrySet()) {
            long n = rolePermissions.selectCount(new LambdaQueryWrapper<SysRolePermission>()
                    .eq(SysRolePermission::getRoleId, e.getValue().getId()));
            if (n > 0) {
                seeded.add(e.getKey());
            }
        }

        // OP 运营管理员：日常内容配置 + 业务数据查询/人工干预 + 看板
        grant(roleMap, seeded, "OP", AdminResource.WORKBENCH, "view");
        grant(roleMap, seeded, "OP", AdminResource.DASHBOARD, "view", "export");
        grant(roleMap, seeded, "OP", AdminResource.REPORT_EXPORT, "view", "export");
        grant(roleMap, seeded, "OP", AdminResource.FAMILY_QUERY, "view");
        grant(roleMap, seeded, "OP", AdminResource.DISH, "view", "create", "edit", "delete", "export");
        grant(roleMap, seeded, "OP", AdminResource.DISH_CATEGORY, "view", "create", "edit", "delete");
        grant(roleMap, seeded, "OP", AdminResource.MENU, "view", "create", "edit", "delete", "config");
        grant(roleMap, seeded, "OP", AdminResource.TASK_LIB, "view", "create", "edit", "delete");
        grant(roleMap, seeded, "OP", AdminResource.MEDAL, "view", "create", "edit", "delete");
        grant(roleMap, seeded, "OP", AdminResource.WISH_MENU, "view", "edit", "config");
        grant(roleMap, seeded, "OP", AdminResource.UGC_QUEUE, "view", "approve");
        grant(roleMap, seeded, "OP", AdminResource.WANT_EAT, "view", "export");
        grant(roleMap, seeded, "OP", AdminResource.CONFIRM, "view", "export");
        grant(roleMap, seeded, "OP", AdminResource.APPROVAL, "view");
        grant(roleMap, seeded, "OP", AdminResource.WALLET, "view", "export");
        grant(roleMap, seeded, "OP", AdminResource.CHORE_HEALTH, "view");
        grant(roleMap, seeded, "OP", AdminResource.MEDAL_AWARD, "view", "edit");
        grant(roleMap, seeded, "OP", AdminResource.NOTICE_TPL, "view");
        grant(roleMap, seeded, "OP", AdminResource.NOTICE_STAT, "view");
        grant(roleMap, seeded, "OP", AdminResource.HELP, "view");

        // CR 内容审核员：UGC 机审+人审、敏感词/图像风控
        grant(roleMap, seeded, "CR", AdminResource.WORKBENCH, "view");
        grant(roleMap, seeded, "CR", AdminResource.DASHBOARD, "view");
        grant(roleMap, seeded, "CR", AdminResource.UGC_QUEUE, "view", "approve", "delete");
        grant(roleMap, seeded, "CR", AdminResource.WORD_LIB, "view", "create", "edit", "delete");
        grant(roleMap, seeded, "CR", AdminResource.HELP, "view");

        // DC 数据运营/客服：看板报表、家庭/孩子工单协助、通知统计
        grant(roleMap, seeded, "DC", AdminResource.WORKBENCH, "view");
        grant(roleMap, seeded, "DC", AdminResource.DASHBOARD, "view", "export");
        grant(roleMap, seeded, "DC", AdminResource.REPORT_EXPORT, "view", "export");
        grant(roleMap, seeded, "DC", AdminResource.FAMILY_QUERY, "view", "export");
        grant(roleMap, seeded, "DC", AdminResource.RISK_CONTROL, "view");
        grant(roleMap, seeded, "DC", AdminResource.WANT_EAT, "view");
        grant(roleMap, seeded, "DC", AdminResource.CONFIRM, "view");
        grant(roleMap, seeded, "DC", AdminResource.WALLET, "view");
        grant(roleMap, seeded, "DC", AdminResource.CHORE_HEALTH, "view");
        grant(roleMap, seeded, "DC", AdminResource.NOTICE_TPL, "view");
        grant(roleMap, seeded, "DC", AdminResource.NOTICE_STAT, "view", "export");
        grant(roleMap, seeded, "DC", AdminResource.HELP, "view");

        // CP 合规/隐私专员：同意核验、隐私工单闭环、合规清单（隔离）
        grant(roleMap, seeded, "CP", AdminResource.WORKBENCH, "view");
        grant(roleMap, seeded, "CP", AdminResource.CONSENT, "view", "export");
        grant(roleMap, seeded, "CP", AdminResource.PRIVACY_TICKET, "view", "edit", "approve", "export");
        grant(roleMap, seeded, "CP", AdminResource.VERIFY, "view");
        grant(roleMap, seeded, "CP", AdminResource.COMPLIANCE, "view", "config");
        grant(roleMap, seeded, "CP", AdminResource.HELP, "view");

        // RA 只读审计员：仅查看审计/日志/看板，无写权限
        grant(roleMap, seeded, "RA", AdminResource.WORKBENCH, "view");
        grant(roleMap, seeded, "RA", AdminResource.DASHBOARD, "view");
        grant(roleMap, seeded, "RA", AdminResource.AUDIT_LOG, "view", "export");
        grant(roleMap, seeded, "RA", AdminResource.C_AUDIT, "view", "export");
        grant(roleMap, seeded, "RA", AdminResource.HELP, "view");

        // M6 补播：详设 §3.4 规定「操作日志/C 端关键操作 查看=全部角色」。
        // 上面的 grant 按角色粒度幂等会跳过已播种角色，这里按（角色×资源）粒度补齐 M6 基线查看权限，
        // 不触碰各角色已有的其他权限点（不覆盖控制台手工调整）。
        for (String roleCode : new String[]{"OP", "CR", "DC", "CP"}) {
            supplement(roleMap, roleCode, AdminResource.AUDIT_LOG, "view");
            supplement(roleMap, roleCode, AdminResource.C_AUDIT, "view");
        }

        // M4 补播：详设 §3.4 M4 行与初始化基线的差量（按「角色×资源×动作」粒度，见 supplementPerm）。
        // OP：超额确认单复核走 approve（§3.4 确认单 审批列=SA,OP）。
        supplementPerm(roleMap, "OP", AdminResource.CONFIRM, "approve");
        // DC：M4 导出列=SA,OP,DC（基线只给了 view）。
        supplementPerm(roleMap, "DC", AdminResource.WANT_EAT, "export");
        supplementPerm(roleMap, "DC", AdminResource.CONFIRM, "export");
        supplementPerm(roleMap, "DC", AdminResource.APPROVAL, "export");
        supplementPerm(roleMap, "DC", AdminResource.WALLET, "export");
        supplementPerm(roleMap, "DC", AdminResource.CHORE_HEALTH, "export");
        supplementPerm(roleMap, "DC", AdminResource.MEDAL_AWARD, "export");
        // CP：M4 查看列含 CP。
        supplementPerm(roleMap, "CP", AdminResource.WANT_EAT, "view");
        supplementPerm(roleMap, "CP", AdminResource.CONFIRM, "view");
        supplementPerm(roleMap, "CP", AdminResource.APPROVAL, "view");
        supplementPerm(roleMap, "CP", AdminResource.WALLET, "view");
        // RA：审批记录 查看=SA,OP,DC,CP,RA。
        supplementPerm(roleMap, "RA", AdminResource.APPROVAL, "view");

        // M5 补播：隐私域（同意留痕/隐私工单/核验记录/合规清单）查看列 = SA,CP,RA（DC/OP/CR 不可见）。
        // CP 已在基线授予 view/approve，SA 运行时旁路；此处补齐 RA 的只读视角。
        supplementPerm(roleMap, "RA", AdminResource.CONSENT, "view");
        supplementPerm(roleMap, "RA", AdminResource.PRIVACY_TICKET, "view");
        supplementPerm(roleMap, "RA", AdminResource.VERIFY, "view");
        supplementPerm(roleMap, "RA", AdminResource.COMPLIANCE, "view");

        // 对象存储补播：新增资源「对象存储」+ 复用「新建」动作（语义=新建一个存储对象）。
        // OP 已在基线播种（按角色粒度幂等会整体跳过），故走 supplementPerm 差量补播。
        supplementPerm(roleMap, "OP", AdminResource.STORAGE, "create");
    }

    /** 按（角色×资源×动作）粒度补播：仅当该权限点尚不存在时插入，用于存量角色的差量基线。 */
    private void supplementPerm(Map<String, SysRole> roleMap, String roleCode,
                                String resource, String action) {
        SysRole role = roleMap.get(roleCode);
        if (role == null) {
            return;
        }
        long existing = rolePermissions.selectCount(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, role.getId())
                .eq(SysRolePermission::getResource, resource)
                .eq(SysRolePermission::getAction, action));
        if (existing > 0) {
            return;
        }
        SysRolePermission perm = new SysRolePermission();
        perm.setRoleId(role.getId());
        perm.setResource(resource);
        perm.setAction(action);
        rolePermissions.insert(perm);
        log.info("[admin-init] 已为角色 {} 补播权限点 {}:{}（§3.4 基线差量）", roleCode, resource, action);
    }

    /** 按（角色×资源）粒度补播：仅当该角色在该资源上尚无任何权限点时插入，用于存量角色的新增基线资源。 */
    private void supplement(Map<String, SysRole> roleMap, String roleCode,
                            String resource, String... actions) {
        SysRole role = roleMap.get(roleCode);
        if (role == null) {
            return;
        }
        long existing = rolePermissions.selectCount(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, role.getId())
                .eq(SysRolePermission::getResource, resource));
        if (existing > 0) {
            return;
        }
        for (String action : actions) {
            SysRolePermission perm = new SysRolePermission();
            perm.setRoleId(role.getId());
            perm.setResource(resource);
            perm.setAction(action);
            rolePermissions.insert(perm);
            log.info("[admin-init] 已为角色 {} 补播权限点 {}:{}（§3.4 基线）", roleCode, resource, action);
        }
    }

    /** 为角色授予资源上的若干操作；角色已播种过则整体跳过（幂等，按角色粒度）。 */
    private void grant(Map<String, SysRole> roleMap, Set<String> seededRoles, String roleCode,
                       String resource, String... actions) {
        if (seededRoles.contains(roleCode)) {
            return;
        }
        SysRole role = roleMap.get(roleCode);
        if (role == null) {
            return;
        }
        for (String action : actions) {
            SysRolePermission perm = new SysRolePermission();
            perm.setRoleId(role.getId());
            perm.setResource(resource);
            perm.setAction(action);
            rolePermissions.insert(perm);
        }
    }

    // ==================== 首个超级管理员 ====================

    private void ensureBootstrapSuperAdmin(SysRole saRole) {
        if (saRole == null) {
            return;
        }
        long total = adminUsers.selectCount(new LambdaQueryWrapper<>());
        if (total > 0) {
            return;
        }
        String salt = passwordEncoder.genSalt();
        SysAdminUser admin = new SysAdminUser();
        admin.setUsername(bootstrapUsername);
        admin.setName(bootstrapName);
        admin.setSalt(salt);
        admin.setPassword(passwordEncoder.encode(bootstrapPassword, salt));
        admin.setRoleId(saRole.getId());
        admin.setStatus("ACTIVE");
        admin.setTokenVersion(0L);
        adminUsers.insert(admin);

        log.warn("[admin-init] 已创建首个超级管理员账号 [{}]。请登录后立即修改密码。", bootstrapUsername);
        if (DEFAULT_PASSWORD.equals(bootstrapPassword)
                && isProductionLike()) {
            log.warn("[admin-init] 检测到生产环境仍在使用默认后台密码，存在安全风险，请通过 "
                    + "CONSOLE_BOOTSTRAP_PASSWORD 或控制台立即修改！");
        }
    }

    // ==================== 合规清单默认项（M5） ====================

    /** 幂等播种合规清单默认项（按 item_key 去重，不覆盖控制台中的手工勾检状态）。 */
    private void seedComplianceChecklist() {
        if (!complianceSchemaPresent()) {
            log.warn("[admin-init] 未检测到 sys_compliance_checklist 表，跳过合规清单播种（请确认已执行 v010_compliance_checklist.sql）");
            return;
        }
        for (String[] item : COMPLIANCE_SEED) {
            String key = item[0];
            Long existing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_compliance_checklist WHERE item_key = ? AND delete_at = 0",
                    Long.class, key);
            if (existing != null && existing > 0) {
                continue;
            }
            jdbc.update(
                    "INSERT INTO sys_compliance_checklist (item_key, item_text, checked, create_time) "
                            + "VALUES (?, ?, 0, CURRENT_TIMESTAMP)",
                    key, item[1]);
        }
    }

    private boolean complianceSchemaPresent() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_compliance_checklist'",
                Integer.class);
        return count != null && count > 0;
    }

    /** M5 合规清单静态默认项：key + 描述。仅播种基线，勾检状态由运营在控制台维护。 */
    private static final String[][] COMPLIANCE_SEED = {
            {"AGE_VERIFY", "已核验儿童年龄（≥8 岁）与监护人身份真实性"},
            {"CONSENT_VALID", "监护人同意书（ORDER/PROFILE）在有效期内且未被撤回"},
            {"DATA_MINIMIZE", "数据导出/删除仅针对授权范围（家庭/孩子），不含无关家庭成员"},
            {"RETENTION", "敏感数据留存符合最小必要与留存期要求"},
            {"BREACH_SELFCHECK", "本周期已完成数据安全与越权访问自查"},
    };

    private boolean isProductionLike() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
