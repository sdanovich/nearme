package com.example.nearme.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock JwtService jwt;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;

    private JwtAuthFilter filter;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        filter = new JwtAuthFilter(jwt);
        responseBody = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @Test
    void skipsTokenEndpointPreflightAndNonApiPaths() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn(JwtAuthFilter.TOKEN_PATH);
        assertThat(filter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("/");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        // OPTIONS (CORS preflight) is skipped regardless of path
        when(request.getMethod()).thenReturn("OPTIONS");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void protectsApiPaths() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/stations/nearby");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void missingHeaderIsRejectedWithoutReachingChain() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
        assertThat(responseBody.toString()).contains("error");
    }

    @Test
    void validTokenPassesThroughToChain() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer good.token.here");
        when(jwt.validate("good.token.here")).thenReturn("nearme-app");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(401);
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer bad");
        when(jwt.validate("bad")).thenThrow(new JwtException("nope"));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }
}
