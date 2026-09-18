-- SiteFlow V11: User Lifecycle, Privacy Governance, and Deactivation Tracking
-- 1. Adds is_active to allow privacy-preserving account deactivation without breaking
--    foreign key references across borrow requests, approvals, and transaction logs.
-- 2. Adds deactivated_at timestamp for data retention and lifecycle audit tracking.

ALTER TABLE users
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE AFTER job_position,
    ADD COLUMN deactivated_at DATETIME NULL AFTER is_active;

CREATE INDEX idx_users_is_active ON users (is_active);
