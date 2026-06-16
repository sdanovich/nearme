package com.example.nearme.errors;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Persisted error event — the end of the pipeline. Written by the consumer that
 * reads the broker stream, NOT by the failing request directly. That decoupling
 * is the whole point: storing here can be slow or fail without affecting the app.
 */
@Entity
@Table(name = "error_event", indexes = {
        @Index(name = "idx_error_occurred", columnList = "occurredAt"),
        @Index(name = "idx_error_kind_status", columnList = "kind,httpStatus"),
        @Index(name = "idx_error_path", columnList = "path")
})
public class ErrorEventRecord {

    /** Use the event's own id as PK so re-delivered events dedupe naturally. */
    @Id
    private String eventId;

    private Instant occurredAt;
    private String kind;
    private int httpStatus;
    private String method;
    private String path;
    private String exception;

    @Column(length = 1100)
    private String message;

    @Column(length = 6000)
    private String stackTrace;

    private String traceId;

    public ErrorEventRecord() {}

    public String getEventId() { return eventId; }
    public void setEventId(String v) { eventId = v; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant v) { occurredAt = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { kind = v; }
    public int getHttpStatus() { return httpStatus; }
    public void setHttpStatus(int v) { httpStatus = v; }
    public String getMethod() { return method; }
    public void setMethod(String v) { method = v; }
    public String getPath() { return path; }
    public void setPath(String v) { path = v; }
    public String getException() { return exception; }
    public void setException(String v) { exception = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { message = v; }
    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String v) { stackTrace = v; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String v) { traceId = v; }
}
