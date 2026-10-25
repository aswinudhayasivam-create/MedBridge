package com.medbridge.review;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.springframework.jdbc.core.JdbcTemplate;

class TiDbReviewSafetyTest {
    static final String HOST="gateway01.us-east-1.prod.aws.tidbcloud.com";
    static final String URL="jdbc:mysql://"+HOST+":4000/"+ReviewModePolicy.DATABASE+"?sslMode=VERIFY_IDENTITY&serverTimezone=UTC";
    static final TiDbReviewDestination.Approval PIN=new TiDbReviewDestination.Approval(HOST,4000,"fixture123");
    @Test void exactDestinationAndPrefixedRestrictedUsernameAreRequired() {
        assertDoesNotThrow(()->TiDbReviewDestination.require(PIN,URL,HOST,"fixture123.mb_review_runtime"));
        for(String user:List.of("root","fixture123.root","mb_review_runtime","other.mb_review_runtime","fixture123.mb_review_x@host"))
            assertThrows(IllegalStateException.class,()->TiDbReviewDestination.require(PIN,URL,HOST,user));
    }
    @ParameterizedTest @ValueSource(strings={"REQUIRED","DISABLED","VERIFY_CA"})
    void weakerTlsFails(String ssl) { assertThrows(IllegalStateException.class,()->TiDbReviewDestination.require(PIN,URL.replace("VERIFY_IDENTITY",ssl),HOST,"fixture123.mb_review_runtime")); }
    @Test void arbitraryUrlsAndUnapprovedBuildCannotActivateHostedReview() {
        for(String url:List.of(URL.replace(HOST,"other.tidbcloud.com"),URL.replace(":4000",":3306"),URL.replace(ReviewModePolicy.DATABASE,"defaultdb"),URL+"&allowPublicKeyRetrieval=true",URL+"#fragment",URL.replace("jdbc:mysql://","jdbc:mysql://root@")))
            assertThrows(IllegalStateException.class,()->TiDbReviewDestination.require(PIN,url,HOST,"fixture123.mb_review_runtime"));
        assertThrows(IllegalStateException.class,()->TiDbReviewDestination.require(new TiDbReviewDestination.Approval("",4000,""),URL,HOST,"fixture123.mb_review_runtime"));
    }
    @Test void tidbAndMysqlFormattingAcceptsOnlyCompleteDatabaseDml() {
        assertDoesNotThrow(()->ReviewConfiguration.requireRestrictedGrants(List.of(
            "GRANT USAGE ON *.* TO 'fixture123.mb_review_runtime'@'%'",
            "GRANT Select,Insert,Update,Delete ON `medbridgereview20261026`.* TO 'fixture123.mb_review_runtime'@'%'")));
        assertDoesNotThrow(()->ReviewConfiguration.requireRestrictedGrants(List.of(
            "GRANT USAGE ON *.* TO `mb_review_fixture`@`%`",
            "GRANT SELECT, INSERT, UPDATE, DELETE ON `medbridgereview20261026`.* TO `mb_review_fixture`@`%`")));
        assertDoesNotThrow(()->ReviewConfiguration.requireRestrictedGrants(List.of(
            "GRANT SELECT, INSERT ON medbridgereview20261026.* TO 'fixture'@'%'",
            "GRANT UPDATE, DELETE ON medbridgereview20261026.* TO 'fixture'@'%'")));
    }
    @ParameterizedTest @ValueSource(strings={
        "GRANT ALL PRIVILEGES ON *.* TO 'fixture'@'%'",
        "GRANT SELECT, INSERT, UPDATE, DELETE ON medbridgereview20261026.* TO 'fixture'@'%' WITH GRANT OPTION",
        "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON medbridgereview20261026.* TO 'fixture'@'%'",
        "GRANT SELECT, INSERT, UPDATE, DELETE ON other.* TO 'fixture'@'%'",
        "GRANT SELECT ON medbridgereview20261026.* TO 'fixture'@'%'",
        "GRANT SELECT, INSERT, UPDATE, DELETE ON *.* TO 'fixture'@'%'",
        "GRANT 'role_admin'@'%' TO 'fixture'@'%'"})
    void rolesGlobalDdlAndIncompletePermissionsFailClosed(String grant) {
        assertThrows(IllegalStateException.class,()->ReviewConfiguration.requireRestrictedGrants(List.of(grant)));
    }
    @Test void emptyGrantsFailClosed() { assertThrows(IllegalStateException.class,()->ReviewConfiguration.requireRestrictedGrants(List.of())); }
    JdbcTemplate validDatabase() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        when(db.queryForObject("SELECT DATABASE()",String.class)).thenReturn(ReviewModePolicy.DATABASE);
        when(db.queryForList(anyString())).thenReturn(List.of(Map.of("purpose",ReviewModePolicy.PURPOSE,"schema_version",1,"expires_at",ReviewModePolicy.HARD_STOP.toString())));
        when(db.queryForObject(contains("COUNT"),eq(Long.class))).thenReturn(0L);
        when(db.query(eq("SHOW GRANTS"),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<String>>any())).thenReturn(List.of("GRANT SELECT,INSERT,UPDATE,DELETE ON medbridgereview20261026.* TO 'fixture123.mb_review_runtime'@'%'"));
        when(db.queryForObject("SELECT VERSION()",String.class)).thenReturn("8.0.11-TiDB-fixture");
        when(db.queryForObject("SELECT @@foreign_key_checks",Integer.class)).thenReturn(1);
        when(db.queryForObject("SELECT @@GLOBAL.tidb_enable_check_constraint",Integer.class)).thenReturn(1);
        when(db.queryForObject("SELECT @@tidb_txn_mode",String.class)).thenReturn("pessimistic");
        when(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Long.class)).thenReturn(11L);
        when(db.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND constraint_type='CHECK' AND enforced='YES'",Long.class)).thenReturn(4L);
        when(db.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()",Long.class)).thenReturn(11L);
        return db;
    }
    @Test void hostedDatabaseProofRequiresRealConstraintAndTransactionCapabilities() {
        ReviewModePolicy policy=mock(ReviewModePolicy.class);
        when(policy.property("app.review.transport")).thenReturn("hosted-tls");
        assertDoesNotThrow(()->ReviewConfiguration.verifyDatabase(validDatabase(),policy));
        for(String sql:List.of("SELECT @@foreign_key_checks","SELECT @@GLOBAL.tidb_enable_check_constraint")) {
            JdbcTemplate db=validDatabase();when(db.queryForObject(sql,Integer.class)).thenReturn(0);
            assertThrows(IllegalStateException.class,()->ReviewConfiguration.verifyDatabase(db,policy));
        }
        JdbcTemplate optimistic=validDatabase();when(optimistic.queryForObject("SELECT @@tidb_txn_mode",String.class)).thenReturn("optimistic");
        assertThrows(IllegalStateException.class,()->ReviewConfiguration.verifyDatabase(optimistic,policy));
        JdbcTemplate mysql=validDatabase();when(mysql.queryForObject("SELECT VERSION()",String.class)).thenReturn("8.4.11");
        assertThrows(IllegalStateException.class,()->ReviewConfiguration.verifyDatabase(mysql,policy));
        for(String sql:List.of("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",
            "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND constraint_type='CHECK' AND enforced='YES'",
            "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()")) {
            JdbcTemplate db=validDatabase();when(db.queryForObject(sql,Long.class)).thenReturn(0L);
            assertThrows(IllegalStateException.class,()->ReviewConfiguration.verifyDatabase(db,policy));
        }
    }

}
