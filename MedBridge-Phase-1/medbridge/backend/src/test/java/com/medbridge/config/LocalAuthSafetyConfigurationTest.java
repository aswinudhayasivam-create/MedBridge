package com.medbridge.config;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

public class LocalAuthSafetyConfigurationTest {
    public static MockEnvironment isolated() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local-auth");
        return environment.withProperty("server.address", "127.0.0.1")
            .withProperty("spring.datasource.url", LocalAuthSafetyConfiguration.LOCAL_JDBC_URL)
            .withProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver")
            .withProperty("spring.jpa.hibernate.ddl-auto", "none")
            .withProperty("spring.sql.init.schema-locations", "classpath:schema.sql")
            .withProperty("spring.sql.init.data-locations", "optional:classpath:local-auth-no-seed.sql");
    }

    @Test void acceptsOnlyTheIsolatedConfiguration() {
        assertDoesNotThrow(() -> LocalAuthSafetyConfiguration.requireIsolated(isolated()));
    }

    @Test void rejectsRemoteAlternateAndSeedConfigurations() {
        String[][] overrides = {
            {"spring.datasource.url", "jdbc:mysql://remote.example.test:3306/production"},
            {"spring.datasource.hikari.jdbc-url", "jdbc:mysql://remote.example.test/production"},
            {"spring.datasource.hikari.data-source-properties.serverName", "remote.example.test"},
            {"spring.datasource.jndi-name", "java:comp/env/production"},
            {"server.address", "0.0.0.0"},
            {"spring.sql.init.data-locations", "classpath:data.sql"},
            {"spring.jpa.hibernate.ddl-auto", "update"}
        };
        for (String[] override : overrides) {
            assertThrows(IllegalStateException.class, () -> LocalAuthSafetyConfiguration.requireIsolated(
                isolated().withProperty(override[0], override[1])), override[0]);
        }
        MockEnvironment mixed = isolated();
        mixed.setActiveProfiles("local-auth", "production");
        assertThrows(IllegalStateException.class, () -> LocalAuthSafetyConfiguration.requireIsolated(mixed));
    }

    @Test void rejectsRemoteConfigurationBeforeDataSourceInstantiation() {
        AtomicInteger connections = new AtomicInteger();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(isolated().withProperty("spring.datasource.url", "jdbc:mysql://remote.example.test/production"));
            context.register(LocalAuthSafetyConfiguration.class);
            context.registerBean("dataSource", Object.class, () -> { connections.incrementAndGet(); return new Object(); });
            assertThrows(IllegalStateException.class, context::refresh);
            assertEquals(0, connections.get());
        }
    }

    @Test void refusesRegistrationOutsideTheLocalProfileAtStartup() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("app.allow-registration", "true"));
            context.register(LocalAuthSafetyConfiguration.class);
            assertThrows(IllegalStateException.class, context::refresh);
        }
    }
}
