-- SAGA with Liberty – PostgreSQL initialisation script
-- Run once against the 'sagadb' database.

CREATE TABLE IF NOT EXISTS accounts (
    id             SERIAL PRIMARY KEY,
    account_number VARCHAR(32)    NOT NULL UNIQUE,
    owner_name     VARCHAR(128)   NOT NULL,
    balance        NUMERIC(19, 4) NOT NULL DEFAULT 0.00,
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Trigger to keep updated_at current
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER accounts_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Seed data for testing
INSERT INTO accounts (account_number, owner_name, balance) VALUES
    ('ACC-001', 'Alice Smith',  1000.00),
    ('ACC-002', 'Bob Jones',    500.00),
    ('ACC-003', 'Carol White',  2500.00)
ON CONFLICT (account_number) DO NOTHING;
