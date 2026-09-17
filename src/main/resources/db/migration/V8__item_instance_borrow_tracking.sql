-- Tool Borrowing and Return Workflow Hardening
-- Adds instance availability tracking and current borrow request association.

ALTER TABLE item_instances
    ADD COLUMN is_available BOOLEAN NOT NULL DEFAULT TRUE AFTER tool_condition,
    ADD COLUMN current_borrow_request_id BIGINT UNSIGNED NULL AFTER is_available,
    ADD CONSTRAINT fk_item_instances_borrow_request
        FOREIGN KEY (current_borrow_request_id) REFERENCES borrow_requests (id) ON DELETE SET NULL;

CREATE INDEX idx_item_instances_available ON item_instances (is_available);
CREATE INDEX idx_item_instances_borrow_request ON item_instances (current_borrow_request_id);
