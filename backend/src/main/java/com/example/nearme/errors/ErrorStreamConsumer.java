package com.example.nearme.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

/**
 * Consumes error events from the broker and lands them in Postgres. This runs
 * decoupled from the request path: if persistence is slow or the DB is down,
 * only this consumer is affected — the app keeps serving, and unacked events
 * stay pending in the stream for retry.
 */
@Component
public class ErrorStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(ErrorStreamConsumer.class);
    public static final String GROUP = "nearme-error-consumer";

    private final ObjectMapper mapper;
    private final ErrorEventRepository repo;
    private final StringRedisTemplate redis;

    public ErrorStreamConsumer(ObjectMapper mapper, ErrorEventRepository repo, StringRedisTemplate redis) {
        this.mapper = mapper;
        this.repo = repo;
        this.redis = redis;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String json = record.getValue().get("payload");
        try {
            ErrorEvent e = mapper.readValue(json, ErrorEvent.class);
            if (!repo.existsById(e.eventId())) {
                ErrorEventRecord r = new ErrorEventRecord();
                r.setEventId(e.eventId());
                r.setOccurredAt(e.occurredAt());
                r.setKind(e.kind());
                r.setHttpStatus(e.httpStatus());
                r.setMethod(e.method());
                r.setPath(e.path());
                r.setException(e.exception());
                r.setMessage(e.message());
                r.setStackTrace(e.stackTrace());
                r.setTraceId(e.traceId());
                repo.save(r);
            }
            // Ack only after successful persistence.
            redis.opsForStream().acknowledge(ErrorPublisher.STREAM, GROUP, record.getId());
        } catch (Exception ex) {
            // Leave unacked for retry; just log. Do not rethrow into the container.
            log.warn("Failed to persist error event {}: {}", record.getId(), ex.getMessage());
        }
    }
}
