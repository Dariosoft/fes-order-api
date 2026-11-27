ALTER TABLE order_outbox
    ADD COLUMN kafka_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN kafka_published_at TIMESTAMPTZ NULL,
    ADD COLUMN kafka_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN kafka_next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE order_outbox
    ADD CONSTRAINT order_outbox_kafka_status_check
    CHECK (kafka_status IN ('PENDING', 'PUBLISHED'));

CREATE INDEX order_outbox_kafka_pending_idx
    ON order_outbox (kafka_status, kafka_next_attempt_at);
