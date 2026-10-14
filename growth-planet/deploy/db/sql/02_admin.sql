-- =====================================================================
-- 运营后台独立账号体系（里程碑 A 地基）
-- 命名与现有 v00x_*.sql 一致，由 application-test.yml 的 schema-locations 加载
-- 角色/权限矩阵/默认 SA 账号的 Seed 由 AdminDataInitializer 在启动时幂等写入，
-- 故此处仅建表，不写业务数据。
-- =====================================================================

-- ----------------------------
-- 后台角色表 sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(16)  NOT NULL COMMENT '角色代码 SA/OP/CR/DC/CP/RA',
  `name`        VARCHAR(32)  NOT NULL COMMENT '角色名称',
  `remark`      VARCHAR(128) DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at`   BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`, `delete_at`),
  KEY `idx_role_delete` (`delete_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台运营角色（独立于小程序 usr_user.role）';

-- ----------------------------
-- 角色权限点 sys_role_permission（资源 × 操作）
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `role_id`     BIGINT       NOT NULL,
  `resource`    VARCHAR(64)  NOT NULL COMMENT '资源标识（见 AdminResource 常量）',
  `action`      VARCHAR(16)  NOT NULL COMMENT 'view/create/edit/delete/export/approve/config',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at`   BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rp` (`role_id`, `resource`, `action`, `delete_at`),
  KEY `idx_rp_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色×资源×操作授权点（细粒度 RBAC 落库）';

-- ----------------------------
-- 后台运营账号 sys_admin_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_admin_user`;
CREATE TABLE `sys_admin_user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `username`        VARCHAR(64)  NOT NULL COMMENT '登录账号（唯一）',
  `name`            VARCHAR(32)  NOT NULL COMMENT '姓名',
  `password`        VARCHAR(200) NOT NULL COMMENT 'PBKDF2 哈希',
  `salt`            VARCHAR(64)  NOT NULL COMMENT 'PBKDF2 盐',
  `role_id`         BIGINT       NOT NULL,
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `token_version`   BIGINT       NOT NULL DEFAULT 0 COMMENT '失效版本：改密/禁用自增，旧 token 即失效',

  `last_login_ip`   VARCHAR(64)  DEFAULT NULL,
  `last_login_time` DATETIME     DEFAULT NULL,
  `creator_id`      BIGINT       DEFAULT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at`       BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_username` (`username`, `delete_at`),
  KEY `idx_admin_role` (`role_id`),
  KEY `idx_admin_delete` (`delete_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台运营人员账号（与 C 端 usr_user 完全隔离）';

-- ----------------------------
-- 后台登录日志 sys_admin_login_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_admin_login_log`;
CREATE TABLE `sys_admin_login_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `admin_id`    BIGINT       DEFAULT NULL COMMENT '关联管理员 id；账号不存在/未知时为 NULL（记录失败尝试）',
  `username`    VARCHAR(64)  NOT NULL,
  `ip`          VARCHAR(64)  DEFAULT NULL,
  `result`      VARCHAR(16)  NOT NULL COMMENT 'SUCCESS/FAILURE',
  `fail_reason` VARCHAR(128) DEFAULT NULL,
  `login_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录发生时间',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at`   BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_login_admin` (`admin_id`),
  KEY `idx_login_delete` (`delete_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台登录审计（补充 sys_audit_log 的结构化登录流水）';
