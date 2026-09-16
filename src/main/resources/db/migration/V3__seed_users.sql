-- Dev-only seed accounts, one per role, for local testing of the API and role-based access.
-- Passwords are the username-matching "<role>123" strings below, BCrypt-hashed.
INSERT IGNORE INTO users (role_id, username, password_hash, full_name, job_position)
SELECT r.id, 'admin', '$2b$10$TfwVMtix/ZbXNl/rYEnVYuhaA9LCKcysxnCRX2mOo0n8..TodUb/6', 'Dev Admin', 'Administrator'
FROM roles r WHERE r.role_name = 'ADMIN';

INSERT IGNORE INTO users (role_id, username, password_hash, full_name, job_position)
SELECT r.id, 'gudang', '$2b$10$kd.Psfos2DL1QPp5.HouVemJD2540oWm6qs5svzhUrL6lw5SuNmo6', 'Dev Warehouse Staff', 'Warehouse Staff'
FROM roles r WHERE r.role_name = 'WAREHOUSE_STAFF';

INSERT IGNORE INTO users (role_id, username, password_hash, full_name, job_position)
SELECT r.id, 'pekerja', '$2b$10$DooaDYXNN0SWNwxPT.0tSemXrfnx2S/lIrNgT1ORswc6f2L/p1L9C', 'Dev Field Staff', 'Field Worker'
FROM roles r WHERE r.role_name = 'FIELD_STAFF';
