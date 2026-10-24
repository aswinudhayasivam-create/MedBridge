package com.medbridge.review;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods=false)
public class ReviewConfiguration {
    @Bean static ReviewModePolicy reviewModePolicy(org.springframework.core.env.Environment environment) { return new ReviewModePolicy(environment); }
    @Bean static BeanFactoryPostProcessor reviewStartupGuard(ReviewModePolicy policy) {
        return factory -> { if (policy.requested()) policy.requireConfiguration(); };
    }
    @Bean ApplicationRunner reviewProvisioning(ReviewModePolicy policy, JdbcTemplate db, PasswordEncoder passwords, PlatformTransactionManager transactions) {
        return args -> {
            if (!policy.requested()) return;
            policy.requireConfiguration();
            verifyDatabase(db, policy);
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                provision(db,passwords,policy,"donor","Fictional Review Donor","DONOR",false,null);
                provision(db,passwords,policy,"ngo","Fictional Review NGO","NGO",true,"Review GreenCross");
                provision(db,passwords,policy,"pending","Fictional Pending NGO","NGO",false,"Review HopeCare");
                provision(db,passwords,policy,"admin","Fictional Review Administrator","ADMIN",false,null);
            });
            policy.markDatabaseVerified();
        };
    }
    public static void verifyDatabase(JdbcTemplate db, ReviewModePolicy policy) {
        if (!ReviewModePolicy.DATABASE.equals(db.queryForObject("SELECT DATABASE()", String.class))) fail();
        var markers=db.queryForList("SELECT purpose,schema_version,expires_at FROM review_environment WHERE environment_id='medbridge-review-20261026'");
        if (markers.size()!=1) fail();
        var marker=markers.get(0);
        if (!ReviewModePolicy.PURPOSE.equals(marker.get("purpose")) || ((Number)marker.get("schema_version")).intValue()!=1 || !ReviewModePolicy.HARD_STOP.equals(Instant.parse((String)marker.get("expires_at")))) fail();
        if (db.queryForObject("SELECT COUNT(*) FROM users WHERE LOWER(TRIM(email)) NOT LIKE '%@review.medbridge.invalid'", Long.class)!=0) fail();
        boolean scopedGrant=false;
        for (String grant : db.query("SHOW GRANTS", (rs,row)->rs.getString(1))) {
            String normalized=grant.toUpperCase(Locale.ROOT).replace("`", "");
            if (normalized.contains("WITH GRANT OPTION")) fail();
            if (normalized.startsWith("GRANT USAGE ON *.* TO ")) continue;
            String[] parts=normalized.split(" ON ",2);
            if(parts.length!=2 || !parts[1].startsWith(ReviewModePolicy.DATABASE.toUpperCase(Locale.ROOT)+".* TO ")) fail();
            Set<String> privileges=new HashSet<>(Arrays.asList(parts[0].substring("GRANT ".length()).split(", ")));
            if (!Set.of("SELECT","INSERT","UPDATE","DELETE").containsAll(privileges) || privileges.isEmpty()) fail();
            scopedGrant=true;
        }
        if (!scopedGrant) fail();
    }
    private static void fail() { throw new IllegalStateException("Review database isolation proof failed."); }
    private void provision(JdbcTemplate db, PasswordEncoder encoder, ReviewModePolicy policy, String key, String name, String role, boolean verified, String org) {
        String email=key+"@review.medbridge.invalid";
        if(db.queryForObject("SELECT COUNT(*) FROM users WHERE email=?",Long.class,email)>0) return;
        db.update("INSERT INTO users(name,email,password_hash,role,verified,latitude,longitude) VALUES(?,?,?,?,?,13.0000,80.2500)", name,email,encoder.encode(policy.property("app.review."+key+"-password")),role,verified);
        Long id=db.queryForObject("SELECT user_id FROM users WHERE email=?",Long.class,email);
        if(org!=null) {
            db.update("INSERT INTO ngo_profiles(ngo_id,org_name,license_no,service_area) VALUES(?,?,?,'Fictional review zone')",id,org,"REVIEW-"+key.toUpperCase(Locale.ROOT));
            db.update("INSERT INTO demo_ngo_applications(ngo_id,status) VALUES(?,?)",id,verified?"APPROVED":"PENDING");
        }
    }
}
