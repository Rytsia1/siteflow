-- =========================================================================
-- Dev-only Repeatable Migration: Seed Local Development Users
--
-- This migration runs ONLY when the 'dev' profile is activated.
-- It provides convenient local developer fixtures and is NEVER executed
-- in production deployments.
-- =========================================================================

INSERT INTO users (role_id, username, password_hash, full_name, job_position, is_active)
SELECT r.id, 'admin', '$2b$10$TfwVMtix/ZbXNl/rYEnVYuhaA9LCKcysxnCRX2mOo0n8..TodUb/6', 'Dev Admin', 'Administrator', TRUE
FROM roles r WHERE r.role_name = 'ADMIN'
ON DUPLICATE KEY UPDATE
    password_hash = '$2b$10$TfwVMtix/ZbXNl/rYEnVYuhaA9LCKcysxnCRX2mOo0n8..TodUb/6',
    is_active = TRUE;

INSERT INTO users (role_id, username, password_hash, full_name, job_position, is_active)
SELECT r.id, 'gudang', '$2b$10$kd.Psfos2DL1QPp5.HouVemJD2540oWm6qs5svzhUrL6lw5SuNmo6', 'Dev Warehouse Staff', 'Warehouse Staff', TRUE
FROM roles r WHERE r.role_name = 'WAREHOUSE_STAFF'
ON DUPLICATE KEY UPDATE
    password_hash = '$2b$10$kd.Psfos2DL1QPp5.HouVemJD2540oWm6qs5svzhUrL6lw5SuNmo6',
    is_active = TRUE;

INSERT INTO users (role_id, username, password_hash, full_name, job_position, is_active)
SELECT r.id, 'pekerja', '$2b$10$DooaDYXNN0SWNwxPT.0tSemXrfnx2S/lIrNgT1ORswc6f2L/p1L9C', 'Dev Field Staff', 'Field Worker', TRUE
FROM roles r WHERE r.role_name = 'FIELD_STAFF'
ON DUPLICATE KEY UPDATE
    password_hash = '$2b$10$DooaDYXNN0SWNwxPT.0tSemXrfnx2S/lIrNgT1ORswc6f2L/p1L9C',
    is_active = TRUE;
