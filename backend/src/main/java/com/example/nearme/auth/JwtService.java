package com.example.nearme.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies the short-lived HS256 JWTs used for app↔backend auth.
 * Symmetric signing: the same secret signs here and verifies here (and would
 * verify in any other instance sharing {@code nearme.jwt.secret}).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlSeconds;
    private final String issuer;

    public JwtService(@Value("${nearme.jwt.secret}") String secret,
                      @Value("${nearme.jwt.ttl-seconds:3600}") long ttlSeconds,
                      @Value("${nearme.jwt.issuer:nearme-backend}") String issuer) {
        // HS256 needs a >= 256-bit (32-byte) key; fail fast if misconfigured.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
        this.issuer = issuer;
    }

    /** Issue a signed token for {@code subject} (the app/client identity). */
    public String issue(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * Verify signature, issuer and expiry. Returns the subject on success;
     * throws {@link JwtException} (incl. expired/tampered/wrong-issuer) otherwise.
     */
    public String validate(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
}
