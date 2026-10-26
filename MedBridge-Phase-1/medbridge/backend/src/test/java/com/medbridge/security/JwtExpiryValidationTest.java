package com.medbridge.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtExpiryValidationTest {
    @Test void rejectsAnActuallyExpiredSignedTokenAndMalformedTokens() {
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        JwtService service = new JwtService(secret, 3600);
        String expired = Jwts.builder().subject("fictional@example.test")
            .expiration(Date.from(Instant.now().minusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
        assertThrows(JwtException.class, () -> service.subject(expired));
        assertThrows(JwtException.class, () -> service.subject(UUID.randomUUID().toString()));
    }
}
