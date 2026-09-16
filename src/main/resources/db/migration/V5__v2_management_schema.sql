-- SiteFlow V2 (Management) schema additions
-- 1. Exact Asset Tracking (Item Instances)
-- 2. Approval Workflow on Borrow Requests
-- 3. Procurement Pipeline (Material Requests & Purchase Orders)

-- ============================================================================
-- 1. Item Instances – individual physical asset tracking
-- ============================================================================
CREATE TABLE item_instances (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    item_id         BIGINT UNSIGNED NOT NULL,
    serial_number   VARCHAR(100) NOT NULL,
    qr_code_value   VARCHAR(255) NOT NULL,
    tool_condition   VARCHAR(20) NOT NULL DEFAULT 'GOOD',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_item_instances_serial_number UNIQUE (serial_number),
    CONSTRAINT uq_item_instances_qr_code_value UNIQUE (qr_code_value),
    CONSTRAINT chk_item_instances_condition CHECK (tool_condition IN ('GOOD', 'NEEDS_REPAIR', 'BROKEN')),
    CONSTRAINT fk_item_instances_item FOREIGN KEY (item_id) REFERENCES items (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_item_instances_item ON item_instances (item_id);
CREATE INDEX idx_item_instances_condition ON item_instances (tool_condition);

-- ============================================================================
-- 2. Approval Workflow – extend borrow_requests
-- ============================================================================
ALTER TABLE borrow_requests
    ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL'
        AFTER status,
    ADD COLUMN approved_by BIGINT UNSIGNED NULL
        AFTER approval_status,
    ADD COLUMN approval_note TEXT NULL
        AFTER approved_by;

ALTER TABLE borrow_requests
    ADD CONSTRAINT chk_borrow_requests_approval_status
        CHECK (approval_status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    ADD CONSTRAINT fk_borrow_requests_approved_by
        FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE RESTRICT;

-- Update existing rows to APPROVED so they remain valid
UPDATE borrow_requests SET approval_status = 'APPROVED' WHERE approval_status = 'PENDING_APPROVAL';

CREATE INDEX idx_borrow_requests_approval_status ON borrow_requests (approval_status);

-- ============================================================================
-- 3. Procurement Pipeline
-- ============================================================================

-- 3a. Material Requests
CREATE TABLE material_requests (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    requested_by    BIGINT UNSIGNED NOT NULL,
    request_date    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    justification   TEXT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_material_requests_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PO_CREATED', 'COMPLETED')),
    CONSTRAINT fk_material_requests_user
        FOREIGN KEY (requested_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_material_requests_status ON material_requests (status);
CREATE INDEX idx_material_requests_requested_by ON material_requests (requested_by);

-- 3b. Material Request Items (line items within a request)
CREATE TABLE material_request_items (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    mr_id           BIGINT UNSIGNED NOT NULL,
    item_id         BIGINT UNSIGNED NOT NULL,
    requested_qty   INT NOT NULL,
    CONSTRAINT chk_material_request_items_qty CHECK (requested_qty > 0),
    CONSTRAINT fk_material_request_items_mr
        FOREIGN KEY (mr_id) REFERENCES material_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_material_request_items_item
        FOREIGN KEY (item_id) REFERENCES items (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_material_request_items_mr ON material_request_items (mr_id);

-- 3c. Purchase Orders
CREATE TABLE purchase_orders (
    id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    mr_id                   BIGINT UNSIGNED NOT NULL,
    po_number               VARCHAR(50) NOT NULL,
    supplier_name           VARCHAR(200) NOT NULL,
    order_date              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expected_delivery_date  DATETIME NULL,
    po_status               VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_purchase_orders_po_number UNIQUE (po_number),
    CONSTRAINT chk_purchase_orders_status
        CHECK (po_status IN ('ISSUED', 'PARTIAL_RECEIVED', 'FULFILLED')),
    CONSTRAINT fk_purchase_orders_mr
        FOREIGN KEY (mr_id) REFERENCES material_requests (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_purchase_orders_mr ON purchase_orders (mr_id);
CREATE INDEX idx_purchase_orders_status ON purchase_orders (po_status);
