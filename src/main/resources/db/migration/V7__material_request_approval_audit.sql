-- Material Request approval workflow: audit trail + rejection status.
-- 1. material_requests had no record of who approved/rejected a request or why —
--    unlike borrow_requests, which has carried approved_by/approval_note since V5.
-- 2. MaterialRequestStatus gains REJECTED so a submitted request can be closed out
--    without ever becoming a purchase order. The CHECK constraint must be widened
--    to allow it, or every REJECTED write fails with MySQL error 3819.

-- ============================================================================
-- 1. Approval audit columns, mirroring borrow_requests.approved_by/approval_note.
-- ============================================================================
ALTER TABLE material_requests
    ADD COLUMN approved_by BIGINT UNSIGNED NULL AFTER justification,
    ADD COLUMN approval_note TEXT NULL AFTER approved_by,
    ADD CONSTRAINT fk_material_requests_approved_by
        FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE RESTRICT;

-- ============================================================================
-- 2. Widen the status CHECK to include REJECTED. Must be a separate statement
--    from the ADD CONSTRAINT below — MySQL does not guarantee DROP-before-ADD
--    ordering for two constraints of the same name within one ALTER TABLE.
-- ============================================================================
ALTER TABLE material_requests DROP CHECK chk_material_requests_status;

ALTER TABLE material_requests
    ADD CONSTRAINT chk_material_requests_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PO_CREATED', 'COMPLETED'));
