package cn.studykid.growthplanet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 仅在 BaseIT 创建的隔离容器中重建旧六表结构，不能用于业务库。 */
class MigrationIT extends BaseIT {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void legacyMigrationPreservesHistoryAndMatchesFreshSchema() {
        List<Map<String, Object>> expectedColumns = columns();
        List<Map<String, Object>> expectedIndexes = indexes();
        jdbc.execute("DROP TABLE sys_notice, sys_privacy_request");
        jdbc.execute("ALTER TABLE usr_user DROP COLUMN token_version, "
                + "MODIFY role VARCHAR(16) NOT NULL DEFAULT 'UNSET', "
                + "DROP INDEX uk_openid, ADD UNIQUE KEY uk_openid (openid, delete_at)");
        jdbc.execute("ALTER TABLE usr_family DROP INDEX uk_invite_code, "
                + "ADD UNIQUE KEY uk_invite_code (invite_code, delete_at)");
        jdbc.execute("ALTER TABLE usr_family_member DROP INDEX uk_effective_child, "
                + "DROP COLUMN effective_child_id, DROP COLUMN application_version, "
                + "MODIFY guardian_status VARCHAR(16) NOT NULL DEFAULT 'PENDING', "
                + "DROP INDEX uk_family_user, ADD UNIQUE KEY uk_family_user (family_id, user_id, delete_at)");
        jdbc.execute("ALTER TABLE usr_child_profile DROP INDEX uk_user, "
                + "ADD UNIQUE KEY uk_user (user_id, delete_at)");
        jdbc.execute("ALTER TABLE usr_consent_log DROP INDEX idx_consent_scope, "
                + "DROP COLUMN apply_id, DROP COLUMN application_version, MODIFY self_reported_age TINYINT");
        jdbc.execute("ALTER TABLE sys_audit_log DROP INDEX idx_request, "
                + "DROP COLUMN request_id, DROP COLUMN result, DROP COLUMN error_code");
        jdbc.update("INSERT INTO usr_user (id, openid, role) VALUES "
                + "(1,'fixture_parent','PARENT'), (2,'fixture_child','CHILD'), (3,'fixture_unselected','UNSET')");
        jdbc.update("INSERT INTO usr_family (id,family_name,owner_user_id,invite_code) VALUES (1,'fixture',1,'ABC123')");
        jdbc.update("INSERT INTO usr_family_member (id,family_id,user_id,role,bind_status,guardian_status) VALUES "
                + "(1,1,1,'PARENT','APPROVED','APPROVED'), (2,1,2,'CHILD','APPROVED','APPROVED')");
        jdbc.update("INSERT INTO usr_child_profile (user_id,family_id,profile_status) VALUES (2,1,'COMPLETED')");
        jdbc.update("INSERT INTO usr_consent_log "
                + "(user_id,child_id,family_id,consent_type,action,version,guardian_status) "
                + "VALUES (1,2,1,'PROFILE','GRANT','v1','APPROVED')");
        jdbc.update("INSERT INTO sys_audit_log (action,target_id) VALUES ('GRANT',1)");

        executeScript("20260918_01_preflight.sql");
        executeScript("20260918_02_expand.sql");
        executeScript("20260918_03_cutover.sql");

        assertEquals(expectedColumns, columns());
        assertEquals(expectedIndexes, indexes());
        assertEquals("UNSELECTED", jdbc.queryForObject("SELECT role FROM usr_user WHERE id=3", String.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM usr_user WHERE token_version=1", Integer.class));
        assertEquals("BOUND", jdbc.queryForObject("SELECT bind_status FROM usr_family_member WHERE id=1", String.class));
        assertEquals("PENDING", jdbc.queryForObject("SELECT bind_status FROM usr_family_member WHERE id=2", String.class));
        assertEquals(2, jdbc.queryForObject("SELECT application_version FROM usr_family_member WHERE id=2", Integer.class));
        assertEquals("INCOMPLETE", jdbc.queryForObject("SELECT profile_status FROM usr_child_profile", String.class));
        assertNull(jdbc.queryForObject("SELECT apply_id FROM usr_consent_log", Long.class));
        assertEquals("APPROVED", jdbc.queryForObject("SELECT guardian_status FROM usr_consent_log", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log", Integer.class));

        jdbc.update("UPDATE usr_user SET delete_at=1 WHERE id=3");
        assertThrows(DuplicateKeyException.class,
                () -> jdbc.update("INSERT INTO usr_user (openid) VALUES ('fixture_unselected')"));
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                "INSERT INTO usr_family_member (family_id,user_id,role,bind_status) VALUES (2,2,'CHILD','PENDING')"));
    }

    private void executeScript(String name) {
        new ResourceDatabasePopulator(new FileSystemResource("sql/migrations/" + name)).execute(dataSource);
    }

    private List<Map<String, Object>> columns() {
        return jdbc.queryForList("""
                SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT,
                       EXTRA, GENERATION_EXPRESSION, COLLATION_NAME
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                ORDER BY TABLE_NAME, COLUMN_NAME
                """);
    }

    private List<Map<String, Object>> indexes() {
        return jdbc.queryForList("""
                SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                """);
    }
}
