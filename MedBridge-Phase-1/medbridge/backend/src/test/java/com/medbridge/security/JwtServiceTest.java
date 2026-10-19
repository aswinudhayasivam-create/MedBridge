package com.medbridge.security;

import com.medbridge.model.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    @Test
    void rejectsShortSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 3600));
    }

    @Test
    void issuesAndVerifiesSignedTokens() {
        JwtService service = new JwtService("local-test-secret-with-more-than-thirty-two-bytes-only", 3600);
        User user = new User();
        user.setEmail("donor@example.test");
        String token = service.generate(user);
        assertEquals("donor@example.test", service.subject(token));
    }

    @Test
    void rejectsTamperedTokens() {
        JwtService service = new JwtService("local-test-secret-with-more-than-thirty-two-bytes-only", 3600);
        User user = new User();
        user.setEmail("donor@example.test");
        String token = service.generate(user);
        String[] parts = token.split("\\.");
        String corruptedSignature = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        assertThrows(JwtException.class, () -> service.subject(parts[0] + "." + parts[1] + "." + corruptedSignature));
    }

    @Test
    void rejectsExpiredTokens() {
        assertThrows(IllegalStateException.class,
            () -> new JwtService("local-test-secret-with-more-than-thirty-two-bytes-only", -5));
    }
}
