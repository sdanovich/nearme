-- V3: error_event table, populated by the error-stream consumer (not by the
-- failing request directly). Stores captured exceptions / failed requests for
-- analytics.

CREATE TABLE error_event (
    event_id     VARCHAR(64) PRIMARY KEY,
    occurred_at  TIMESTAMP(6) WITH TIME ZONE,
    kind         VARCHAR(32),
    http_status  INTEGER,
    method       VARCHAR(16),
    path         VARCHAR(512),
    exception    VARCHAR(512),
    message      VARCHAR(1100),
    stack_trace  VARCHAR(6000),
    trace_id     VARCHAR(128)
);

CREATE INDEX idx_error_occurred    ON error_event (occurred_at);
CREATE INDEX idx_error_kind_status ON error_event (kind, http_status);
CREATE INDEX idx_error_path        ON error_event (path);
