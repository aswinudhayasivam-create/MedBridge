package com.medbridge.review;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("review")
public class ReviewController {
    private final ReviewModePolicy policy;
    private final JdbcTemplate db;
    public ReviewController(ReviewModePolicy policy,JdbcTemplate db){this.policy=policy;this.db=db;}
    @GetMapping("/api/auth/review-mode") public Map<String,Object> capability(){
        if(!policy.ready()) throw new com.medbridge.exception.ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Review access is unavailable.");
        return Map.of("environment","isolated-review","reviewEnabled",true,"registrationEnabled",false,"syntheticOnly",true,"expiresAt",policy.expiresAt().toString());
    }
    @GetMapping("/api/review/ready") public ResponseEntity<Map<String,String>> ready(){
        try { if(policy.ready() && Integer.valueOf(1).equals(db.queryForObject("SELECT 1",Integer.class))) return ResponseEntity.ok(Map.of("status","UP")); }
        catch(RuntimeException unavailable){ /* Do not expose database details. */ }
        return ResponseEntity.status(503).body(Map.of("status","UNAVAILABLE"));
    }
}
