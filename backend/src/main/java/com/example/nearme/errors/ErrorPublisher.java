package com.example.nearme.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes error events to a Redis Stream — but SAFELY.
 *
 * Two non-negotiable rules, because this code runs *while the app is already
 * failing*:
 *   1. It ALWAYS writes the error to the local structured log first. The log is
 *      the floor that never depends on external infra. The broker is an
 *      enhancement on top, never a replacement.
 *   2. The broker publish is async (off the request thread) and best-effort: any
 *      failure to publish is swallowed and logged locally, never thrown. A broker
 *      outage must never turn into a new error in the error path.
 */
@Component
public class ErrorPublisher {

    public static final String STREAM = "stream:errors";
    private static final int MAXLEN = 50_000; // cap stream growth (approx)

    private static final Logger log = LoggerFactory.getLogger(ErrorPublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public ErrorPublisher(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /**
     * Capture an error. Returns immediately. Local logging happens synchronously
     * (cheap, reliable); the broker publish is dispatched async.
     */
    public void capture(ErrorEvent event) {
        // 1. ALWAYS log locally first — this is the reliable floor.
        log.error("[error-event] {} {} {} status={} ex={} msg={}",
                event.eventId(), event.method(), event.path(),
                event.httpStatus(), event.exception(), event.message());

        // 2. Best-effort async publish to the broker.
        publishAsync(event);
    }

    @Async("errorPublisherExecutor")
    public void publishAsync(ErrorEvent event) {
        try {
            String json = mapper.writeValueAsString(event);
            var record = StreamRecords.newRecord()
                    .in(STREAM)
                    .ofMap(Map.of("payload", json));
            redis.opsForStream().add(record);
            // Trim opportunistically so the stream can't grow without bound.
            redis.opsForStream().trim(STREAM, MAXLEN, true);
        } catch (Exception e) {
            // Swallow: a failure to publish an error must not create another error.
            // We already logged the event locally above, so nothing is lost.
            log.warn("[error-event] broker publish failed (event already logged locally): {}",
                    e.getMessage());
        }
    }
}
