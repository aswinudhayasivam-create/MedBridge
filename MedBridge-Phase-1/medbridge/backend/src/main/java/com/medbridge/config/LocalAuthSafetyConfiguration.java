package com.medbridge.config;

import java.util.Arrays;
import java.util.Map;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class LocalAuthSafetyConfiguration {
    public static final String LOCAL_JDBC_URL = "jdbc:mysql://127.0.0.1:13306/medbridge_phase4_dev?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // Static factory: validate before a DataSource or other ordinary singleton is created.
    @Bean
    static BeanFactoryPostProcessor localAuthConnectionGuard(Environment environment) {
        return factory -> {
            boolean local = Arrays.asList(environment.getActiveProfiles()).contains("local-auth");
            boolean registration = environment.getProperty("app.allow-registration", Boolean.class, false);
            if (local || registration) requireIsolated(environment);
        };
    }

    public static void requireIsolated(Environment environment) {
        if (!Arrays.equals(environment.getActiveProfiles(), new String[]{"local-auth"})) {
            throw new IllegalStateException("Registration requires only the isolated local-auth profile.");
        }
        require(environment, "server.address", "127.0.0.1");
        require(environment, "spring.datasource.url", LOCAL_JDBC_URL);
        String hikariUrl = environment.getProperty("spring.datasource.hikari.jdbc-url", "");
        if (!hikariUrl.isBlank() && !LOCAL_JDBC_URL.equals(hikariUrl)) fail();
        for (String key : new String[]{"spring.datasource.jndi-name", "spring.datasource.hikari.data-source-class-name"}) {
            if (!environment.getProperty(key, "").isBlank()) fail();
        }
        Map<String, String> properties = Binder.get(environment).bind(
            "spring.datasource.hikari.data-source-properties", Bindable.mapOf(String.class, String.class)).orElse(Map.of());
        if (!properties.isEmpty()) fail();
        require(environment, "spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        require(environment, "spring.jpa.hibernate.ddl-auto", "none");
        require(environment, "spring.sql.init.schema-locations", "classpath:schema.sql");
        require(environment, "spring.sql.init.data-locations", "optional:classpath:local-auth-no-seed.sql");
    }

    private static void require(Environment environment, String key, String expected) {
        if (!expected.equals(environment.getProperty(key))) fail();
    }

    private static void fail() {
        // Never echo a URL, username, password or token into startup logs.
        throw new IllegalStateException("Local authentication requires the isolated loopback MySQL configuration and no seed data.");
    }
}
