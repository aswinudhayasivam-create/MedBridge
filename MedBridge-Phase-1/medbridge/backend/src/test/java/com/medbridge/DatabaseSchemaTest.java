package com.medbridge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
class DatabaseSchemaTest {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("medbridge_auth_test")
        .withUsername("phase4_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
        .withPassword(UUID.randomUUID().toString())
        .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
            new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), new ExposedPort(3306))));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
        String signingSecret = UUID.randomUUID().toString() + UUID.randomUUID();
        registry.add("app.jwt.secret", () -> signingSecret);
        registry.add("app.frontend-url", () -> "http://localhost:5173");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void schemaCreatesRequiredTablesWithoutDemoAccountsOrMedicines() {
        for (String table : new String[]{"users", "ngo_profiles", "medicines", "requests", "notifications"}) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, table);
            assertEquals(1, count, "Missing table: " + table);
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM medicines", Integer.class));
    }
}
