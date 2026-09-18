-- 停写窗口内执行；备份和 01 预检完成后才可切换应用。
START TRANSACTION;
UPDATE usr_user SET role = 'UNSELECTED' WHERE role = 'UNSET';
UPDATE usr_user SET token_version = token_version + 1;
UPDATE usr_family_member SET bind_status = 'BOUND'
WHERE role = 'PARENT' AND bind_status = 'APPROVED';
-- 不将历史 APPROVED 视为有效儿童授权；重新获取与申请关联的 PROFILE 同意后审批。
UPDATE usr_family_member SET bind_status = 'PENDING', application_version = application_version + 1
WHERE role = 'CHILD' AND bind_status IN ('APPROVED', 'BOUND');
UPDATE usr_family_member SET guardian_status = 'UNVERIFIED';
UPDATE usr_child_profile SET profile_status = 'INCOMPLETE';
COMMIT;

-- 旧同意保留原始历史，apply_id/application_version 保持 NULL，因此不能授权新写入。
-- 原实现被覆盖的 GRANT 无法重构；禁止通过回填伪造 SELF_ATTESTED 或 VERIFIED。
ALTER TABLE usr_user
  MODIFY COLUMN role VARCHAR(16) NOT NULL DEFAULT 'UNSELECTED',
  DROP INDEX uk_openid, ADD UNIQUE KEY uk_openid (openid);
ALTER TABLE usr_family DROP INDEX uk_invite_code, ADD UNIQUE KEY uk_invite_code (invite_code);
ALTER TABLE usr_child_profile DROP INDEX uk_user, ADD UNIQUE KEY uk_user (user_id);
ALTER TABLE usr_family_member
  MODIFY COLUMN guardian_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
  DROP INDEX uk_family_user, ADD UNIQUE KEY uk_family_user (family_id, user_id),
  ADD COLUMN effective_child_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN role = 'CHILD' AND bind_status IN ('PENDING','BOUND') AND delete_at = 0 THEN user_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_effective_child (effective_child_id);
