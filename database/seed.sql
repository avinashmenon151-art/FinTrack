-- ============================================================================
-- Project: FinTrack – Personal Finance Management System
-- Database: PostgreSQL 14+ compatible
-- File: database/seed.sql
-- Description: Realistic baseline seed and test data for development and testing.
-- ============================================================================
--
-- IMPORTANT SEED DATA DESIGN NOTES:
--
-- 1. Development & Testing Purpose:
--    All passwords stored below use standard bcrypt placeholder hashes.
--    DO NOT use these hashes or credentials in production environments.
--
-- 2. System vs Custom Categories:
--    - Shared system categories have user_id IS NULL (accessible to all users).
--    - Custom categories are explicitly tied to the creating user's user_id.
--
-- 3. Circular Dependency Resolution (users ↔ accounts):
--    - Users are inserted initially with default_account_id = NULL.
--    - Accounts are inserted with user_id referencing the created users.
--    - Users are then updated to assign their respective default_account_id.
--    - Every default account belongs to the exact same user.
--
-- 4. Cross-Tenant Integrity (transactions ↔ accounts):
--    Every transaction's user_id matches the owner (user_id) of its account,
--    satisfying the composite foreign key (account_id, user_id).
--
-- 5. Idempotent Execution:
--    This script safely resets existing data via TRUNCATE ... RESTART IDENTITY CASCADE
--    allowing repeatable development testing without dropping the schema.
--
-- ============================================================================


-- ============================================================================
-- 1. DEVELOPMENT DATA RESET (IDEMPOTENCY)
-- ============================================================================

-- Disengage circular dependency before truncation
UPDATE users SET default_account_id = NULL;

-- Truncate all tables in reverse dependency order and reset sequence counters
TRUNCATE TABLE 
    budget_category,
    transactions,
    savings_goals,
    budgets,
    categories,
    accounts,
    users
RESTART IDENTITY CASCADE;


-- ============================================================================
-- 2. USERS
-- 3 realistic test profiles with bcrypt development password hashes
-- Placeholder password for all 3 users: "TestPassword123!"
-- ============================================================================

INSERT INTO users (name, email, password_hash, phone_number, default_account_id, created_at)
VALUES
    (
        'Avinash Menon',
        'avinash@example.com',
        '$2a$12$o1O4OQapHgraUWn4ByukvOY7HdaYpkjBZpfhY/bCy7Pa3kRmn4g3C',
        '+91 9876543210',
        NULL,
        '2026-09-01 09:00:00+05:30'
    ),
    (
        'Rahul Sharma',
        'rahul@example.com',
        '$2a$12$T.bCrsfanYj9DoTfTVn67.5okMMuQIrQBG6CwQvYIjPj9FsGSncgq',
        '+91 9812345678',
        NULL,
        '2026-09-01 10:15:00+05:30'
    ),
    (
        'Ananya Nair',
        'ananya@example.com',
        '$2a$12$Va5S9y14PW.SiWt4CQGTIuLWXFV/IyDTOjc9QOgqkhQPWjuTb3nLm',
        '+91 9845012345',
        NULL,
        '2026-09-02 11:30:00+05:30'
    );


-- ============================================================================
-- 3. CATEGORIES
-- Part A: System Shared Categories (user_id IS NULL)
-- Part B: Custom Categories (user_id IS NOT NULL)
-- ============================================================================

-- Part A: System Categories (user_id IS NULL)
INSERT INTO categories (user_id, category_name, category_type, description)
VALUES
    -- Income Categories
    (NULL, 'Salary', 'Income', 'Monthly employment and professional salary'),
    (NULL, 'Freelance', 'Income', 'Freelance consulting, side projects, and contract income'),
    (NULL, 'Investment', 'Income', 'Dividends, capital gains, and bank interest payouts'),

    -- Expense Categories
    (NULL, 'Food', 'Expense', 'Groceries, supermarkets, restaurants, and food delivery'),
    (NULL, 'Transport', 'Expense', 'Public transit, fuel, rideshare, and vehicle maintenance'),
    (NULL, 'Rent', 'Expense', 'Apartment rental and housing lease payments'),
    (NULL, 'Utilities', 'Expense', 'Electricity, water, cooking gas, and high-speed internet bills'),
    (NULL, 'Entertainment', 'Expense', 'Movies, streaming subscriptions, gaming, and leisure'),
    (NULL, 'Shopping', 'Expense', 'Clothing, consumer electronics, and home essentials'),
    (NULL, 'Healthcare', 'Expense', 'Medical appointments, pharmaceuticals, and health insurance');

-- Part B: Custom User-Owned Categories
INSERT INTO categories (user_id, category_name, category_type, description)
VALUES
    -- Avinash custom category
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        'College',
        'Expense',
        'University tuition, academic textbooks, supplies, and certifications'
    ),
    -- Rahul custom category
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        'Gym',
        'Expense',
        'Fitness club membership, personal training, and workout supplements'
    ),
    -- Ananya custom category
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        'Travel',
        'Expense',
        'Airline tickets, hotel stays, and international vacation trips'
    );


-- ============================================================================
-- 4. ACCOUNTS
-- Multiple accounts per user (Checking, Savings, Cash)
-- ============================================================================

-- Avinash Accounts (User 1)
INSERT INTO accounts (user_id, account_name, account_type, balance, created_at)
VALUES
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        'HDFC Salary Checking',
        'Checking',
        45000.00,
        '2026-09-01 09:30:00+05:30'
    ),
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        'SBI Growth Savings',
        'Savings',
        120000.00,
        '2026-09-01 09:35:00+05:30'
    ),
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        'Physical Cash Wallet',
        'Cash',
        3500.00,
        '2026-09-01 09:40:00+05:30'
    );

-- Rahul Accounts (User 2)
INSERT INTO accounts (user_id, account_name, account_type, balance, created_at)
VALUES
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        'ICICI Premium Checking',
        'Checking',
        62000.00,
        '2026-09-01 10:30:00+05:30'
    ),
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        'Axis High-Yield Savings',
        'Savings',
        210000.00,
        '2026-09-01 10:35:00+05:30'
    );

-- Ananya Accounts (User 3)
INSERT INTO accounts (user_id, account_name, account_type, balance, created_at)
VALUES
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        'Kotak Everyday Checking',
        'Checking',
        38000.00,
        '2026-09-02 11:45:00+05:30'
    ),
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        'HDFC Secure Savings',
        'Savings',
        95000.00,
        '2026-09-02 11:50:00+05:30'
    ),
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        'Pocket Cash',
        'Cash',
        2000.00,
        '2026-09-02 11:55:00+05:30'
    );


-- ============================================================================
-- 5. UPDATE DEFAULT ACCOUNTS
-- Assign each user's designated primary account (strictly user-owned)
-- ============================================================================

UPDATE users
SET default_account_id = (
    SELECT account_id FROM accounts 
    WHERE user_id = users.user_id AND account_name = 'HDFC Salary Checking'
)
WHERE email = 'avinash@example.com';

UPDATE users
SET default_account_id = (
    SELECT account_id FROM accounts 
    WHERE user_id = users.user_id AND account_name = 'ICICI Premium Checking'
)
WHERE email = 'rahul@example.com';

UPDATE users
SET default_account_id = (
    SELECT account_id FROM accounts 
    WHERE user_id = users.user_id AND account_name = 'Kotak Everyday Checking'
)
WHERE email = 'ananya@example.com';


-- ============================================================================
-- 6. TRANSACTIONS
-- Realistic transactions across all 3 users (Income and Expense)
-- Integrity: user_id always matches account.user_id
-- ============================================================================

-- ----------------------------------------------------------------------------
-- User 1: Avinash Menon Transactions (9 records)
-- ----------------------------------------------------------------------------
INSERT INTO transactions (user_id, account_id, category_id, amount, transaction_type, transaction_date, description, created_at)
VALUES
    -- Salary deposit
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'HDFC Salary Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'avinash@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Salary' AND user_id IS NULL),
        85000.00, 'Income', '2026-09-30', 'September Monthly Salary Credit', '2026-09-30 18:00:00+05:30'
    ),
    -- Monthly Rent
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'HDFC Salary Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'avinash@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Rent' AND user_id IS NULL),
        18000.00, 'Expense', '2026-10-01', 'Apartment Rent Transfer', '2026-10-01 09:30:00+05:30'
    ),
    -- Supermarket Food
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'HDFC Salary Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'avinash@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Food' AND user_id IS NULL),
        3200.00, 'Expense', '2026-10-02', 'Weekly Supermarket Groceries', '2026-10-02 12:15:00+05:30'
    ),
    -- Metro commute
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'HDFC Salary Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'avinash@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Transport' AND user_id IS NULL),
        1450.00, 'Expense', '2026-10-02', 'Metro SmartCard Monthly Recharge', '2026-10-02 08:45:00+05:30'
    ),
    -- Electricity Bill
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'HDFC Salary Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'avinash@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Utilities' AND user_id IS NULL),
        2100.00, 'Expense', '2026-10-03', 'Electricity Bill Payment', '2026-10-03 14:00:00+05:30'
    ),
    -- University Tuition (Custom Category: College)
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'SBI Growth Savings' AND user_id = (SELECT user_id FROM users WHERE email = 'avinash@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'College' AND user_id = (SELECT user_id FROM users WHERE email = 'avinash@example.com')),
        12500.00, 'Expense', '2026-10-03', 'Semester Course Material & Exam Fees', '2026-10-03 15:30:00+05:30'
    ),
    -- Movie tickets with cash
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'Physical Cash Wallet' AND user_id = (SELECT user_id FROM users WHERE email = 'avinash@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Entertainment' AND user_id IS NULL),
        950.00, 'Expense', '2026-10-03', 'Cinema Tickets & Snacks', '2026-10-03 21:00:00+05:30'
    ),
    -- Freelance client payment
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'SBI Growth Savings' AND user_id = (SELECT user_id FROM users WHERE email = 'avinash@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Freelance' AND user_id IS NULL),
        15000.00, 'Income', '2026-10-04', 'Web Development Consulting Milestone', '2026-10-04 11:00:00+05:30'
    ),
    -- Food street snacks with cash
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'Physical Cash Wallet' AND user_id = (SELECT user_id FROM users WHERE email = 'avinash@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Food' AND user_id IS NULL),
        450.00, 'Expense', '2026-10-04', 'Cafe Coffee & Pastries', '2026-10-04 17:30:00+05:30'
    );

-- ----------------------------------------------------------------------------
-- User 2: Rahul Sharma Transactions (9 records)
-- ----------------------------------------------------------------------------
INSERT INTO transactions (user_id, account_id, category_id, amount, transaction_type, transaction_date, description, created_at)
VALUES
    -- Salary deposit
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'ICICI Premium Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'rahul@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Salary' AND user_id IS NULL),
        110000.00, 'Income', '2026-09-30', 'Corporate Tech Salary Deposit', '2026-09-30 18:30:00+05:30'
    ),
    -- Monthly Rent
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'ICICI Premium Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'rahul@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Rent' AND user_id IS NULL),
        25000.00, 'Expense', '2026-10-01', 'Penthouse Studio Rent', '2026-10-01 10:00:00+05:30'
    ),
    -- Dining Out
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'ICICI Premium Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'rahul@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Food' AND user_id IS NULL),
        5400.00, 'Expense', '2026-10-01', 'Weekend Dinner with Team', '2026-10-01 20:30:00+05:30'
    ),
    -- Gym Membership (Custom Category: Gym)
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'ICICI Premium Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'rahul@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Gym' AND user_id = (SELECT user_id FROM users WHERE email = 'rahul@example.com')),
        4000.00, 'Expense', '2026-10-02', 'CrossFit Quarterly Membership', '2026-10-02 07:15:00+05:30'
    ),
    -- Shopping Mall
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'ICICI Premium Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'rahul@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Shopping' AND user_id IS NULL),
        8500.00, 'Expense', '2026-10-02', 'Winter Apparel Shopping', '2026-10-02 16:45:00+05:30'
    ),
    -- Fiber Broadband
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'ICICI Premium Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'rahul@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Utilities' AND user_id IS NULL),
        3200.00, 'Expense', '2026-10-03', 'Fiber Internet Bill', '2026-10-03 11:30:00+05:30'
    ),
    -- Fuel / Uber rides
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'ICICI Premium Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'rahul@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Transport' AND user_id IS NULL),
        2100.00, 'Expense', '2026-10-03', 'Vehicle Fuel Station', '2026-10-03 13:00:00+05:30'
    ),
    -- Mutual Fund Dividend
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'Axis High-Yield Savings' AND user_id = (SELECT user_id FROM users WHERE email = 'rahul@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Investment' AND user_id IS NULL),
        6800.00, 'Income', '2026-10-04', 'Quarterly Mutual Fund Dividend', '2026-10-04 10:00:00+05:30'
    ),
    -- Dental Checkup
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'Axis High-Yield Savings' AND user_id = (SELECT user_id FROM users WHERE email = 'rahul@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Healthcare' AND user_id IS NULL),
        1800.00, 'Expense', '2026-10-04', 'Dental Cleaning & Consultation', '2026-10-04 16:00:00+05:30'
    );

-- ----------------------------------------------------------------------------
-- User 3: Ananya Nair Transactions (8 records)
-- ----------------------------------------------------------------------------
INSERT INTO transactions (user_id, account_id, category_id, amount, transaction_type, transaction_date, description, created_at)
VALUES
    -- Salary deposit
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'Kotak Everyday Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'ananya@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Salary' AND user_id IS NULL),
        75000.00, 'Income', '2026-09-30', 'Design Studio Salary Deposit', '2026-09-30 18:00:00+05:30'
    ),
    -- Rent
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'Kotak Everyday Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'ananya@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Rent' AND user_id IS NULL),
        16000.00, 'Expense', '2026-10-01', 'Co-living Shared Rent', '2026-10-01 09:00:00+05:30'
    ),
    -- Groceries
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'Kotak Everyday Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'ananya@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Food' AND user_id IS NULL),
        2800.00, 'Expense', '2026-10-01', 'Organic Market Groceries', '2026-10-01 17:15:00+05:30'
    ),
    -- Flight Tickets (Custom Category: Travel)
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'HDFC Secure Savings' AND user_id = (SELECT user_id FROM users WHERE email = 'ananya@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Travel' AND user_id = (SELECT user_id FROM users WHERE email = 'ananya@example.com')),
        14500.00, 'Expense', '2026-10-02', 'Goa Vacation Flight Booking', '2026-10-02 11:20:00+05:30'
    ),
    -- Theatre play with cash
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'Pocket Cash' AND user_id = (SELECT user_id FROM users WHERE email = 'ananya@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Entertainment' AND user_id IS NULL),
        1200.00, 'Expense', '2026-10-02', 'Art House Theatre Entry', '2026-10-02 19:30:00+05:30'
    ),
    -- Online Shopping
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'Kotak Everyday Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'ananya@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Shopping' AND user_id IS NULL),
        4300.00, 'Expense', '2026-10-03', 'Online Design Books & Decor', '2026-10-03 15:45:00+05:30'
    ),
    -- Auto / Cab fares
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'Kotak Everyday Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'ananya@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Transport' AND user_id IS NULL),
        980.00, 'Expense', '2026-10-03', 'Rideshare Trips to Client Meeting', '2026-10-03 18:20:00+05:30'
    ),
    -- UI Design Freelance Project
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'Kotak Everyday Checking' AND user_id = (SELECT user_id FROM users WHERE email = 'ananya@example.com')),
        (SELECT category_id FROM categories WHERE category_name = 'Freelance' AND user_id IS NULL),
        12000.00, 'Income', '2026-10-04', 'Brand Identity Freelance Payment', '2026-10-04 14:10:00+05:30'
    );


-- ============================================================================
-- 7. BUDGETS
-- Monthly and periodic budgets (end_date >= start_date)
-- ============================================================================

INSERT INTO budgets (user_id, budget_name, start_date, end_date, created_at)
VALUES
    -- Avinash Budget
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        'October 2026 Student Living Budget',
        '2026-10-01',
        '2026-10-31',
        '2026-09-28 10:00:00+05:30'
    ),
    -- Rahul Budget
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        'Q4 2026 Living & Fitness Budget',
        '2026-10-01',
        '2026-12-31',
        '2026-09-29 11:30:00+05:30'
    ),
    -- Ananya Budget
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        'Autumn 2026 Lifestyle & Travel Budget',
        '2026-10-01',
        '2026-10-31',
        '2026-09-30 09:15:00+05:30'
    );


-- ============================================================================
-- 8. BUDGET_CATEGORY
-- Category-specific allocations for each budget (allocated_amount > 0)
-- ============================================================================

-- Avinash Budget Allocations
INSERT INTO budget_category (budget_id, category_id, allocated_amount)
VALUES
    (
        (SELECT budget_id FROM budgets WHERE budget_name = 'October 2026 Student Living Budget'),
        (SELECT category_id FROM categories WHERE category_name = 'Food' AND user_id IS NULL),
        8000.00
    ),
    (
        (SELECT budget_id FROM budgets WHERE budget_name = 'October 2026 Student Living Budget'),
        (SELECT category_id FROM categories WHERE category_name = 'Transport' AND user_id IS NULL),
        3500.00
    ),
    (
        (SELECT budget_id FROM budgets WHERE budget_name = 'October 2026 Student Living Budget'),
        (SELECT category_id FROM categories WHERE category_name = 'Entertainment' AND user_id IS NULL),
        3000.00
    ),
    (
        (SELECT budget_id FROM budgets WHERE budget_name = 'October 2026 Student Living Budget'),
        (SELECT category_id FROM categories WHERE category_name = 'College' AND user_id = (SELECT user_id FROM users WHERE email = 'avinash@example.com')),
        15000.00
    );

-- Rahul Budget Allocations
INSERT INTO budget_category (budget_id, category_id, allocated_amount)
VALUES
    (
        (SELECT budget_id FROM budgets WHERE budget_name = 'Q4 2026 Living & Fitness Budget'),
        (SELECT category_id FROM categories WHERE category_name = 'Food' AND user_id IS NULL),
        15000.00
    ),
    (
        (SELECT budget_id FROM budgets WHERE budget_name = 'Q4 2026 Living & Fitness Budget'),
        (SELECT category_id FROM categories WHERE category_name = 'Shopping' AND user_id IS NULL),
        12000.00
    ),
    (
        (SELECT budget_id FROM budgets WHERE budget_name = 'Q4 2026 Living & Fitness Budget'),
        (SELECT category_id FROM categories WHERE category_name = 'Transport' AND user_id IS NULL),
        6000.00
    ),
    (
        (SELECT budget_id FROM budgets WHERE budget_name = 'Q4 2026 Living & Fitness Budget'),
        (SELECT category_id FROM categories WHERE category_name = 'Gym' AND user_id = (SELECT user_id FROM users WHERE email = 'rahul@example.com')),
        5000.00
    );

-- Ananya Budget Allocations
INSERT INTO budget_category (budget_id, category_id, allocated_amount)
VALUES
    (
        (SELECT budget_id FROM budgets WHERE budget_name = 'Autumn 2026 Lifestyle & Travel Budget'),
        (SELECT category_id FROM categories WHERE category_name = 'Food' AND user_id IS NULL),
        7000.00
    ),
    (
        (SELECT budget_id FROM budgets WHERE budget_name = 'Autumn 2026 Lifestyle & Travel Budget'),
        (SELECT category_id FROM categories WHERE category_name = 'Entertainment' AND user_id IS NULL),
        4000.00
    ),
    (
        (SELECT budget_id FROM budgets WHERE budget_name = 'Autumn 2026 Lifestyle & Travel Budget'),
        (SELECT category_id FROM categories WHERE category_name = 'Shopping' AND user_id IS NULL),
        6000.00
    ),
    (
        (SELECT budget_id FROM budgets WHERE budget_name = 'Autumn 2026 Lifestyle & Travel Budget'),
        (SELECT category_id FROM categories WHERE category_name = 'Travel' AND user_id = (SELECT user_id FROM users WHERE email = 'ananya@example.com')),
        20000.00
    );


-- ============================================================================
-- 9. SAVINGS GOALS
-- Realistic goals across users with active/completed/cancelled states
-- target_amount > 0, saved_amount >= 0
-- ============================================================================

INSERT INTO savings_goals (user_id, account_id, goal_name, target_amount, saved_amount, target_date, status, created_at)
VALUES
    -- Avinash Goal 1: Emergency Fund (Active, linked to SBI Growth Savings)
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'SBI Growth Savings' AND user_id = (SELECT user_id FROM users WHERE email = 'avinash@example.com')),
        'Emergency Reserve Fund',
        150000.00,
        85000.00,
        '2027-06-30',
        'Active',
        '2026-09-05 10:00:00+05:30'
    ),
    -- Avinash Goal 2: MacBook Upgrade (Completed, account_id IS NULL demonstrating optional link)
    (
        (SELECT user_id FROM users WHERE email = 'avinash@example.com'),
        NULL,
        'MacBook Pro M-Series Upgrade',
        120000.00,
        120000.00,
        '2026-09-30',
        'Completed',
        '2026-06-01 09:00:00+05:30'
    ),
    -- Rahul Goal 3: Vacation Fund (Active, linked to Axis High-Yield Savings)
    (
        (SELECT user_id FROM users WHERE email = 'rahul@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'Axis High-Yield Savings' AND user_id = (SELECT user_id FROM users WHERE email = 'rahul@example.com')),
        'European Summer Holiday',
        250000.00,
        90000.00,
        '2027-08-15',
        'Active',
        '2026-09-10 11:30:00+05:30'
    ),
    -- Ananya Goal 4: New Phone (Active, linked to HDFC Secure Savings)
    (
        (SELECT user_id FROM users WHERE email = 'ananya@example.com'),
        (SELECT account_id FROM accounts WHERE account_name = 'HDFC Secure Savings' AND user_id = (SELECT user_id FROM users WHERE email = 'ananya@example.com')),
        'Flagship Smartphone Purchase',
        130000.00,
        45000.00,
        '2027-03-31',
        'Active',
        '2026-09-15 14:00:00+05:30'
    );
