package com.example.nearme.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Requires a valid {@code Authorization: Bearer <jwt>} on every {@code /api/**}
 * request, EXCEPT the token endpoint itself. Anything that fails verification
 * gets a clean 401 and never reaches a controller.
 *
 * Deliberately a plain servlet filter (no Spring Security): it's surgical, easy
 * to reason about, and doesn't change the rest of the app's request handling.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    static final String TOKEN_PATH = "/api/auth/token";

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // CORS preflight carries no auth header; only guard /api/**; leave the
        // token endpoint open so the app can obtain a token in the first place.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        if (TOKEN_PATH.equals(path)) return true;
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            unauthorized(response, "missing bearer token");
            return;
        }
        try {
            jwt.validate(header.substring(7).trim());
        } catch (JwtException | IllegalArgumentException e) {
            unauthorized(response, "invalid or expired token");
            return;
        }
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // small, fixed JSON — no user input interpolated
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
