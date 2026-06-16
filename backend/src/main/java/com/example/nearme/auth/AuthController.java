package com.example.nearme.auth;

import com.example.nearme.dto.TokenRequest;
import com.example.nearme.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Issues access tokens. This is the one /api endpoint the auth filter leaves
 * open: the app posts its shared client secret and gets back a short-lived JWT.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwt;
    private final String clientSecret;

    public AuthController(JwtService jwt,
                          @Value("${nearme.auth.client-secret}") String clientSecret) {
        this.jwt = jwt;
        this.clientSecret = clientSecret;
    }

    @PostMapping("/token")
    public ResponseEntity<?> token(@Valid @RequestBody TokenRequest req) {
        if (!secretMatches(req.clientSecret())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid client secret"));
        }
        String token = jwt.issue("nearme-app");
        return ResponseEntity.ok(new TokenResponse(token, "Bearer", jwt.ttlSeconds()));
    }

    /** Constant-time comparison; also rejects when no server secret is configured. */
    private boolean secretMatches(String presented) {
        if (clientSecret == null || clientSecret.isBlank() || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                clientSecret.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
