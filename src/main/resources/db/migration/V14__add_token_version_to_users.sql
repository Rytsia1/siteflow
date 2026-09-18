-- SiteFlow V14: Token Revocation and Session Versioning
-- Adds token_version to users table to invalidate previously issued JWTs
-- upon account deactivation, credential change, role modification, or logout.

ALTER TABLE users
    ADD COLUMN token_version INT NOT NULL DEFAULT 1 AFTER is_active;

CREATE INDEX idx_users_token_version ON users (token_version);
