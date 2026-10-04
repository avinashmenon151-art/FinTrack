# FinTrack – Database Architecture & Operational Guide

This directory contains the relational database definitions, seed scripts, and architectural documentation for the **FinTrack Personal Finance Management System**, powered by **PostgreSQL**.

---

## 1. Prerequisites & Engine Requirements

- **Database Engine**: PostgreSQL 14.0 or higher (tested and verified up to PostgreSQL 18.x).
- **Default Database Name**: `fintrack`
- **Default Port**: `5432`
- **Encoding**: `UTF-8`

---

## 2. Directory Structure

```text
database/
├── schema.sql      # Complete DDL: Enums, tables, composite constraints, indexes, triggers
├── seed.sql        # Realistic multi-user seed dataset with demo users, accounts, transactions
└── README.md       # Database setup guide, constraints reference, and architecture notes
```

---

## 3. Database Initialization & Execution Order

To initialize FinTrack from scratch, execute the SQL scripts in dependency order:

### Step 1: Create Database
Connect to PostgreSQL using the `psql` command-line utility or pgAdmin:

```sql
CREATE DATABASE fintrack;
```

Or from the command line:
```bash
createdb -U postgres fintrack
```

### Step 2: Execute Schema DDL (`schema.sql`)
Execute `schema.sql` against the `fintrack` database:

```bash
psql -U postgres -d fintrack -f database/schema.sql
```

#### DDL Execution Order & Circular Dependency Handling:
1. **Custom ENUM Types**:
   - `account_type` (`CHECKING`, `SAVINGS`, `CREDIT_CARD`, `INVESTMENT`, `CASH`, `LOAN`, `OTHER`)
   - `category_type` (`INCOME`, `EXPENSE`)
   - `transaction_type` (`INCOME`, `EXPENSE`, `TRANSFER`)
   - `savings_goal_status` (`IN_PROGRESS`, `ACHIEVED`, `CANCELLED`)
2. **Independent Core Entities**:
   - `users`: Core profile table created without immediate FK to `accounts` to avoid circular creation dependency.
   - `categories`: System-wide lookup (`user_id IS NULL`) and user-specific custom categories (`user_id REFERENCES users(user_id)`).
3. **Accounts Table (`accounts`)**:
   - Contains `account_id`, `user_id REFERENCES users(user_id) ON DELETE CASCADE`, `balance`, `account_type`.
   - **Composite Key Constraint**: `UNIQUE (account_id, user_id)` established to enable downstream multi-column composite foreign keys.
4. **Resolution of Circular `users` &harr; `accounts` Dependency**:
   - `users.default_account_id` is linked via `ALTER TABLE` after `accounts` exists:
     ```sql
     ALTER TABLE users
         ADD CONSTRAINT fk_users_default_account
         FOREIGN KEY (default_account_id)
         REFERENCES accounts(account_id)
         ON DELETE SET NULL;
     ```
   - When an account designated as default is deleted, the user's `default_account_id` cleanly resets to `NULL` without cascading user deletion.
5. **Dependent Entities**:
   - `transactions`: References `categories(category_id)` and enforces multi-tenant safety via a **composite foreign key**:
     ```sql
     CONSTRAINT fk_transactions_account_user
         FOREIGN KEY (account_id, user_id)
         REFERENCES accounts(account_id, user_id)
         ON DELETE CASCADE
     ```
     This strictly guarantees at the database level that a transaction cannot belong to User A while referencing an account owned by User B.
   - `budgets`: Belongs to `users(user_id)`.
   - `budget_categories`: Many-to-many link table with composite primary key `(budget_id, category_id)`.
   - `savings_goals`: Belongs to `users(user_id)` with optional link to `accounts(account_id)`.
6. **Performance Indexes & Triggers**:
   - B-Tree indexes on `transactions(user_id, transaction_date)`, `transactions(account_id)`, `categories(user_id)`, and `budgets(user_id)`.
   - `update_updated_at_column()` triggers automatically maintain `updated_at` timestamps on row updates.

### Step 3: Execute Seed Data (`seed.sql`)
Load baseline reference data and multi-user test profiles:

```bash
psql -U postgres -d fintrack -f database/seed.sql
```

The seed script provides:
- Standard system categories (Food, Rent, Utilities, Salary, Investments, etc.).
- 3 realistic user profiles (`avinash@example.com`, `rahul@example.com`, `ananya@example.com`) with BCrypt password hashes.
- Seeded checking, savings, and credit accounts.
- Seeded income/expense transactions, monthly budgets with category allocations, and active savings goals.

---

## 4. Database Reset Procedure

To cleanly wipe all existing data and reset auto-increment primary key sequences to 1:

```sql
TRUNCATE TABLE
    budget_categories,
    transactions,
    savings_goals,
    budgets,
    accounts,
    categories,
    users
RESTART IDENTITY CASCADE;
```

Then re-run `database/seed.sql`.

Alternatively, drop and recreate the database:
```bash
dropdb -U postgres fintrack
createdb -U postgres fintrack
psql -U postgres -d fintrack -f database/schema.sql
psql -U postgres -d fintrack -f database/seed.sql
```

---

## 5. Relational Integrity & Security Model

| Constraint / Rule | Implementation | Architectural Purpose |
| :--- | :--- | :--- |
| **Cross-Tenant Isolation** | `UNIQUE (account_id, user_id)` on `accounts`<br>`FK (account_id, user_id)` on `transactions` | Prevents data leakage where transactions point to foreign accounts. |
| **System vs Custom Taxonomy** | `categories.user_id` nullable (`NULL` = system-wide) | System categories are immutable and shared; custom categories are private. |
| **Monetary Validity** | `CHECK (amount > 0)`<br>`CHECK (allocated_amount > 0)`<br>`CHECK (target_amount > 0)`<br>`CHECK (saved_amount >= 0)` | Disallows zero or negative transaction amounts and budget limits. |
| **Date Range Sanity** | `CHECK (end_date >= start_date)` | Prevents invalid chronological budget intervals. |
| **Cascade Behavior** | `ON DELETE CASCADE` on child tables<br>`ON DELETE SET NULL` on `default_account_id` | Deleting a user cleans up all accounts/transactions cleanly without foreign key lockouts. |

---

## 6. Architectural Note: Account Balance Management

### Current Implementation (Pattern A: Snapshot Balance)
In the current FinTrack implementation:
- `accounts.balance` represents the current balance snapshot as configured by the user upon account creation or during manual balance reconciliation.
- Creating, updating, or deleting a record in `transactions` **operates strictly on the `transactions` table** and does not alter `accounts.balance`.

### Rationale
- Personal finance users frequently begin using tracking software with existing bank balances where prior transaction history is unrecorded.
- Snapshot balances allow direct reconciliation with bank statements without requiring backdated adjustment transactions.

### Recommended Future Evolution (Pattern B: Derived / Ledger Balance)
If automated balance synchronization is desired in future releases:
1. Introduce an explicit `OPENING_BALANCE` transaction type created automatically upon account creation.
2. Implement atomic database triggers (`AFTER INSERT/UPDATE/DELETE ON transactions`) to update `accounts.balance` in the same database transaction.
3. Utilize `SELECT balance FROM accounts WHERE account_id = ? FOR UPDATE` row-level locking in `TransactionDAO` to prevent concurrent write race conditions.
