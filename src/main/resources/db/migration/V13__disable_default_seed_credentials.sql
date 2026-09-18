-- =========================================================================
-- V13: Disable and neutralize legacy default seed credentials
--
-- Rationale:
-- Earlier development migrations (V3) seeded default credentials ('admin',
-- 'gudang', 'pekerja' with known passwords). In existing databases where V3
-- was already executed, removing the SQL from V3 does not remove those
-- accounts from the users table.
--
-- Directly deleting those accounts using DELETE is unsafe:
-- 1. Historical business records (borrow_requests, stock_adjustments,
--    material_requests) reference those user IDs with ON DELETE RESTRICT foreign keys.
--    Executing DELETE would trigger constraint violation errors and break migrations.
-- 2. Deleting user accounts would destroy historical audit accountability,
--    violating traceability requirements.
--
-- Safety design:
-- This migration safely deactivates and neutralizes only accounts that still
-- retain the exact known default seed password hashes. It sets is_active = FALSE,
-- updates deactivated_at, and rotates the password hash to an unmatchable disabled
-- marker. Legitimate user accounts and any accounts whose passwords have already
-- been changed are completely untouched.
-- =========================================================================

UPDATE users
SET is_active = FALSE,
    deactivated_at = COALESCE(deactivated_at, NOW()),
    password_hash = CONCAT('*DISABLED_DEFAULT_CREDENTIAL*', SHA2(UUID(), 256)),
    updated_at = NOW()
WHERE username IN ('admin', 'gudang', 'pekerja')
  AND password_hash IN (
    '$2b$10$TfwVMtix/ZbXNl/rYEnVYuhaA9LCKcysxnCRX2mOo0n8..TodUb/6', -- admin123
    '$2b$10$kd.Psfos2DL1QPp5.HouVemJD2540oWm6qs5svzhUrL6lw5SuNmo6', -- gudang123
    '$2b$10$DooaDYXNN0SWNwxPT.0tSemXrfnx2S/lIrNgT1ORswc6f2L/p1L9C'  -- pekerja123
  );
