-- =========================================================================
-- V12: Evolve transaction_logs into Audit Trail & Accountability Ledger
-- Idempotent migration supporting fresh installations and existing schemas
-- =========================================================================

DELIMITER //

CREATE PROCEDURE apply_v12_audit_migration()
BEGIN
    -- 1. Drop check constraint if it exists
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'transaction_logs' AND constraint_name = 'chk_transaction_logs_type'
    ) THEN
        ALTER TABLE transaction_logs DROP CHECK chk_transaction_logs_type;
    END IF;

    -- Drop foreign keys if they exist
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'transaction_logs' AND constraint_name = 'fk_transaction_logs_user'
    ) THEN
        ALTER TABLE transaction_logs DROP FOREIGN KEY fk_transaction_logs_user;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'transaction_logs' AND constraint_name = 'fk_transaction_logs_item'
    ) THEN
        ALTER TABLE transaction_logs DROP FOREIGN KEY fk_transaction_logs_item;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'transaction_logs' AND constraint_name = 'fk_transaction_logs_location'
    ) THEN
        ALTER TABLE transaction_logs DROP FOREIGN KEY fk_transaction_logs_location;
    END IF;

    -- 2. Relax stock-only NOT NULL columns so non-stock audit events can be recorded
    ALTER TABLE transaction_logs MODIFY COLUMN item_id BIGINT UNSIGNED NULL;
    ALTER TABLE transaction_logs MODIFY COLUMN location_id BIGINT UNSIGNED NULL;
    ALTER TABLE transaction_logs MODIFY COLUMN user_id BIGINT UNSIGNED NULL;
    ALTER TABLE transaction_logs MODIFY COLUMN transaction_type VARCHAR(50) NULL DEFAULT 'AUDIT';
    ALTER TABLE transaction_logs MODIFY COLUMN qty_change INT NULL DEFAULT 0;
    ALTER TABLE transaction_logs MODIFY COLUMN reference_id BIGINT UNSIGNED NULL;

    -- 3. Add audit columns for full accountability (WHO, WHAT, WHICH, STATUS, BEFORE/AFTER, METADATA)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'transaction_logs' AND column_name = 'action'
    ) THEN
        ALTER TABLE transaction_logs ADD COLUMN action VARCHAR(50) NOT NULL DEFAULT 'LEGACY_TRANSACTION' AFTER timestamp;
        ALTER TABLE transaction_logs ADD COLUMN resource_type VARCHAR(50) NULL AFTER action;
        ALTER TABLE transaction_logs ADD COLUMN resource_id BIGINT UNSIGNED NULL AFTER resource_type;
        ALTER TABLE transaction_logs ADD COLUMN actor_username VARCHAR(100) NULL AFTER resource_id;
        ALTER TABLE transaction_logs ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' AFTER actor_username;
        ALTER TABLE transaction_logs ADD COLUMN before_state VARCHAR(255) NULL AFTER status;
        ALTER TABLE transaction_logs ADD COLUMN after_state VARCHAR(255) NULL AFTER before_state;
        ALTER TABLE transaction_logs ADD COLUMN details VARCHAR(500) NULL AFTER after_state;
        ALTER TABLE transaction_logs ADD COLUMN ip_address VARCHAR(45) NULL AFTER details;
    END IF;

    -- 4. Backfill existing transaction records with typed actions and resource metadata
    UPDATE transaction_logs SET action = 'ITEM_BORROWED', resource_type = 'ITEM', resource_id = item_id, status = 'SUCCESS' WHERE transaction_type = 'BORROW' AND action = 'LEGACY_TRANSACTION';
    UPDATE transaction_logs SET action = 'ITEM_RETURNED', resource_type = 'ITEM', resource_id = item_id, status = 'SUCCESS' WHERE transaction_type = 'RETURN' AND action = 'LEGACY_TRANSACTION';
    UPDATE transaction_logs SET action = 'STOCK_ADJUSTED', resource_type = 'ITEM', resource_id = item_id, status = 'SUCCESS' WHERE transaction_type = 'ADJUSTMENT' AND action = 'LEGACY_TRANSACTION';

    -- Backfill actor_username from users table for historical records
    UPDATE transaction_logs t
    JOIN users u ON u.id = t.user_id
    SET t.actor_username = u.username
    WHERE t.actor_username IS NULL AND t.user_id IS NOT NULL;

    -- 5. Indexes for high-performance audit querying, filtering, and pagination
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'transaction_logs' AND index_name = 'idx_transaction_logs_action_timestamp'
    ) THEN
        CREATE INDEX idx_transaction_logs_action_timestamp ON transaction_logs (action, timestamp);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'transaction_logs' AND index_name = 'idx_transaction_logs_resource'
    ) THEN
        CREATE INDEX idx_transaction_logs_resource ON transaction_logs (resource_type, resource_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'transaction_logs' AND index_name = 'idx_transaction_logs_actor_timestamp'
    ) THEN
        CREATE INDEX idx_transaction_logs_actor_timestamp ON transaction_logs (actor_username, timestamp);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'transaction_logs' AND index_name = 'idx_transaction_logs_timestamp'
    ) THEN
        CREATE INDEX idx_transaction_logs_timestamp ON transaction_logs (timestamp DESC);
    END IF;

END //

DELIMITER ;

CALL apply_v12_audit_migration();
DROP PROCEDURE apply_v12_audit_migration;
