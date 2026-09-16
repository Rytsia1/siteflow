-- SiteFlow schema audit follow-up: timestamp consistency and analytics query performance.
-- 1. borrow_requests was the only mutable entity table without an updated_at column
--    (status/approval_status/approved_by/approval_note all change after row creation,
--    with no record of when the most recent change happened).
-- 2. transaction_logs is queried by AnalyticsMapper filtered on (transaction_type,
--    timestamp) for consumption trends and "most borrowed item this period" — neither
--    existing index on this table covers that predicate pair, and this table grows
--    unbounded as an append-only ledger.

-- ============================================================================
-- 1. borrow_requests.updated_at — match the audit-timestamp convention already used
--    by users, items, item_stocks, item_instances, material_requests, purchase_orders.
-- ============================================================================
ALTER TABLE borrow_requests
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        AFTER approval_note;

-- ============================================================================
-- 2. transaction_logs — composite index for the analytics module's type+date-range reads.
-- ============================================================================
CREATE INDEX idx_transaction_logs_type_timestamp ON transaction_logs (transaction_type, timestamp);
