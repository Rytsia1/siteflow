-- SiteFlow MVP schema
-- Naming: snake_case columns, plural table names, BIGINT UNSIGNED surrogate keys.

CREATE TABLE roles (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    role_name   VARCHAR(50) NOT NULL,
    CONSTRAINT uq_roles_role_name UNIQUE (role_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE users (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    role_id         BIGINT UNSIGNED NOT NULL,
    username        VARCHAR(50) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(100) NOT NULL,
    job_position    VARCHAR(100) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE locations (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    location_name   VARCHAR(100) NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_locations_location_name UNIQUE (location_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE items (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    item_code           VARCHAR(50) NOT NULL,
    name                VARCHAR(150) NOT NULL,
    category            VARCHAR(20) NOT NULL,
    unit                VARCHAR(20) NOT NULL,
    min_stock_threshold INT NOT NULL DEFAULT 0,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_items_item_code UNIQUE (item_code),
    CONSTRAINT chk_items_category CHECK (category IN ('TOOL', 'CONSUMABLE', 'LIFTING_GEAR')),
    CONSTRAINT chk_items_min_stock_threshold CHECK (min_stock_threshold >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_items_category ON items (category);

CREATE TABLE item_stocks (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    item_id         BIGINT UNSIGNED NOT NULL,
    location_id     BIGINT UNSIGNED NOT NULL,
    current_qty     INT NOT NULL DEFAULT 0,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_item_stocks_item_location UNIQUE (item_id, location_id),
    CONSTRAINT chk_item_stocks_current_qty CHECK (current_qty >= 0),
    CONSTRAINT fk_item_stocks_item FOREIGN KEY (item_id) REFERENCES items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_item_stocks_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE borrow_requests (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    location_id     BIGINT UNSIGNED NOT NULL,
    request_date    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT chk_borrow_requests_status CHECK (status IN ('PENDING', 'BORROWED', 'PARTIAL_RETURN', 'COMPLETED')),
    CONSTRAINT fk_borrow_requests_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_borrow_requests_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_borrow_requests_status ON borrow_requests (status);

CREATE TABLE borrow_items (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    borrow_request_id   BIGINT UNSIGNED NOT NULL,
    item_id             BIGINT UNSIGNED NOT NULL,
    qty_borrowed        INT NOT NULL,
    qty_returned        INT NOT NULL DEFAULT 0,
    return_date         DATETIME NULL,
    CONSTRAINT chk_borrow_items_qty_borrowed CHECK (qty_borrowed > 0),
    CONSTRAINT chk_borrow_items_qty_returned CHECK (qty_returned >= 0 AND qty_returned <= qty_borrowed),
    CONSTRAINT fk_borrow_items_request FOREIGN KEY (borrow_request_id) REFERENCES borrow_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_borrow_items_item FOREIGN KEY (item_id) REFERENCES items (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_borrow_items_item ON borrow_items (item_id);

CREATE TABLE stock_adjustments (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    item_id         BIGINT UNSIGNED NOT NULL,
    location_id     BIGINT UNSIGNED NOT NULL,
    adjusted_by     BIGINT UNSIGNED NOT NULL,
    adjustment_type VARCHAR(10) NOT NULL,
    qty             INT NOT NULL,
    reason          TEXT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stock_adjustments_type CHECK (adjustment_type IN ('IN', 'OUT')),
    CONSTRAINT chk_stock_adjustments_qty CHECK (qty > 0),
    CONSTRAINT fk_stock_adjustments_item FOREIGN KEY (item_id) REFERENCES items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_adjustments_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_adjustments_user FOREIGN KEY (adjusted_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE transaction_logs (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    item_id             BIGINT UNSIGNED NOT NULL,
    location_id         BIGINT UNSIGNED NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    transaction_type    VARCHAR(20) NOT NULL,
    qty_change           INT NOT NULL,
    reference_id        BIGINT UNSIGNED NOT NULL,
    timestamp           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_transaction_logs_type CHECK (transaction_type IN ('BORROW', 'RETURN', 'ADJUSTMENT')),
    CONSTRAINT fk_transaction_logs_item FOREIGN KEY (item_id) REFERENCES items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaction_logs_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaction_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_transaction_logs_item_timestamp ON transaction_logs (item_id, timestamp);
CREATE INDEX idx_transaction_logs_type_reference ON transaction_logs (transaction_type, reference_id);
