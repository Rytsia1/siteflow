-- =============================================================================
-- SiteFlow — Least-Privilege MySQL User Provisioning Script
--
-- Security Hardening:
-- 1. Separates the application runtime user (DML only) from the schema migration
--    user (DDL + DML).
-- 2. Prevents the web application from executing dangerous DDL statements (DROP,
--    ALTER, CREATE) even if a SQL injection vulnerability were to exist.
-- 3. Eliminates the use of the MySQL 'root' superuser for application connections.
--
-- Usage:
--   mysql -u root -p < scripts/setup-least-privilege-users.sql
--
-- IMPORTANT:
-- Replace 'CHANGE_APP_PASSWORD_HERE' and 'CHANGE_MIGRATION_PASSWORD_HERE'
-- with strong, unique secrets generated via a secure password manager.
-- =============================================================================

-- 1. Ensure target database exists
CREATE DATABASE IF NOT EXISTS `siteflow`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- 2. Create dedicated Application Runtime User (Least Privilege: DML only)
-- Can read, write, and update application domain data; cannot alter schema.
CREATE USER IF NOT EXISTS 'siteflow_app'@'%'
    IDENTIFIED BY 'CHANGE_APP_PASSWORD_HERE'
    REQUIRE SSL;

GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE
    ON `siteflow`.*
    TO 'siteflow_app'@'%';

-- 3. Create dedicated Flyway Migration User (DDL + DML)
-- Has structural schema management privileges needed to run Flyway migrations.
CREATE USER IF NOT EXISTS 'siteflow_migration'@'%'
    IDENTIFIED BY 'CHANGE_MIGRATION_PASSWORD_HERE'
    REQUIRE SSL;

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, CREATE ROUTINE, ALTER ROUTINE, TRIGGER
    ON `siteflow`.*
    TO 'siteflow_migration'@'%';

-- 4. Apply privilege changes
FLUSH PRIVILEGES;

-- 5. Verification queries (run to inspect granted privileges)
-- SHOW GRANTS FOR 'siteflow_app'@'%';
-- SHOW GRANTS FOR 'siteflow_migration'@'%';
