package com.medbridge.review;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/** A separate review boundary; never substitutes for the exact local-auth guard. */
public class ReviewModePolicy {
    public static final String DATABASE = "medbridgereview20261026";
    public static final String PURPOSE = "FICTIONAL_COLLEGE_REVIEW_ONLY";
    public static final Instant HARD_STOP = ZonedDateTime.of(2026, 10, 27, 0, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
    public static final String LOCAL_URL = "jdbc:mysql://127.0.0.1:13307/" + DATABASE + "?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private final Environment env;
    private final Clock clock;
    private volatile boolean databaseVerified;

    @Autowired public ReviewModePolicy(Environment env) { this(env, Clock.systemUTC()); }
    public ReviewModePolicy(Environment env, Clock clock) { this.env = env; this.clock = clock; }
    public boolean requested() { return env.getProperty("app.review.enabled", Boolean.class, false) || Arrays.asList(env.getActiveProfiles()).contains("review"); }
    public Instant now() { return clock.instant(); }
    public String property(String name) { return env.getProperty(name, ""); }
    public Instant expiresAt() { return Instant.parse(property("app.review.expires-at")); }
    public boolean isReviewIdentity(String email) { return email != null && email.trim().toLowerCase(Locale.ROOT).endsWith("@review.medbridge.invalid"); }
    public boolean allowsIdentity(String email) {
        if (!isReviewIdentity(email)) return !requested();
        return ready();
    }
    public boolean ready() {
        try { requireConfiguration(); return databaseVerified; } catch (RuntimeException denied) { return false; }
    }
    void markDatabaseVerified() { requireConfiguration(); databaseVerified = true; }
    public boolean acceptsKey(String key) {
        return key != null && key.length() <= 160 && MessageDigest.isEqual(
            property("app.review.access-key").getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
    }
    public void requireConfiguration() {
        try {
            require(Arrays.equals(env.getActiveProfiles(), new String[]{"review"}));
            require(env.getProperty("app.review.enabled", Boolean.class, false));
            require(PURPOSE.equals(property("app.review.purpose")));
            require(!env.getProperty("app.allow-registration", Boolean.class, false));
            require(!env.getProperty("app.demo-workflows", Boolean.class, false));
            require(expiresAt().isAfter(now()) && !expiresAt().isAfter(HARD_STOP));
            require("none".equals(property("spring.jpa.hibernate.ddl-auto")) && "never".equals(property("spring.sql.init.mode")));
            require("com.mysql.cj.jdbc.Driver".equals(property("spring.datasource.driver-class-name")));
            for (String name : new String[]{"spring.datasource.hikari.jdbc-url", "spring.datasource.jndi-name", "spring.datasource.hikari.data-source-class-name"}) require(property(name).isBlank());
            var overrides = org.springframework.boot.context.properties.bind.Binder.get(env).bind(
                "spring.datasource.hikari.data-source-properties", org.springframework.boot.context.properties.bind.Bindable.mapOf(String.class,String.class)).orElse(Map.of());
            require(overrides.isEmpty());
            String transport = property("app.review.transport");
            if ("loopback-test".equals(transport)) {
                require(property("spring.datasource.username").matches("mb_review_[a-z0-9_]{1,32}"));
                require(property("spring.datasource.hikari.connection-init-sql").isBlank());
                require("127.0.0.1".equals(property("server.address")));
                require(LOCAL_URL.equals(property("spring.datasource.url")));
                require("http://127.0.0.1:5174".equals(property("app.frontend-url")));
            } else {
                require("hosted-tls".equals(transport) && "0.0.0.0".equals(property("server.address")));
                require(property("spring.datasource.url").startsWith("jdbc:mysql://"));
                URI db = URI.create(property("spring.datasource.url").replaceFirst("^jdbc:", ""));
                require("mysql".equals(db.getScheme()) && db.getUserInfo() == null && db.getFragment() == null);
                require(db.getHost() != null && db.getHost().equals(property("app.review.database-host")));
                TiDbReviewDestination.require(property("spring.datasource.url"), property("app.review.database-host"), property("spring.datasource.username"));
                require("system".equals(property("app.review.tls-trust")));
                require("SET SESSION foreign_key_checks=ON, tidb_txn_mode='pessimistic'".equals(property("spring.datasource.hikari.connection-init-sql")));
                require(("/" + DATABASE).equals(db.getPath()));
                require("sslMode=VERIFY_IDENTITY&serverTimezone=UTC".equals(db.getRawQuery()));
                URI frontend = URI.create(property("app.frontend-url"));
                require("https".equals(frontend.getScheme()) && frontend.getHost() != null && frontend.getUserInfo() == null);
                require(frontend.getRawQuery() == null && frontend.getFragment() == null && frontend.getPath().isEmpty());
            }
            Set<String> secrets = new HashSet<>();
            for (String key : new String[]{"app.review.access-key", "app.jwt.secret", "app.review.donor-password", "app.review.ngo-password", "app.review.pending-password", "app.review.admin-password", "spring.datasource.password"}) {
                String value = property(key);
                require(value.length() >= (key.endsWith("password") ? 24 : 48) && value.length() <= 128);
                require(value.chars().distinct().count() >= 12 && !value.toLowerCase(Locale.ROOT).contains("change_me"));
                require(secrets.add(value));
            }
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Review environment is unavailable or incorrectly isolated.");
        }
    }
    private static void require(boolean value) { if (!value) throw new IllegalStateException(); }
}
