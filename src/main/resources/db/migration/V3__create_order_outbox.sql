CREATE TABLE order_outbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT order_outbox_status_check CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX order_outbox_pending_idx ON order_outbox (status, next_attempt_at);

CREATE INDEX order_outbox_aggregate_id_idx ON order_outbox (aggregate_id);
