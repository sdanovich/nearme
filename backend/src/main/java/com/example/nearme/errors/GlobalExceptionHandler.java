package com.example.nearme.errors;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Catches unhandled exceptions and validation failures across all controllers,
 * captures them as error events (which logs locally + publishes to the broker),
 * and returns a clean error response to the client.
 *
 * Capturing must never itself break the response — capture() is safe by design,
 * but we also guard the call so a capture failure can't replace the real error.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ErrorPublisher publisher;

    public GlobalExceptionHandler(ErrorPublisher publisher) {
        this.publisher = publisher;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBadRequest(IllegalArgumentException ex, HttpServletRequest req) {
        capture("HTTP_ERROR", 400, ex, req);
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception ex, HttpServletRequest req) {
        capture("EXCEPTION", 500, ex, req);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }

    private void capture(String kind, int status, Exception ex, HttpServletRequest req) {
        try {
            ErrorEvent event = new ErrorEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    kind,
                    status,
                    req != null ? req.getMethod() : null,
                    req != null ? req.getRequestURI() : null,
                    ex.getClass().getName(),
                    truncate(ex.getMessage(), 1000),
                    trimmedStack(ex),
                    req != null ? req.getHeader("X-Trace-Id") : null
            );
            publisher.capture(event);
        } catch (Exception captureFailure) {
            // Never let error-capture failure mask the original error.
            captureFailure.printStackTrace();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** First 15 stack frames, enough to diagnose without storing megabytes. */
    private String trimmedStack(Throwable t) {
        StringBuilder sb = new StringBuilder(t.toString()).append('\n');
        StackTraceElement[] frames = t.getStackTrace();
        int limit = Math.min(frames.length, 15);
        for (int i = 0; i < limit; i++) {
            sb.append("  at ").append(frames[i]).append('\n');
        }
        if (frames.length > limit) {
            sb.append("  ... ").append(frames.length - limit).append(" more\n");
        }
        return sb.toString();
    }
}
