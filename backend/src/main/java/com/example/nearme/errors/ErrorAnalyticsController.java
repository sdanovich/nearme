package com.example.nearme.errors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Small analytics surface over the persisted error events. This is the payoff of
 * the pipeline: queryable error data, populated safely off the request path.
 */
@RestController
@RequestMapping("/api/errors")
public class ErrorAnalyticsController {

    private final ErrorEventRepository repo;

    public ErrorAnalyticsController(ErrorEventRepository repo) {
        this.repo = repo;
    }

    /** Most recent errors (newest first). */
    @GetMapping("/recent")
    public List<ErrorEventRecord> recent() {
        return repo.findTop100ByOrderByOccurredAtDesc();
    }

    /** Which endpoints fail most over the last N hours. */
    @GetMapping("/by-path")
    public List<Map<String, Object>> byPath(@RequestParam(defaultValue = "24") int hours) {
        Instant since = Instant.now().minus(Duration.ofHours(hours));
        return repo.countByPathSince(since).stream()
                .map(p -> Map.<String, Object>of("path", p.getPath() == null ? "" : p.getPath(),
                        "count", p.getCount()))
                .toList();
    }

    /** What's breaking, by exception type, over the last N hours. */
    @GetMapping("/by-exception")
    public List<Map<String, Object>> byException(@RequestParam(defaultValue = "24") int hours) {
        Instant since = Instant.now().minus(Duration.ofHours(hours));
        return repo.countByExceptionSince(since).stream()
                .map(e -> Map.<String, Object>of("exception", e.getException() == null ? "" : e.getException(),
                        "count", e.getCount()))
                .toList();
    }
}
