package com.medbridge.security;

import com.medbridge.config.*;
import com.medbridge.dto.LoginRequest;
import com.medbridge.exception.ApiException;
import com.medbridge.model.*;
import com.medbridge.repository.*;
import com.medbridge.service.AuthService;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Hosted-like configuration is data only: no DataSource, HTTP client or remote connection exists here. */
class DemoIdentityAuthenticationTest {
    static final List<String> IDENTITIES = List.of("meera@medbridge.test", "divya@medbridge.test",
        "pending@medbridge.test", "admin@medbridge.test");
    static final BCryptPasswordEncoder PASSWORDS = new BCryptPasswordEncoder(4);
    static final String PASSWORD = UUID.randomUUID().toString();
    static final String HASH = PASSWORDS.encode(PASSWORD);
    final UserRepository users = mock(UserRepository.class);
    final NgoProfileRepository ngos = mock(NgoProfileRepository.class);
    // Random test-only key. Tokens are deliberately never logged or persisted.
    final JwtService jwt = new JwtService(UUID.randomUUID().toString() + UUID.randomUUID(), 3600);

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    static MockEnvironment isolated() { return LocalAuthSafetyConfigurationTest.isolated(); }
    static MockEnvironment blocked(String scenario) {
        MockEnvironment env = isolated();
        switch (scenario) {
            case "disabled" -> env.setActiveProfiles();
            case "profile-only" -> { env = new MockEnvironment(); env.setActiveProfiles("local-auth"); }
            case "flag-only" -> env = new MockEnvironment().withProperty("app.demo-workflows", "true");
            case "remote-database" -> env.setProperty("spring.datasource.url", "jdbc:mysql://unreachable.invalid:3306/fictional");
            case "wrong-bind" -> env.setProperty("server.address", "0.0.0.0");
            case "mixed-profiles" -> env.setActiveProfiles("local-auth", "production");
            case "hikari-override" -> env.setProperty("spring.datasource.hikari.jdbc-url", "jdbc:mysql://unreachable.invalid/fictional");
            case "seed-override" -> env.setProperty("spring.sql.init.data-locations", "classpath:data.sql");
            default -> throw new IllegalArgumentException("Unknown test scenario");
        }
        return env;
    }
    static Stream<Arguments> hostedCases() {
        return Stream.of("disabled", "profile-only", "flag-only", "remote-database", "wrong-bind",
            "mixed-profiles", "hikari-override", "seed-override").flatMap(scenario ->
                IDENTITIES.stream().flatMap(email -> Stream.of(email, "  " + email.toUpperCase(Locale.ROOT) + "  "))
                    .map(email -> Arguments.of(scenario, email)));
    }
    AuthService auth(MockEnvironment env) {
        return new AuthService(users, ngos, PASSWORDS, jwt, new RegistrationPolicy(env), new DemoIdentityPolicy(env));
    }
    User account(String email) {
        User user = new User(); user.setEmail(email); user.setName("Fictional security fixture");
        user.setPasswordHash(HASH); user.setRole(email.startsWith("admin") ? Role.ADMIN : email.startsWith("meera") ? Role.DONOR : Role.NGO);
        user.setVerified(email.startsWith("divya")); return user;
    }
    void filter(String token, MockEnvironment env) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        new JwtAuthenticationFilter(jwt, users, new DemoIdentityPolicy(env))
            .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
    @ParameterizedTest(name="{0}: demo login, profile and prior JWT denied [{index}]")
    @MethodSource("hostedCases")
    void hostedDemoIdentitiesAreDeniedBeforeAnyAccountLookup(String scenario, String email) throws Exception {
        MockEnvironment env = blocked(scenario);
        // Represents a previously issued, cryptographically valid demo token with this subject.
        String token = jwt.generate(account(email)); assertEquals(email, jwt.subject(token));
        ApiException login = assertThrows(ApiException.class, () -> auth(env).login(new LoginRequest(email, PASSWORD)));
        assertEquals(HttpStatus.UNAUTHORIZED, login.getStatus()); assertEquals("Invalid email or password", login.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ApiException.class, () -> auth(env).me(email)).getStatus());
        filter(token, env); assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(users, ngos);
    }
    @ParameterizedTest @ValueSource(strings={"meera@medbridge.test", "divya@medbridge.test", "pending@medbridge.test", "admin@medbridge.test"})
    void fullyIsolatedAccountsCanLoginAndUseTheirJwt(String email) throws Exception {
        User user = account(email);
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        var response = auth(isolated()).login(new LoginRequest(email, PASSWORD));
        assertEquals(email, jwt.subject(response.token()));
        assertEquals(email, auth(isolated()).me(email).email());
        filter(response.token(), isolated());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication); assertEquals(email, authentication.getName());
        if (email.startsWith("pending")) assertTrue(authentication.getAuthorities().isEmpty());
    }
    @Test void allMedbridgeTestIdentitiesAreCoveredAndUncertainConfigurationFailsClosed() {
        assertFalse(new DemoIdentityPolicy(new MockEnvironment()).allows("Another@MedBridge.Test"));
        assertFalse(new DemoIdentityPolicy(isolated()).allows(null));
        assertFalse(new DemoIdentityPolicy(isolated()).allows(" "));
        assertTrue(new DemoIdentityPolicy(isolated()).allows("  Another@MedBridge.Test  "));
        MockEnvironment broken = mock(MockEnvironment.class);
        when(broken.getActiveProfiles()).thenThrow(new IllegalStateException("Synthetic unavailable configuration"));
        assertFalse(new DemoIdentityPolicy(broken).allows(IDENTITIES.get(0)));
    }
    @Test void nonDemoAuthenticationStillWorksWithoutTheLocalProfile() throws Exception {
        MockEnvironment env = new MockEnvironment(); String email = "regular@example.invalid";
        User user = account(email); user.setRole(Role.DONOR);
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        var response = auth(env).login(new LoginRequest(email, PASSWORD));
        assertEquals(email, auth(env).me(email).email());
        filter(response.token(), env); assertEquals(email, SecurityContextHolder.getContext().getAuthentication().getName());
    }
    @Test void storedDemoIdentityCannotBypassPolicyThroughADifferentLookupSubject() throws Exception {
        MockEnvironment env = new MockEnvironment(); String alias = "regular@example.invalid";
        when(users.findByEmailIgnoreCase(alias)).thenReturn(Optional.of(account(IDENTITIES.get(0))));
        assertThrows(ApiException.class, () -> auth(env).login(new LoginRequest(alias, PASSWORD)));
        assertThrows(ApiException.class, () -> auth(env).me(alias));
        filter(jwt.generate(account(alias)), env); assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
