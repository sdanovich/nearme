package com.example.nearme.errors;

import java.time.Instant;

/**
 * One captured error/failed-request event. This is the payload published to the
 * broker and later persisted. Kept flat and JSON-friendly.
 *
 * @param eventId    unique id (also used to dedupe on the consumer side)
 * @param occurredAt when it happened
 * @param kind       "EXCEPTION" or "HTTP_ERROR"
 * @param httpStatus response status if it was a request failure (else 0)
 * @param method     HTTP method if applicable
 * @param path       request path if applicable
 * @param exception  exception class name if applicable
 * @param message    short error message (truncated)
 * @param stackTrace trimmed stack trace (first N frames)
 * @param traceId    correlation id if present, for tying to logs
 */
public record ErrorEvent(
        String eventId,
        Instant occurredAt,
        String kind,
        int httpStatus,
        String method,
        String path,
        String exception,
        String message,
        String stackTrace,
        String traceId
) {}
