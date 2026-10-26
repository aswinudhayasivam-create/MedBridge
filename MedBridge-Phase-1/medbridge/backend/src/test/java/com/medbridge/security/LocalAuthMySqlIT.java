package com.medbridge.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real MySQL test. No substitute database and no committed account credentials. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local-auth")
class LocalAuthMySqlIT {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;
    @Autowired PasswordEncoder passwords;
    @Autowired ConfigurableEnvironment environment;
    @LocalServerPort int port;

    @Test void isolatedAccountsAndAuthorization() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String donorEmail = "donor-" + suffix + "@example.invalid";
        String ngoEmail = "ngo-" + suffix + "@example.invalid";
        String password = UUID.randomUUID().toString();
        String license = UUID.randomUUID().toString();
        Map<String, Object> donor = new HashMap<>(Map.of("name", "Fictional Donor", "email", donorEmail,
            "password", password, "role", "DONOR"));
        Map<String, Object> ngo = new HashMap<>(Map.of("name", "Fictional NGO Contact", "email", ngoEmail,
            "password", password, "role", "NGO", "orgName", "Fictional Organization", "licenseNo", license,
            "serviceArea", "Fictional Test Area"));
        ngo.put("verified", true); // Public requests cannot approve their own NGO.
        try {
            assertEquals("isolated-local-auth", body(call(HttpMethod.GET, "/api/auth/local-mode", null, null)).path("environment").asText());
            ResponseEntity<String> registered = call(HttpMethod.POST, "/api/auth/register", donor, null);
            assertEquals(201, registered.getStatusCode().value());
            JsonNode donorResponse = body(registered);
            assertNoPassword(donorResponse);
            String donorToken = donorResponse.path("token").asText();
            assertFalse(donorToken.isBlank());
            String stored = database.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, donorEmail);
            assertNotEquals(password, stored);
            assertTrue(passwords.matches(password, stored));
            assertEquals("DONOR", body(call(HttpMethod.GET, "/api/auth/me", null, donorToken)).path("role").asText());
            assertEquals(200, call(HttpMethod.GET, "/api/roles/donor", null, donorToken).getStatusCode().value());
            for (String role : new String[]{"ngo", "admin"}) {
                assertEquals(403, call(HttpMethod.GET, "/api/roles/" + role, null, donorToken).getStatusCode().value());
            }
            ResponseEntity<String> login = call(HttpMethod.POST, "/api/auth/login", Map.of("email", donorEmail, "password", password), null);
            assertEquals(200, login.getStatusCode().value());
            assertNoPassword(body(login));
            assertEquals(donorEmail, body(call(HttpMethod.GET, "/api/auth/me", null, body(login).path("token").asText())).path("email").asText());

            ResponseEntity<String> ngoRegistered = call(HttpMethod.POST, "/api/auth/register", ngo, null);
            assertEquals(201, ngoRegistered.getStatusCode().value());
            JsonNode ngoResponse = body(ngoRegistered);
            assertNoPassword(ngoResponse);
            assertFalse(ngoResponse.path("user").path("verified").asBoolean(true));
            assertEquals(Boolean.FALSE, database.queryForObject("SELECT verified FROM users WHERE email = ?", Boolean.class, ngoEmail));
            assertEquals(license, database.queryForObject("SELECT n.license_no FROM ngo_profiles n JOIN users u ON u.user_id=n.ngo_id WHERE u.email=?", String.class, ngoEmail));
            String ngoToken = ngoResponse.path("token").asText();
            ResponseEntity<String> ngoLogin = call(HttpMethod.POST, "/api/auth/login", Map.of("email", ngoEmail, "password", password), null);
            assertEquals(200, ngoLogin.getStatusCode().value());
            assertNoPassword(body(ngoLogin));
            JsonNode ngoProfile = body(call(HttpMethod.GET, "/api/auth/me", null, ngoToken));
            assertFalse(ngoProfile.path("verified").asBoolean(true));
            assertNoPassword(ngoProfile);
            for (String role : new String[]{"ngo", "donor", "admin"}) {
                assertEquals(403, call(HttpMethod.GET, "/api/roles/" + role, null, ngoToken).getStatusCode().value());
            }
            assertEquals(409, call(HttpMethod.POST, "/api/auth/register", donor, null).getStatusCode().value());
            ngo.put("email", "duplicate-" + suffix + "@example.invalid");
            assertEquals(409, call(HttpMethod.POST, "/api/auth/register", ngo, null).getStatusCode().value());
            donor.put("role", "ADMIN");
            assertEquals(400, call(HttpMethod.POST, "/api/auth/register", donor, null).getStatusCode().value());
            donor.put("role", "DONOR");
            donor.put("email", "invalid-" + suffix + "@example.invalid");
            donor.put("name", "");
            ResponseEntity<String> validation = call(HttpMethod.POST, "/api/auth/register", donor, null);
            assertEquals(400, validation.getStatusCode().value());
            assertTrue(body(validation).path("fields").has("name"));
            donor.put("name", "Fictional Donor");
            assertEquals(401, call(HttpMethod.POST, "/api/auth/login", Map.of("email", donorEmail, "password", UUID.randomUUID().toString()), null).getStatusCode().value());
            assertEquals(401, call(HttpMethod.GET, "/api/auth/me", null, null).getStatusCode().value());
            assertEquals(401, call(HttpMethod.GET, "/api/auth/me", null, UUID.randomUUID().toString()).getStatusCode().value());
            String expired = Jwts.builder().subject(donorEmail).expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(environment.getRequiredProperty("app.jwt.secret").getBytes(StandardCharsets.UTF_8))).compact();
            assertEquals(401, call(HttpMethod.GET, "/api/auth/me", null, expired).getStatusCode().value());

            long before = database.queryForObject("SELECT COUNT(*) FROM users", Long.class);
            environment.getPropertySources().addFirst(new MapPropertySource("disabledRegistrationTest", Map.of("app.allow-registration", "false")));
            try {
                ResponseEntity<String> disabled = call(HttpMethod.POST, "/api/auth/register", donor, null);
                assertEquals(503, disabled.getStatusCode().value());
                assertEquals("Registration is unavailable in this environment.", body(disabled).path("error").asText());
                assertEquals(before, database.queryForObject("SELECT COUNT(*) FROM users", Long.class));
            } finally { environment.getPropertySources().remove("disabledRegistrationTest"); }
        } finally {
            // Delete only this run's generated fictional identities, including failed-write probes.
            for (String prefix : new String[]{"donor-", "ngo-", "duplicate-", "invalid-"}) {
                database.update("DELETE FROM users WHERE email = ?", prefix + suffix + "@example.invalid");
            }
        }
    }

    private ResponseEntity<String> call(HttpMethod method, String path, Object payload, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange("http://127.0.0.1:" + port + path, method, new HttpEntity<>(payload, headers), String.class);
    }
    private JsonNode body(ResponseEntity<String> response) throws Exception { return json.readTree(response.getBody()); }
    private void assertNoPassword(JsonNode value) {
        assertFalse(value.has("password")); assertFalse(value.has("passwordHash")); assertFalse(value.has("password_hash"));
        if (value.has("user")) assertNoPassword(value.get("user"));
    }
}
