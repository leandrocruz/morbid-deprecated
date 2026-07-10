-- Mirrors the change applied to the new morbid backend:
-- (1) Drop the UNIQUE on accounts.name (display names no longer have to be unique).
-- (2) Add nullable `identifier` (CPF/CNPJ) with a partial unique index — uniqueness only
--     applies when the value is present, so existing rows with NULL identifier are unaffected.
--
-- Idempotent: each statement guards on existence so re-runs are safe.

BEGIN;

ALTER TABLE accounts
    DROP CONSTRAINT IF EXISTS accounts_name_key;

ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS identifier VARCHAR(256);

CREATE UNIQUE INDEX IF NOT EXISTS accounts_identifier_key
    ON accounts (identifier)
    WHERE identifier IS NOT NULL;

COMMIT;
