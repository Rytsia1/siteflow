-- =========================================================================
-- Test Fixtures: Seed Users for Automated Integration Testing
--
-- This migration runs ONLY in test scope (src/test/resources).
-- It is physically excluded from the production JAR build artifact.
-- =========================================================================

INSERT INTO users (role_id, username, password_hash, full_name, job_position, is_active, token_version)
SELECT r.id, 'admin', '$2b$10$TfwVMtix/ZbXNl/rYEnVYuhaA9LCKcysxnCRX2mOo0n8..TodUb/6', 'Test Admin', 'Administrator', TRUE, 1
FROM roles r WHERE r.role_name = 'ADMIN'
ON DUPLICATE KEY UPDATE
    password_hash = '$2b$10$TfwVMtix/ZbXNl/rYEnVYuhaA9LCKcysxnCRX2mOo0n8..TodUb/6',
    is_active = TRUE,
    token_version = 1;

INSERT INTO users (role_id, username, password_hash, full_name, job_position, is_active, token_version)
SELECT r.id, 'gudang', '$2b$10$kd.Psfos2DL1QPp5.HouVemJD2540oWm6qs5svzhUrL6lw5SuNmo6', 'Test Warehouse Staff', 'Warehouse Staff', TRUE, 1
FROM roles r WHERE r.role_name = 'WAREHOUSE_STAFF'
ON DUPLICATE KEY UPDATE
    password_hash = '$2b$10$kd.Psfos2DL1QPp5.HouVemJD2540oWm6qs5svzhUrL6lw5SuNmo6',
    is_active = TRUE,
    token_version = 1;

INSERT INTO users (role_id, username, password_hash, full_name, job_position, is_active, token_version)
SELECT r.id, 'pekerja', '$2b$10$DooaDYXNN0SWNwxPT.0tSemXrfnx2S/lIrNgT1ORswc6f2L/p1L9C', 'Test Field Staff', 'Field Worker', TRUE, 1
FROM roles r WHERE r.role_name = 'FIELD_STAFF'
ON DUPLICATE KEY UPDATE
    password_hash = '$2b$10$DooaDYXNN0SWNwxPT.0tSemXrfnx2S/lIrNgT1ORswc6f2L/p1L9C',
    is_active = TRUE,
    token_version = 1;

-- Fixtures for authorization test scenarios (ResourceAuthorizationTest & RbacSecurityTest)
INSERT INTO users (id, role_id, username, password_hash, full_name, job_position, is_active, token_version)
SELECT 10, r.id, 'userA', '$2b$10$DooaDYXNN0SWNwxPT.0tSemXrfnx2S/lIrNgT1ORswc6f2L/p1L9C', 'User A', 'Field Worker', TRUE, 1
FROM roles r WHERE r.role_name = 'FIELD_STAFF'
ON DUPLICATE KEY UPDATE
    is_active = TRUE,
    token_version = 1;

INSERT INTO users (id, role_id, username, password_hash, full_name, job_position, is_active, token_version)
SELECT 20, r.id, 'userB', '$2b$10$DooaDYXNN0SWNwxPT.0tSemXrfnx2S/lIrNgT1ORswc6f2L/p1L9C', 'User B', 'Field Worker', TRUE, 1
FROM roles r WHERE r.role_name = 'FIELD_STAFF'
ON DUPLICATE KEY UPDATE
    is_active = TRUE,
    token_version = 1;

INSERT INTO users (id, role_id, username, password_hash, full_name, job_position, is_active, token_version)
SELECT 99, r.id, 'norole', '$2b$10$DooaDYXNN0SWNwxPT.0tSemXrfnx2S/lIrNgT1ORswc6f2L/p1L9C', 'No Role User', 'Staff', TRUE, 1
FROM roles r WHERE r.role_name = 'FIELD_STAFF'
ON DUPLICATE KEY UPDATE
    is_active = TRUE,
    token_version = 1;
