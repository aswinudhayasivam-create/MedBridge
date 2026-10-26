package com.medbridge.demo;

import com.medbridge.config.LocalAuthSafetyConfiguration;
import com.medbridge.review.ReviewModePolicy;
import com.medbridge.exception.ApiException;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DemoSafety {
    private final Environment environment;
    private final JdbcTemplate db;
    private final ReviewModePolicy review;
    public DemoSafety(Environment environment,JdbcTemplate db,ReviewModePolicy review){this.environment=environment;this.db=db;this.review=review;}
    public Map<String,Object> actor(String email,String... roles) {
        if(review.requested()) {
            if(!review.ready()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Review access is unavailable.");
            if(!review.isReviewIdentity(email)) throw new ApiException(HttpStatus.UNAUTHORIZED,"Authentication required.");
        } else {
            if(!environment.getProperty("app.demo-workflows",Boolean.class,false)) throw new ApiException(HttpStatus.NOT_FOUND,"Demo workflows are disabled.");
            LocalAuthSafetyConfiguration.requireIsolated(environment);
        }
        var users=db.queryForList("SELECT user_id,name,email,role,verified,latitude,longitude FROM users WHERE email=?",email);
        if(users.size()!=1) throw new ApiException(HttpStatus.UNAUTHORIZED,"Authentication required.");
        var user=users.get(0);
        if(java.util.Arrays.stream(roles).noneMatch(role->role.equals(user.get("role")))) throw new ApiException(HttpStatus.FORBIDDEN,"This role cannot perform that action.");
        return user;
    }
    public Map<String,Object> verifiedNgo(String email){
        var user=actor(email,"NGO");
        if(!Boolean.TRUE.equals(user.get("verified"))) throw new ApiException(HttpStatus.FORBIDDEN,"Demo NGO verification is required.");
        return user;
    }
    public static long id(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
}
