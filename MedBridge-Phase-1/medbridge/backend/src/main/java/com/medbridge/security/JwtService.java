package com.medbridge.security;

import com.medbridge.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirySeconds;
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiry-seconds:3600}") long expirySeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        if (expirySeconds <= 0 || expirySeconds > 86400)
            throw new IllegalStateException("JWT expiry must be between 1 and 86400 seconds");
        this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirySeconds=expirySeconds;
    }
    // Signed token contains only identity; authorities are loaded from the database.
    public String generate(User user) {
        Instant now=Instant.now();
        return Jwts.builder().subject(user.getEmail()).issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expirySeconds))).signWith(key).compact();
    }
    public String subject(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    }
    public long expirySeconds() { return expirySeconds; }
}
