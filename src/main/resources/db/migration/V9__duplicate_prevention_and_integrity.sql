-- SiteFlow V9: Duplicate Submission Prevention and Data Integrity
-- 1. Unique constraint on purchase_orders.mr_id: ensures 1:1 mapping between Material Request and Purchase Order
-- 2. Unique constraint on material_request_items: prevents duplicate item lines within the same Material Request
-- 3. idempotency_keys table: provides database-backed unique locks for preventing duplicate submissions

-- 1. Enforce at most one Purchase Order per Material Request
ALTER TABLE purchase_orders
    ADD CONSTRAINT uq_purchase_orders_mr_id UNIQUE (mr_id);

-- 2. Enforce item uniqueness within a Material Request
ALTER TABLE material_request_items
    ADD CONSTRAINT uq_material_request_items_mr_item UNIQUE (mr_id, item_id);

-- 3. Idempotency tracking table
CREATE TABLE idempotency_keys (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    key_value   VARCHAR(128) NOT NULL,
    user_id     BIGINT UNSIGNED NOT NULL,
    endpoint    VARCHAR(100) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idempotency_keys_value UNIQUE (key_value),
    CONSTRAINT fk_idempotency_keys_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_idempotency_keys_created_at ON idempotency_keys (created_at);
CREATE INDEX idx_idempotency_keys_user ON idempotency_keys (user_id);
