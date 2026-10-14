-- v014 家庭多家长 / 多学生 / 多家庭支持
-- 与 sprint 设计一致：权限角色(PARENT/CHILD) 与 关系标签(父亲/母亲/爷爷…) 解耦；
-- 邀请码按类型(PARENT/CHILD)锁定加入角色；家长码免审、学生码需审；同一用户可多家庭归属。

-- 1) 放开多家庭：删除基于生成列的全局唯一键（曾强约束「一个 CHILD 同时只在一个家庭」）
ALTER TABLE usr_family_member DROP KEY uk_effective_child;

-- 2) 新增审计列：记录成员经哪个邀请码加入，便于溯源 / 撤销
ALTER TABLE usr_family_member ADD COLUMN invite_id BIGINT DEFAULT NULL;

-- 3) 新增邀请码表（多码并存 / 按类型锁定角色 / 可撤销 / 可过期 / 可限次）
CREATE TABLE usr_family_invite (
  id               BIGINT      PRIMARY KEY AUTO_INCREMENT,
  family_id        BIGINT      NOT NULL,
  code             VARCHAR(6)  NOT NULL,                 -- 6 位大写字母数字, 全局唯一
  invite_type      VARCHAR(8)  NOT NULL,                 -- PARENT / CHILD (锁定加入角色)
  creator_user_id  BIGINT      NOT NULL,                 -- 生成者(家长)
  status           VARCHAR(8)  NOT NULL DEFAULT 'ACTIVE',-- ACTIVE / REVOKED
  expire_at        BIGINT      NOT NULL,                 -- 毫秒时间戳
  max_use          INT         NOT NULL DEFAULT 0,       -- 0=不限制使用次数
  used_count       INT         NOT NULL DEFAULT 0,
  create_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at        BIGINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_code (code),
  KEY idx_family (family_id),
  CONSTRAINT fk_invite_family FOREIGN KEY (family_id) REFERENCES usr_family (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4) 旧单码迁移：未过期的旧邀请码补录为 CHILD 类型（家长重新生成家长码请走新接口）
INSERT INTO usr_family_invite (family_id, code, invite_type, creator_user_id, status, expire_at, max_use, used_count, delete_at)
SELECT id, invite_code, 'CHILD', owner_user_id, 'ACTIVE', invite_code_expire, 0, 0, 0
FROM usr_family WHERE invite_code IS NOT NULL AND invite_code_expire > UNIX_TIMESTAMP() * 1000;
