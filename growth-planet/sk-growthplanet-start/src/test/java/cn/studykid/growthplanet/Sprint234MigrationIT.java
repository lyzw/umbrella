package cn.studykid.growthplanet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Runs one-time incremental DDL only against BaseIT's isolated MySQL container. */
@TestPropertySource(properties = "spring.sql.init.schema-locations=classpath:sprint1_schema.sql")
class Sprint234MigrationIT extends BaseIT {
    private static final List<String> BASE_TABLES = List.of(
            "usr_user", "usr_family", "usr_family_member", "usr_child_profile",
            "usr_consent_log", "sys_audit_log", "sys_notice", "sys_privacy_request");
    private static final List<String> NEW_TABLES = List.of(
            "life_wallet", "life_allowance_rule", "life_allowance_log",
            "life_dish_category", "life_dish", "life_menu_daily",
            "life_menu_confirm", "life_menu_item", "life_confirm_approval",
            "sys_notice_subscription", "sys_privacy_verification");

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void incrementalScriptsPreserveHistoryAndEnforceDurableKeys() {
        assertEquals(BASE_TABLES.stream().sorted().toList(), tables());
        Map<String, List<String>> oldColumns = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> history = new LinkedHashMap<>();
        seedSprint1History();
        for (String table : BASE_TABLES) {
            List<String> columns = columns(table);
            oldColumns.put(table, columns);
            history.put(table, rows(table, columns));
        }

        // DDL commits implicitly: execute the full chain once, not once per assertion/test.
        executeScript("sprint2_schema.sql");
        executeScript("sprint3_catalog.sql");
        executeScript("sprint3_confirmation.sql");
        executeScript("sprint4_schema.sql");

        assertEquals(Stream.concat(BASE_TABLES.stream(), NEW_TABLES.stream()).sorted().toList(), tables());
        for (String table : BASE_TABLES) {
            assertEquals(history.get(table), rows(table, oldColumns.get(table)), table + " history changed");
        }
        for (String table : NEW_TABLES) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class), table);
        }
        assertAddedColumns(oldColumns, "usr_child_profile", List.of("favorite_dish_ids"));
        assertAddedColumns(oldColumns, "sys_notice",
                List.of("read_at", "attempt_count", "last_attempt_at", "next_retry_at", "last_error"));
        assertAddedColumns(oldColumns, "sys_privacy_request", List.of("operator_id", "evidence_ref", "version"));
        assertBackfillDefaults();
        assertWalletConstraints();
        assertCatalogConstraints();
        assertConfirmationConstraints();
        assertNoticeAndPrivacyConstraints();
    }

    private void seedSprint1History() {
        jdbc.update("""
                INSERT INTO usr_user (id,openid,role,token_version,delete_at) VALUES
                (1,'migration_parent','PARENT',7,0),
                (2,'migration_child','CHILD',3,0),
                (3,'migration_deleted','CHILD',9,100)
                """);
        jdbc.update("""
                INSERT INTO usr_family (id,family_name,owner_user_id,invite_code)
                VALUES (1,'migration_family',1,'MIG234')
                """);
        jdbc.update("""
                INSERT INTO usr_family_member
                (id,family_id,user_id,role,bind_status,guardian_status,application_version,delete_at) VALUES
                (1,1,1,'PARENT','BOUND','SELF_ATTESTED',2,0),
                (2,1,2,'CHILD','BOUND','SELF_ATTESTED',4,0),
                (3,1,3,'CHILD','UNBOUND','UNVERIFIED',6,100)
                """);
        jdbc.update("""
                INSERT INTO usr_child_profile
                (id,user_id,family_id,nickname,allergies,dislikes,tastes,profile_status,delete_at) VALUES
                (1,2,1,'active','["PEANUT"]','["carrot"]','["mild"]','COMPLETE',0),
                (2,3,1,'deleted',NULL,NULL,NULL,'INCOMPLETE',100)
                """);
        jdbc.update("""
                INSERT INTO usr_consent_log
                (id,user_id,child_id,family_id,apply_id,application_version,consent_type,action,version,
                 guardian_status,signed_at,delete_at) VALUES
                (1,1,2,1,2,4,'PROFILE','GRANT','v1','SELF_ATTESTED',1000,0),
                (2,1,3,1,NULL,NULL,'PROFILE','REVOKE','v1','UNVERIFIED',2000,100)
                """);
        jdbc.update("""
                INSERT INTO sys_audit_log (id,actor_user_id,family_id,action,target_id,request_id,result,delete_at)
                VALUES (1,1,1,'GRANT',2,'migration-grant','SUCCESS',0),
                       (2,1,1,'REVOKE',3,'migration-revoke','SUCCESS',100)
                """);
        jdbc.update("""
                INSERT INTO sys_notice
                (id,event_key,receiver_id,family_id,child_id,channel,status,event_type,delete_at) VALUES
                (1,'migration-event',1,1,2,'IN_APP','PENDING','GRANT',0),
                (2,'migration-event',1,1,2,'SUBSCRIBE','UNAUTHORIZED','GRANT',0),
                (3,'migration-old-event',1,1,3,'SUBSCRIBE','UNAUTHORIZED','REVOKE',100)
                """);
        jdbc.update("""
                INSERT INTO sys_privacy_request
                (id,requester_id,child_id,family_id,request_type,idempotency_key,request_hash,status,
                 due_at,verified_at,result_ref,expires_at,error_code,delete_at) VALUES
                (1,1,2,1,'EXPORT','migration-export',REPEAT('a',64),'COMPLETED',
                 5000,1000,'opaque-export-ref',6000,NULL,0),
                (2,1,3,1,'DELETE','migration-delete',REPEAT('b',64),'RECEIVED',
                 7000,NULL,NULL,NULL,'REVIEW_REQUIRED',100)
                """);
    }

    private void assertBackfillDefaults() {
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM usr_child_profile
                WHERE JSON_TYPE(favorite_dish_ids)='ARRAY' AND JSON_LENGTH(favorite_dish_ids)=0
                """, Integer.class));
        assertEquals(3, jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_notice WHERE read_at IS NULL AND attempt_count=0
                AND last_attempt_at IS NULL AND next_retry_at IS NULL AND last_error IS NULL
                """, Integer.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_notice WHERE channel='SUBSCRIBE' AND status='UNAUTHORIZED'
                """, Integer.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_privacy_request
                WHERE operator_id IS NULL AND evidence_ref IS NULL AND version=0
                """, Integer.class));
        jdbc.update("INSERT INTO usr_child_profile (id,user_id,family_id) VALUES (3,4,1)");
        assertEquals("[]", jdbc.queryForObject(
                "SELECT CAST(favorite_dish_ids AS CHAR) FROM usr_child_profile WHERE id=3", String.class));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE usr_child_profile SET favorite_dish_ids=NULL WHERE id=3"));
    }

    private void assertWalletConstraints() {
        jdbc.update("INSERT INTO life_wallet (id,child_id,family_id) VALUES (1,2,1)");
        assertEquals("0.00", jdbc.queryForObject(
                "SELECT CAST(balance AS CHAR) FROM life_wallet WHERE id=1", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT version FROM life_wallet WHERE id=1", Integer.class));
        assertReservedKey("life_wallet", 1, "INSERT INTO life_wallet (child_id,family_id) VALUES (2,2)");
        assertCheckViolation("UPDATE life_wallet SET balance=-0.01 WHERE id=1");

        String rule = """
                INSERT INTO life_allowance_rule (child_id,family_id,daily_period,weekly_period)
                VALUES (2,1,'2026-09-18','2026-09-14')
                """;
        jdbc.update(rule);
        assertReservedKey("life_allowance_rule", 1, rule);
        assertCheckViolation("UPDATE life_allowance_rule SET single_limit=31 WHERE id=1");
        assertCheckViolation("UPDATE life_allowance_rule SET daily_used=-1 WHERE id=1");

        String grant = """
                INSERT INTO life_allowance_log
                (wallet_id,child_id,family_id,operator_id,trans_type,scene,amount,
                 balance_before,balance_after,wallet_version,usage_date,request_key,request_hash)
                VALUES (1,2,1,1,'GRANT','MANUAL_GRANT',10,0,10,1,'2026-09-18','grant-key',REPEAT('c',64))
                """;
        jdbc.update(grant);
        assertReservedKey("life_allowance_log", 1, grant);
        String debit = """
                INSERT INTO life_allowance_log
                (wallet_id,child_id,family_id,operator_id,trans_type,scene,ref_id,amount,
                 balance_before,balance_after,wallet_version,usage_date)
                VALUES (1,2,1,1,'DEDUCT','MENU_CONFIRM',10,5,10,5,2,'2026-09-18')
                """;
        jdbc.update(debit);
        long debitId = jdbc.queryForObject(
                "SELECT id FROM life_allowance_log WHERE ref_id=10", Long.class);
        assertReservedKey("life_allowance_log", debitId, debit);
        assertCheckViolation("UPDATE life_allowance_log SET ref_id=NULL WHERE id=?", debitId);
    }

    private void assertCatalogConstraints() {
        jdbc.update("INSERT INTO life_dish_category (id,name) VALUES (1,'migration category')");
        jdbc.update("""
                INSERT INTO life_dish (id,category_id,name,virtual_price,spice_level)
                VALUES (1,1,'migration dish',12.30,0)
                """);
        assertEquals("UNKNOWN", jdbc.queryForObject(
                "SELECT allergen_status FROM life_dish WHERE id=1", String.class));
        assertEquals("12.30", jdbc.queryForObject(
                "SELECT CAST(virtual_price AS CHAR) FROM life_dish WHERE id=1", String.class));
        assertEquals("[]", jdbc.queryForObject(
                "SELECT CAST(allergens AS CHAR) FROM life_dish WHERE id=1", String.class));
        assertCheckViolation("UPDATE life_dish SET virtual_price=-1 WHERE id=1");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE life_dish SET category_id=999 WHERE id=1"));
        String menu = """
                INSERT INTO life_menu_daily (source_type,owner_key,family_id,menu_date,meal_type,dish_ids)
                VALUES ('FAMILY','1',1,'2026-09-18','LUNCH','[1]')
                """;
        jdbc.update(menu);
        assertReservedKey("life_menu_daily", 1, menu);
        assertCheckViolation("UPDATE life_menu_daily SET owner_key='2' WHERE id=1");
        assertCheckViolation("UPDATE life_menu_daily SET dish_ids='[]' WHERE id=1");
    }

    private void assertConfirmationConstraints() {
        String confirmation = """
                INSERT INTO life_menu_confirm
                (confirm_no,child_id,family_id,menu_id,menu_date,meal_type,total_amount,request_key,
                 request_hash,estimated_balance,submit_time)
                VALUES (?,2,1,1,'2026-09-18','LUNCH',12.30,?,REPEAT('d',64),20,'2026-09-18 12:00:00')
                """;
        jdbc.update(confirmation, "migration-confirm", "confirm-key");
        // Vary the other unique key so each constraint is independently exercised.
        assertReservedKey("life_menu_confirm", 1, confirmation, "migration-confirm", "different-key");
        jdbc.update("UPDATE life_menu_confirm SET delete_at=0 WHERE id=1");
        assertReservedKey("life_menu_confirm", 1, confirmation, "different-confirm", "confirm-key");
        assertCheckViolation("UPDATE life_menu_confirm SET status='APPROVED' WHERE id=1");
        assertNull(jdbc.queryForObject(
                "SELECT completed_wallet_version FROM life_menu_confirm WHERE id=1", Integer.class));

        String item = """
                INSERT INTO life_menu_item (confirm_id,dish_id,dish_name,quantity,unit_price,subtotal)
                VALUES (1,1,'snapshot name',1,12.30,12.30)
                """;
        jdbc.update(item);
        assertReservedKey("life_menu_item", 1, item);
        assertCheckViolation("UPDATE life_menu_item SET quantity=10,subtotal=123 WHERE id=1");
        assertCheckViolation("UPDATE life_menu_item SET subtotal=1 WHERE id=1");
        String approval = """
                INSERT INTO life_confirm_approval
                (confirm_id,parent_id,action,before_status,after_status,is_over_limit)
                VALUES (1,1,'REJECT','PENDING','REJECTED',0)
                """;
        jdbc.update(approval);
        assertReservedKey("life_confirm_approval", 1, approval);
    }

    private void assertNoticeAndPrivacyConstraints() {
        String subscription = """
                INSERT INTO sys_notice_subscription (notice_id,user_id,consent_id,status,authorized_at,expires_at)
                VALUES (2,1,1,'AUTHORIZED',1000,2000)
                """;
        jdbc.update(subscription);
        assertReservedKey("sys_notice_subscription", 1, subscription);
        assertCheckViolation("UPDATE sys_notice SET attempt_count=5 WHERE id=1");
        assertCheckViolation("UPDATE sys_notice SET attempt_count=-1 WHERE id=1");
        String privacy = """
                INSERT INTO sys_privacy_request
                (requester_id,child_id,family_id,request_type,idempotency_key,request_hash,status)
                VALUES (?,2,1,?,?,REPEAT('e',64),'RECEIVED')
                """;
        assertReservedKey("sys_privacy_request", 1, privacy, 1, "DELETE", "migration-export");
        assertThrows(DuplicateKeyException.class,
                () -> jdbc.update(privacy, 1, "EXPORT", "migration-delete"));
        // Different requester, different key case: neither may be blocked by an overbroad index.
        jdbc.update(privacy, 9, "DELETE", "migration-export");
        jdbc.update(privacy, 1, "EXPORT", "Migration-export");
        String verification = """
                INSERT INTO sys_privacy_verification (code_hash,requester_id,request_id,verified_at)
                VALUES (REPEAT('f',64),?,?,1000)
                """;
        jdbc.update(verification, 1, 1);
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(verification, 9, 2));
    }

    private void assertCheckViolation(String sql, Object... args) {
        // CHECK failures can be translated differently; require the actual MySQL constraint error.
        DataAccessException failure = assertThrows(DataAccessException.class, () -> jdbc.update(sql, args));
        SQLException cause = assertInstanceOf(SQLException.class, failure.getMostSpecificCause());
        assertEquals(3819, cause.getErrorCode(), sql);
        assertEquals("HY000", cause.getSQLState(), sql);
    }

    private void assertReservedKey(String table, long id, String insert, Object... args) {
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(insert, args), table);
        assertEquals(1, jdbc.update("UPDATE " + table + " SET delete_at=100 WHERE id=?", id));
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(insert, args), table + " deleted key");
    }

    private void assertAddedColumns(Map<String, List<String>> before, String table, List<String> added) {
        assertEquals(Stream.concat(before.get(table).stream(), added.stream()).sorted().toList(), columns(table));
    }

    private List<String> tables() {
        return jdbc.queryForList("""
                SELECT TABLE_NAME FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME
                """, String.class);
    }

    private List<String> columns(String table) {
        return jdbc.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY COLUMN_NAME
                """, String.class, table);
    }

    private List<Map<String, Object>> rows(String table, List<String> columns) {
        return jdbc.queryForList("SELECT " + String.join(",", columns) + " FROM " + table + " ORDER BY id");
    }

    private void executeScript(String name) {
        new ResourceDatabasePopulator(new FileSystemResource("sql/" + name)).execute(dataSource);
    }
}
