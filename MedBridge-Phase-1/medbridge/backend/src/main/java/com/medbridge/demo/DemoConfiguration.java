package com.medbridge.demo;

import com.medbridge.config.LocalAuthSafetyConfiguration;
import java.util.Map;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods=false)
public class DemoConfiguration {
    @Bean static BeanFactoryPostProcessor demoEnvironmentGuard(Environment environment) {
        return factory -> {
            if (environment.getProperty("app.demo-workflows", Boolean.class, false)) {
                LocalAuthSafetyConfiguration.requireIsolated(environment);
            }
        };
    }
    @Bean ApplicationRunner demoProvisioning(Environment environment, JdbcTemplate db, PasswordEncoder passwords) {
        return args -> {
            if (!environment.getProperty("app.demo-workflows", Boolean.class, false)) return;
            LocalAuthSafetyConfiguration.requireIsolated(environment);
            new ResourceDatabasePopulator(new ClassPathResource("demo-schema.sql")).execute(db.getDataSource());
            // Intentionally public FICTIONAL demo credentials. Never used outside the exact guarded database.
            provision(db,passwords,"Meera K","meera@medbridge.test","DemoDonor2026!","DONOR",false,null,null);
            provision(db,passwords,"Divya Rao","divya@medbridge.test","DemoNgo2026!","NGO",true,"GreenCross Foundation","DEMO-GREEN-2026");
            provision(db,passwords,"HopeCare Contact","pending@medbridge.test","DemoPending2026!","NGO",false,"HopeCare Foundation","DEMO-HOPE-2026");
            provision(db,passwords,"MedBridge Review Team","admin@medbridge.test","DemoAdmin2026!","ADMIN",false,null,null);
        };
    }
    private void provision(JdbcTemplate db, PasswordEncoder encoder, String name, String email, String password,
                           String role, boolean verified, String org, String license) {
        if (db.queryForObject("SELECT COUNT(*) FROM users WHERE email=?",Integer.class,email)>0) return;
        db.update("INSERT INTO users(name,email,password_hash,role,verified,latitude,longitude) VALUES(?,?,?,?,?,13.0000,80.2500)",
            name,email,encoder.encode(password),role,verified);
        Long id=db.queryForObject("SELECT user_id FROM users WHERE email=?",Long.class,email);
        if (org!=null) {
            db.update("INSERT INTO ngo_profiles(ngo_id,org_name,license_no,service_area) VALUES(?,?,?,'Fictional Chennai demo zone')",id,org,license);
            db.update("INSERT INTO demo_ngo_applications(ngo_id,status) VALUES(?,?)",id,verified?"APPROVED":"PENDING");
            if (verified) db.update("INSERT INTO notifications(user_id,type,message) VALUES(?,'DEMO_VERIFIED','Verified for fictional demonstration only; not regulatory clearance.')",id);
        }
    }
}
