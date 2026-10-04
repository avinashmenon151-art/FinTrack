-- ============================================================================
-- Project: FinTrack – Personal Finance Management System
-- Database: PostgreSQL 14+ compatible
-- File: database/schema.sql
-- Description: Authoritative physical database schema implementing the finalized
--              7-relation relational design.
-- ============================================================================
--
-- IMPORTANT ARCHITECTURAL & DESIGN DECISIONS:
--
-- 1. Circular Foreign Key Dependency (users ↔ accounts):
--    A user can designate at most one account as their default account
--    (users.default_account_id -> accounts.account_id), while every account must
--    belong to an existing user (accounts.user_id -> users.user_id).
--    To eliminate insertion deadlocks, the users table is created first with
--    default_account_id as NULLable, accounts is created next, and the foreign key
--    constraint (fk_users_default_account) is added subsequently via ALTER TABLE.
--    The constraint uses ON DELETE SET NULL to safely handle account deletions.
--
-- 2. Controlled Denormalization of transactions.user_id:
--    Because accounts.account_id -> accounts.user_id, storing user_id inside
--    the transactions table introduces a transitive functional dependency:
--    (transaction_id -> account_id -> user_id), placing transactions in 2NF
--    rather than strict 3NF/BCNF.
--    This is an INTENTIONAL engineering decision to ensure:
--      a) Direct multi-tenant data isolation and permission enforcement.
--      b) High-performance filtering and indexing by user_id without joins.
--
-- 3. Composite Foreign Key for Cross-Tenant Integrity:
--    To prevent data corruption where a transaction's user_id disagrees with
--    the owner of its referenced account, accounts enforces a UNIQUE constraint
--    on (account_id, user_id). Transactions then references accounts via a
--    composite foreign key: FOREIGN KEY (account_id, user_id) REFERENCES
--    accounts(account_id, user_id).
--    Per project specification, no separate single-column FK on account_id
--    is created since the composite reference fully guarantees integrity.
--
-- 4. Composite Primary Key on budget_category:
--    The relationship between budgets and categories is Many-to-Many (M:N).
--    The allocated_amount attribute belongs to the relationship itself.
--    The bridge table budget_category enforces PRIMARY KEY (budget_id, category_id)
--    which represents the minimal candidate key in BCNF.
--
-- ============================================================================


-- ============================================================================
-- 1. DEVELOPMENT RESET SECTION
-- Safe teardown in reverse dependency order
-- ============================================================================

-- Drop the circular reference constraint first if it exists
ALTER TABLE IF EXISTS users 
    DROP CONSTRAINT IF EXISTS fk_users_default_account;

-- Drop tables in reverse dependency order
DROP TABLE IF EXISTS budget_category CASCADE;
DROP TABLE IF EXISTS transactions CASCADE;
DROP TABLE IF EXISTS savings_goals CASCADE;
DROP TABLE IF EXISTS budgets CASCADE;
DROP TABLE IF EXISTS categories CASCADE;
DROP TABLE IF EXISTS accounts CASCADE;
DROP TABLE IF EXISTS users CASCADE;


-- ============================================================================
-- 2. TABLE DEFINITIONS
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Entity 1: users
-- Represents system users and authentication credentials.
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    default_account_id INT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_email 
        UNIQUE (email)
);

COMMENT ON TABLE users IS 'Registered users of the FinTrack system';
COMMENT ON COLUMN users.default_account_id IS 'Optional reference to the user designated primary account';


-- ----------------------------------------------------------------------------
-- Entity 2: accounts
-- Financial accounts (checking, savings, credit, cash) owned by a user.
-- ----------------------------------------------------------------------------
CREATE TABLE accounts (
    account_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_accounts_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(user_id) 
        ON DELETE CASCADE,

    -- Unique composite key required to back the composite FK in transactions
    CONSTRAINT uq_accounts_account_user 
        UNIQUE (account_id, user_id)
);

COMMENT ON TABLE accounts IS 'Financial accounts owned by users';
COMMENT ON CONSTRAINT uq_accounts_account_user ON accounts IS 'Composite unique key enabling composite foreign key referencing from transactions';


-- ----------------------------------------------------------------------------
-- Circular Foreign Key Link: users.default_account_id -> accounts.account_id
-- Added after accounts table exists to prevent creation-order deadlock.
-- ----------------------------------------------------------------------------
ALTER TABLE users
    ADD CONSTRAINT fk_users_default_account
    FOREIGN KEY (default_account_id)
    REFERENCES accounts(account_id)
    ON DELETE SET NULL;


-- ----------------------------------------------------------------------------
-- Entity 3: categories
-- Taxonomies for income and expense classification.
-- Shared system defaults have user_id IS NULL; custom ones reference user_id.
-- ----------------------------------------------------------------------------
CREATE TABLE categories (
    category_id SERIAL PRIMARY KEY,
    user_id INT NULL,
    category_name VARCHAR(100) NOT NULL,
    category_type VARCHAR(20) NOT NULL,
    description TEXT,

    CONSTRAINT fk_categories_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(user_id) 
        ON DELETE CASCADE,

    CONSTRAINT chk_categories_type 
        CHECK (category_type IN ('Income', 'Expense'))
);

COMMENT ON TABLE categories IS 'Income and expense classification categories (system-wide when user_id IS NULL)';


-- ----------------------------------------------------------------------------
-- Entity 4: transactions
-- Individual income and expense events.
-- ----------------------------------------------------------------------------
CREATE TABLE transactions (
    transaction_id BIGSERIAL PRIMARY KEY,
    user_id INT NOT NULL,
    account_id INT NOT NULL,
    category_id INT NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    transaction_date DATE NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transactions_amount 
        CHECK (amount > 0),

    CONSTRAINT chk_transactions_type 
        CHECK (transaction_type IN ('Income', 'Expense')),

    -- Composite foreign key guarantees transaction account belongs to this user
    CONSTRAINT fk_transactions_account_user 
        FOREIGN KEY (account_id, user_id) 
        REFERENCES accounts(account_id, user_id) 
        ON DELETE CASCADE,

    CONSTRAINT fk_transactions_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(user_id) 
        ON DELETE CASCADE,

    CONSTRAINT fk_transactions_category 
        FOREIGN KEY (category_id) 
        REFERENCES categories(category_id) 
        ON DELETE RESTRICT
);

COMMENT ON TABLE transactions IS 'Financial transactions recorded against an account and categorized';
COMMENT ON CONSTRAINT fk_transactions_account_user ON transactions IS 'Guarantees that the transaction account belongs to the exact same user recorded on the transaction';


-- ----------------------------------------------------------------------------
-- Entity 5: budgets
-- Periodic spending plans defined by a user.
-- ----------------------------------------------------------------------------
CREATE TABLE budgets (
    budget_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL,
    budget_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_budgets_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(user_id) 
        ON DELETE CASCADE,

    CONSTRAINT chk_budgets_date_range 
        CHECK (end_date >= start_date)
);

COMMENT ON TABLE budgets IS 'Time-bounded spending plans allocated across expense categories';


-- ----------------------------------------------------------------------------
-- Entity 6: budget_category
-- Associative entity resolving M:N relationship between budgets and categories.
-- Stores category-specific monetary allocations.
-- ----------------------------------------------------------------------------
CREATE TABLE budget_category (
    budget_id INT NOT NULL,
    category_id INT NOT NULL,
    allocated_amount NUMERIC(14, 2) NOT NULL,

    CONSTRAINT pk_budget_category 
        PRIMARY KEY (budget_id, category_id),

    CONSTRAINT chk_budget_category_allocated_amount 
        CHECK (allocated_amount > 0),

    CONSTRAINT fk_budget_category_budget 
        FOREIGN KEY (budget_id) 
        REFERENCES budgets(budget_id) 
        ON DELETE CASCADE,

    CONSTRAINT fk_budget_category_category 
        FOREIGN KEY (category_id) 
        REFERENCES categories(category_id) 
        ON DELETE RESTRICT
);

COMMENT ON TABLE budget_category IS 'Associative entity mapping budget allocations to categories (M:N)';


-- ----------------------------------------------------------------------------
-- Entity 7: savings_goals
-- Targets for accumulated savings, optionally linked to a specific account.
-- ----------------------------------------------------------------------------
CREATE TABLE savings_goals (
    goal_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL,
    account_id INT NULL,
    goal_name VARCHAR(100) NOT NULL,
    target_amount NUMERIC(14, 2) NOT NULL,
    saved_amount NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    target_date DATE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_savings_goals_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(user_id) 
        ON DELETE CASCADE,

    CONSTRAINT fk_savings_goals_account 
        FOREIGN KEY (account_id) 
        REFERENCES accounts(account_id) 
        ON DELETE SET NULL,

    CONSTRAINT chk_savings_goals_target_amount 
        CHECK (target_amount > 0),

    CONSTRAINT chk_savings_goals_saved_amount 
        CHECK (saved_amount >= 0),

    CONSTRAINT chk_savings_goals_status 
        CHECK (status IN ('Active', 'Completed', 'Cancelled'))
);

COMMENT ON TABLE savings_goals IS 'Monetary savings targets with optional funding account linkage';


-- ============================================================================
-- 3. PERFORMANCE INDEXES
-- Indexing foreign keys and high-frequency filter paths
-- ============================================================================

CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_categories_user_id ON categories(user_id);
CREATE INDEX idx_transactions_user_date ON transactions(user_id, transaction_date DESC);
CREATE INDEX idx_transactions_category_id ON transactions(category_id);
CREATE INDEX idx_budgets_user_id ON budgets(user_id);
CREATE INDEX idx_budget_category_category_id ON budget_category(category_id);
CREATE INDEX idx_savings_goals_user_id ON savings_goals(user_id);
CREATE INDEX idx_savings_goals_account_id ON savings_goals(account_id);
