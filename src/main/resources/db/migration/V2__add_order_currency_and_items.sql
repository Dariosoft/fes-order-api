ALTER TABLE orders ADD COLUMN currency VARCHAR(3);

UPDATE orders SET currency = 'ARS' WHERE currency IS NULL;

ALTER TABLE orders ALTER COLUMN currency SET NOT NULL;

ALTER TABLE orders ADD CONSTRAINT orders_currency_check CHECK (currency IN ('ARS', 'USD'));

ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('PENDING', 'STOCK_RESERVED', 'STOCK_REJECTED'));

CREATE TABLE order_items (
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    CONSTRAINT order_items_pk PRIMARY KEY (order_id, product_id),
    CONSTRAINT order_items_order_fk FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT order_items_quantity_check CHECK (quantity > 0),
    CONSTRAINT order_items_unit_price_check CHECK (unit_price >= 0),
    CONSTRAINT order_items_currency_check CHECK (currency IN ('ARS', 'USD'))
);

CREATE INDEX order_items_order_id_idx ON order_items (order_id);
