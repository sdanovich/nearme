package com.example.nearme.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-32+"; // >= 32 bytes
    private static final String ISSUER = "nearme-backend";

    private JwtService service(long ttlSeconds) {
        return new JwtService(SECRET, ttlSeconds, ISSUER);
    }

    @Test
    void issuedTokenValidatesAndReturnsSubject() {
        JwtService jwt = service(3600);
        String token = jwt.issue("nearme-app");
        assertThat(jwt.validate(token)).isEqualTo("nearme-app");
    }

    @Test
    void ttlSecondsIsExposed() {
        assertThat(service(1800).ttlSeconds()).isEqualTo(1800);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = service(3600);
        String token = jwt.issue("nearme-app");
        assertThatThrownBy(() -> jwt.validate(token + "x"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        String token = service(3600).issue("nearme-app");
        JwtService other = new JwtService("a-totally-different-secret-of-32+bytes!!", 3600, ISSUER);
        assertThatThrownBy(() -> other.validate(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = service(-10); // issued already-expired
        String token = jwt.issue("nearme-app");
        assertThatThrownBy(() -> jwt.validate(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void wrongIssuerIsRejected() {
        String token = new JwtService(SECRET, 3600, "some-other-issuer").issue("nearme-app");
        assertThatThrownBy(() -> service(3600).validate(token))
                .isInstanceOf(JwtException.class);
    }
}
