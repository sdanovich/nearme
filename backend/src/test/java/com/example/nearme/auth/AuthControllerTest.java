package com.example.nearme.auth;

import com.example.nearme.dto.TokenRequest;
import com.example.nearme.dto.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerTest {

    private static final String CLIENT_SECRET = "the-shared-client-secret";

    private JwtService jwt;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        jwt = new JwtService("test-secret-test-secret-test-secret-32+", 3600, "nearme-backend");
        controller = new AuthController(jwt, CLIENT_SECRET);
    }

    @Test
    void validClientSecretReturnsAUsableToken() {
        ResponseEntity<?> resp = controller.token(new TokenRequest(CLIENT_SECRET));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isInstanceOf(TokenResponse.class);
        TokenResponse body = (TokenResponse) resp.getBody();
        assertThat(body.tokenType()).isEqualTo("Bearer");
        assertThat(body.expiresInSeconds()).isEqualTo(3600);
        // the issued token actually verifies
        assertThat(jwt.validate(body.token())).isEqualTo("nearme-app");
    }

    @Test
    void wrongClientSecretIsUnauthorized() {
        ResponseEntity<?> resp = controller.token(new TokenRequest("nope"));

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        assertThat(resp.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsKey("error");
    }

    @Test
    void emptyServerSecretRejectsEverything() {
        AuthController noSecret = new AuthController(jwt, "");
        ResponseEntity<?> resp = noSecret.token(new TokenRequest(""));
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }
}
