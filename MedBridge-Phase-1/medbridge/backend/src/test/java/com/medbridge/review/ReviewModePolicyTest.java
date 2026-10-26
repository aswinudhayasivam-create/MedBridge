package com.medbridge.review;

import com.medbridge.config.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewModePolicyTest {
    static final Instant BEFORE=Instant.parse("2026-10-25T06:00:00Z");
    static String secret(){return UUID.randomUUID().toString()+UUID.randomUUID();}
    static MockEnvironment valid(){
        MockEnvironment env=new MockEnvironment();env.setActiveProfiles("review");
        Map.ofEntries(Map.entry("app.review.enabled","true"),Map.entry("app.review.purpose",ReviewModePolicy.PURPOSE),
            Map.entry("app.review.transport","loopback-test"),Map.entry("app.review.expires-at",ReviewModePolicy.HARD_STOP.toString()),
            Map.entry("server.address","127.0.0.1"),Map.entry("app.frontend-url","http://127.0.0.1:5174"),
            Map.entry("spring.datasource.url",ReviewModePolicy.LOCAL_URL),Map.entry("spring.datasource.username","mb_review_test"),
            Map.entry("spring.datasource.driver-class-name","com.mysql.cj.jdbc.Driver"),Map.entry("spring.jpa.hibernate.ddl-auto","none"),
            Map.entry("spring.sql.init.mode","never")).forEach(env::setProperty);
        for(String key:List.of("app.review.access-key","app.jwt.secret","app.review.donor-password","app.review.ngo-password","app.review.pending-password","app.review.admin-password","spring.datasource.password"))env.setProperty(key,secret());
        return env;
    }
    static ReviewModePolicy policy(MockEnvironment env){return new ReviewModePolicy(env,Clock.fixed(BEFORE,ZoneOffset.UTC));}
    @Test void requiresDatabaseProofAndKeepsAllOriginalIdentitiesLocalOnly(){
        var env=valid();var policy=policy(env);assertDoesNotThrow(policy::requireConfiguration);assertFalse(policy.ready());
        policy.markDatabaseVerified();assertTrue(policy.ready());
        var identities=new DemoIdentityPolicy(env,policy);
        for(String name:List.of("meera","divya","pending","admin"))assertFalse(identities.allows(name.toUpperCase(Locale.ROOT)+"@MedBridge.Test"));
        assertTrue(identities.allows("donor@review.medbridge.invalid"));assertFalse(identities.allows("person@example.invalid"));
        assertFalse(new DemoIdentityPolicy(new MockEnvironment()).allows("donor@review.medbridge.invalid"));
    }
    @ParameterizedTest @CsvSource({"app.review.enabled,false","app.review.purpose,wrong","app.review.access-key,short",
        "app.review.admin-password,DemoAdmin2026!","app.jwt.secret,short","app.allow-registration,true","app.demo-workflows,true",
        "server.address,0.0.0.0","spring.sql.init.mode,always","spring.jpa.hibernate.ddl-auto,update",
        "spring.datasource.username,production","spring.datasource.url,jdbc:mysql://unreachable.invalid/production",
        "spring.datasource.hikari.jdbc-url,jdbc:mysql://unreachable.invalid/production","app.frontend-url,http://example.invalid",
        "app.review.expires-at,2026-10-27T00:00:01Z"})
    void incorrectSettingsFailClosed(String key,String value){var env=valid().withProperty(key,value);assertThrows(IllegalStateException.class,policy(env)::requireConfiguration);assertFalse(policy(env).ready());}
    @Test void profileFlagMissingConfigurationAndMixedProfilesNeverSuffice(){
        for(MockEnvironment env:List.of(new MockEnvironment(),new MockEnvironment().withProperty("app.review.enabled","true"),valid())){
            if(env.getActiveProfiles().length>0)env.setActiveProfiles("review","production");
            assertThrows(IllegalStateException.class,policy(env)::requireConfiguration);
        }
    }
    @Test void cutoffIsExactlyMidnightAfterOctober26InKolkata(){
        assertEquals(Instant.parse("2026-10-26T18:30:00Z"),ReviewModePolicy.HARD_STOP);
        for(Instant time:List.of(ReviewModePolicy.HARD_STOP,ReviewModePolicy.HARD_STOP.plusSeconds(1)))assertThrows(IllegalStateException.class,()->new ReviewModePolicy(valid(),Clock.fixed(time,ZoneOffset.UTC)).requireConfiguration());
    }
    @Test void hostedTransportRequiresHostnameVerifyingTlsAndExactOrigin(){
        var env=valid().withProperty("app.review.transport","hosted-tls").withProperty("server.address","0.0.0.0")
            .withProperty("app.review.database-host","fictional-review.aivencloud.com")
            .withProperty("spring.datasource.url","jdbc:mysql://fictional-review.aivencloud.com:12345/"+ReviewModePolicy.DATABASE+"?sslMode=VERIFY_IDENTITY&serverTimezone=UTC")
            .withProperty("app.frontend-url","https://fictional-review.example.invalid");
        assertThrows(IllegalStateException.class,policy(env)::requireConfiguration);
        env.setProperty("spring.datasource.url",env.getProperty("spring.datasource.url").replace("VERIFY_IDENTITY","REQUIRED"));
        assertThrows(IllegalStateException.class,policy(env)::requireConfiguration);
    }
    @Test void startupRefusesInvalidReviewBeforeCreatingAnyDataSource(){
        AtomicInteger connections=new AtomicInteger();
        try(var context=new AnnotationConfigApplicationContext()){
            context.registerBean("testPolicy",ReviewModePolicy.class,()->policy(valid().withProperty("server.address","0.0.0.0")), definition -> definition.setPrimary(true));
            context.registerBean("dataSource",Object.class,()->{connections.incrementAndGet();return new Object();});
            context.register(ReviewConfiguration.class);assertThrows(RuntimeException.class,context::refresh);assertEquals(0,connections.get());
        }
    }
    @Test void privateInvitationRateLimitAndExpiryAreServerEnforced()throws Exception{
        var env=valid();var policy=policy(env);policy.markDatabaseVerified();var filter=new ReviewAccessFilter(policy);
        var missing=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("POST","/api/auth/login"),missing,new MockFilterChain());assertEquals(401,missing.getStatus());
        for(int i=0;i<10;i++){
            var request=new MockHttpServletRequest("POST","/api/auth/login");request.addHeader("X-Forwarded-For","198.51.100."+i);
            var response=new MockHttpServletResponse();filter.doFilter(request,response,new MockFilterChain());
            if(i==9)assertEquals(429,response.getStatus());
        }
        var request=new MockHttpServletRequest("GET","/api/demo/summary");request.addHeader("X-MedBridge-Review-Key",env.getProperty("app.review.access-key"));
        var accepted=new MockHttpServletResponse();filter.doFilter(request,accepted,new MockFilterChain());assertEquals(200,accepted.getStatus());
        env.setProperty("app.review.expires-at",BEFORE.toString());var expired=new MockHttpServletResponse();filter.doFilter(request,expired,new MockFilterChain());assertEquals(503,expired.getStatus());
    }
    @Test void databaseProofRejectsProductionRecordsAndAdministrativeGrants(){
        JdbcTemplate db=mock(JdbcTemplate.class);when(db.queryForObject("SELECT DATABASE()",String.class)).thenReturn(ReviewModePolicy.DATABASE);
        when(db.queryForList(anyString())).thenReturn(List.of(Map.of("purpose",ReviewModePolicy.PURPOSE,"schema_version",1,"expires_at",ReviewModePolicy.HARD_STOP.toString())));
        when(db.queryForObject(contains("COUNT"),eq(Long.class))).thenReturn(1L);
        assertThrows(IllegalStateException.class,()->ReviewConfiguration.verifyDatabase(db,policy(valid())));
        when(db.queryForObject(contains("COUNT"),eq(Long.class))).thenReturn(0L);
        when(db.query(eq("SHOW GRANTS"),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<String>>any())).thenReturn(List.of("GRANT ALL PRIVILEGES ON *.* TO 'fixture'@'%'"));
        assertThrows(IllegalStateException.class,()->ReviewConfiguration.verifyDatabase(db,policy(valid())));
    }
}
