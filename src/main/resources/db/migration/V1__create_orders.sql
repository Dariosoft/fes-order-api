CREATE TABLE orders (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    total NUMERIC(19, 2) NOT NULL CHECK (total >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX orders_tenant_id_idx ON orders (tenant_id);
