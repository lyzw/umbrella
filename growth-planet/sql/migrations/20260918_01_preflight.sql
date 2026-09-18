-- 只读预检：停写后执行。以下查询必须全部返回空结果，否则人工处置后重做。
SELECT openid, COUNT(*) AS duplicate_count FROM usr_user GROUP BY openid HAVING COUNT(*) > 1;
SELECT invite_code, COUNT(*) AS duplicate_count FROM usr_family
WHERE invite_code IS NOT NULL GROUP BY invite_code HAVING COUNT(*) > 1;
SELECT family_id, user_id, COUNT(*) AS duplicate_count FROM usr_family_member
GROUP BY family_id, user_id HAVING COUNT(*) > 1;
SELECT user_id, COUNT(*) AS duplicate_count FROM usr_child_profile GROUP BY user_id HAVING COUNT(*) > 1;
SELECT user_id, COUNT(*) AS active_count FROM usr_family_member
WHERE role = 'CHILD' AND bind_status IN ('PENDING','APPROVED','BOUND') AND delete_at = 0
GROUP BY user_id HAVING COUNT(*) > 1;
SELECT user_id, COUNT(*) AS family_count FROM usr_family_member
WHERE role = 'PARENT' AND bind_status IN ('APPROVED','BOUND') AND delete_at = 0
GROUP BY user_id HAVING COUNT(*) > 1;
SELECT m.id FROM usr_family_member m
LEFT JOIN usr_user u ON u.id = m.user_id
LEFT JOIN usr_family f ON f.id = m.family_id
WHERE u.id IS NULL OR f.id IS NULL OR m.role <> u.role;
SELECT p.id FROM usr_child_profile p
LEFT JOIN usr_user u ON u.id = p.user_id
LEFT JOIN usr_family f ON f.id = p.family_id
WHERE u.id IS NULL OR f.id IS NULL;
SELECT id, role FROM usr_user WHERE role NOT IN ('UNSET','UNSELECTED','CHILD','PARENT','ADMIN');
SELECT id, bind_status FROM usr_family_member
WHERE bind_status NOT IN ('PENDING','APPROVED','BOUND','REJECTED');
