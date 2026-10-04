# FinTrack – Relational Schema Specification

This document defines the authoritative relational schema for **FinTrack – Personal Finance Management System** targeted for PostgreSQL. It establishes the 7 core relations, data types, integrity constraints, foreign key actions, and business rule mappings.

---

## 1. Schema Notation Overview

```text
USER(
    user_id [PK],
    name,
    email [UQ],
    password_hash,
    phone_number,
    default_account_id [FK NULL -> ACCOUNT.account_id],
    created_at
)

ACCOUNT(
    account_id [PK],
    user_id [FK -> USER.user_id],
    account_name,
    account_type,
    balance,
    created_at
)

CATEGORY(
    category_id [PK],
    user_id [FK NULL -> USER.user_id],
    category_name,
    category_type,
    description
)

TRANSACTION(
    transaction_id [PK],
    user_id [FK -> USER.user_id],
    account_id [FK -> ACCOUNT.account_id],
    category_id [FK -> CATEGORY.category_id],
    amount,
    transaction_type,
    transaction_date,
    description,
    created_at
)

BUDGET(
    budget_id [PK],
    user_id [FK -> USER.user_id],
    budget_name,
    start_date,
    end_date,
    created_at
)

BUDGET_CATEGORY(
    budget_id [PK, FK -> BUDGET.budget_id],
    category_id [PK, FK -> CATEGORY.category_id],
    allocated_amount
)

SAVINGS_GOAL(
    goal_id [PK],
    user_id [FK -> USER.user_id],
    account_id [FK NULL -> ACCOUNT.account_id],
    goal_name,
    target_amount,
    saved_amount,
    target_date,
    status,
    created_at
)
```

---

## 2. Detailed Table Specifications

### 2.1 Table: `users`
Represents an individual registered on the platform. Stores security credentials, contact details, and an optional reference to their designated default account.

| Column | Data Type | Nullable | Constraints & Defaults | Description |
| :--- | :--- | :--- | :--- | :--- |
| `user_id` | `SERIAL` / `INT` | **No** | **PRIMARY KEY** | Unique surrogate identifier for the user |
| `name` | `VARCHAR(100)` | **No** | — | Full name of the user |
| `email` | `VARCHAR(255)` | **No** | **UNIQUE** | User login email (case-insensitive indexing recommended) |
| `password_hash` | `VARCHAR(255)` | **No** | — | Cryptographic hash of user password (e.g. BCrypt / Argon2) |
| `phone_number` | `VARCHAR(20)` | Yes | — | Optional contact telephone number |
| `default_account_id` | `INT` | Yes | **FK** -> `accounts(account_id)` | Designated default financial account (nullable) |
| `created_at` | `TIMESTAMPTZ` | **No** | `DEFAULT CURRENT_TIMESTAMP` | Account creation timestamp |

- **Foreign Keys**:
  - `default_account_id` REFERENCES `accounts(account_id)` ON DELETE SET NULL ON UPDATE CASCADE
- **Candidate Keys**: `{user_id}`, `{email}`

---

### 2.2 Table: `accounts`
Represents a financial account owned by a user (e.g., checking, savings, cash, credit card).

| Column | Data Type | Nullable | Constraints & Defaults | Description |
| :--- | :--- | :--- | :--- | :--- |
| `account_id` | `SERIAL` / `INT` | **No** | **PRIMARY KEY** | Unique surrogate identifier for the account |
| `user_id` | `INT` | **No** | **FK** -> `users(user_id)` | Owner user identifier |
| `account_name` | `VARCHAR(100)` | **No** | — | User-defined label (e.g. "Chase Main Checking") |
| `account_type` | `VARCHAR(50)` | **No** | — | Account class (e.g. 'Checking', 'Savings', 'Credit Card', 'Cash') |
| `balance` | `NUMERIC(14,2)` | **No** | `DEFAULT 0.00` | Current balance with currency precision |
| `created_at` | `TIMESTAMPTZ` | **No** | `DEFAULT CURRENT_TIMESTAMP` | Account opening / creation timestamp |

- **Foreign Keys**:
  - `user_id` REFERENCES `users(user_id)` ON DELETE CASCADE ON UPDATE CASCADE
- **Candidate Keys**: `{account_id}`

---

### 2.3 Table: `categories`
Taxonomy applied to categorize income and expenses. Supports both system-wide shared categories (`user_id IS NULL`) and user-defined custom categories.

| Column | Data Type | Nullable | Constraints & Defaults | Description |
| :--- | :--- | :--- | :--- | :--- |
| `category_id` | `SERIAL` / `INT` | **No** | **PRIMARY KEY** | Unique surrogate identifier for the category |
| `user_id` | `INT` | Yes | **FK** -> `users(user_id)` | Owner user ID; NULL for default system categories |
| `category_name` | `VARCHAR(100)` | **No** | — | Category display name (e.g., 'Groceries', 'Salary') |
| `category_type` | `VARCHAR(20)` | **No** | `CHECK (category_type IN ('Income', 'Expense'))` | Transaction flow classification |
| `description` | `TEXT` | Yes | — | Optional explanation or user note |

- **Foreign Keys**:
  - `user_id` REFERENCES `users(user_id)` ON DELETE CASCADE ON UPDATE CASCADE
- **Candidate Keys**: `{category_id}`

---

### 2.4 Table: `transactions`
Logs individual income and expense events against a specific user, account, and category.

| Column | Data Type | Nullable | Constraints & Defaults | Description |
| :--- | :--- | :--- | :--- | :--- |
| `transaction_id` | `BIGSERIAL` / `BIGINT` | **No** | **PRIMARY KEY** | Unique transaction identifier |
| `user_id` | `INT` | **No** | **FK** -> `users(user_id)` | Owner user ID (retained for data isolation) |
| `account_id` | `INT` | **No** | **FK** -> `accounts(account_id)` | Financial account debited or credited |
| `category_id` | `INT` | **No** | **FK** -> `categories(category_id)` | Associated budget/expense category |
| `amount` | `NUMERIC(14,2)` | **No** | `CHECK (amount > 0)` | Monetary value of the transaction |
| `transaction_type` | `VARCHAR(20)` | **No** | `CHECK (transaction_type IN ('Income', 'Expense'))` | Direction of funds |
| `transaction_date` | `DATE` | **No** | — | Date on which the transaction occurred |
| `description` | `VARCHAR(255)` | Yes | — | Optional payee or note description |
| `created_at` | `TIMESTAMPTZ` | **No** | `DEFAULT CURRENT_TIMESTAMP` | System recording timestamp |

- **Foreign Keys**:
  - `user_id` REFERENCES `users(user_id)` ON DELETE CASCADE ON UPDATE CASCADE
  - `account_id` REFERENCES `accounts(account_id)` ON DELETE CASCADE ON UPDATE CASCADE
  - `category_id` REFERENCES `categories(category_id)` ON DELETE RESTRICT ON UPDATE CASCADE
- **Candidate Keys**: `{transaction_id}`

---

### 2.5 Table: `budgets`
Represents spending limits planned by a user for defined date ranges (e.g., monthly budgets).

| Column | Data Type | Nullable | Constraints & Defaults | Description |
| :--- | :--- | :--- | :--- | :--- |
| `budget_id` | `SERIAL` / `INT` | **No** | **PRIMARY KEY** | Unique surrogate identifier for the budget |
| `user_id` | `INT` | **No** | **FK** -> `users(user_id)` | Owner user identifier |
| `budget_name` | `VARCHAR(100)` | **No** | — | Label (e.g. "October 2026 Household Budget") |
| `start_date` | `DATE` | **No** | — | Effective starting date |
| `end_date` | `DATE` | **No** | `CHECK (end_date >= start_date)` | Effective ending date |
| `created_at` | `TIMESTAMPTZ` | **No** | `DEFAULT CURRENT_TIMESTAMP` | Record creation timestamp |

- **Foreign Keys**:
  - `user_id` REFERENCES `users(user_id)` ON DELETE CASCADE ON UPDATE CASCADE
- **Candidate Keys**: `{budget_id}`

---

### 2.6 Table: `budget_categories`
Associative bridge table implementing the Many-to-Many ($M:N$) relationship between `budgets` and `categories`. Stores category-specific allocations.

| Column | Data Type | Nullable | Constraints & Defaults | Description |
| :--- | :--- | :--- | :--- | :--- |
| `budget_id` | `INT` | **No** | **PK, FK** -> `budgets(budget_id)` | Target budget reference |
| `category_id` | `INT` | **No** | **PK, FK** -> `categories(category_id)` | Included category reference |
| `allocated_amount`| `NUMERIC(14,2)` | **No** | `CHECK (allocated_amount > 0)` | Spending limit allocated to category |

- **Primary Key**: `PRIMARY KEY (budget_id, category_id)`
- **Foreign Keys**:
  - `budget_id` REFERENCES `budgets(budget_id)` ON DELETE CASCADE ON UPDATE CASCADE
  - `category_id` REFERENCES `categories(category_id)` ON DELETE RESTRICT ON UPDATE CASCADE
- **Candidate Keys**: `{(budget_id, category_id)}`

---

### 2.7 Table: `savings_goals`
Tracks target financial accumulations over time, with an optional link to a dedicated savings or funding account.

| Column | Data Type | Nullable | Constraints & Defaults | Description |
| :--- | :--- | :--- | :--- | :--- |
| `goal_id` | `SERIAL` / `INT` | **No** | **PRIMARY KEY** | Unique goal identifier |
| `user_id` | `INT` | **No** | **FK** -> `users(user_id)` | Owner user identifier |
| `account_id` | `INT` | Yes | **FK** -> `accounts(account_id)` | Optional linked funding account |
| `goal_name` | `VARCHAR(100)` | **No** | — | Label (e.g. "Emergency Fund", "Graduation Trip") |
| `target_amount` | `NUMERIC(14,2)` | **No** | `CHECK (target_amount > 0)` | Target financial total |
| `saved_amount` | `NUMERIC(14,2)` | **No** | `CHECK (saved_amount >= 0)` | Currently accumulated amount (default 0.00) |
| `target_date` | `DATE` | **No** | — | Planned completion deadline |
| `status` | `VARCHAR(20)` | **No** | `CHECK (status IN ('Active', 'Completed', 'Cancelled'))` | Goal tracking lifecycle state |
| `created_at` | `TIMESTAMPTZ` | **No** | `DEFAULT CURRENT_TIMESTAMP` | Record creation timestamp |

- **Foreign Keys**:
  - `user_id` REFERENCES `users(user_id)` ON DELETE CASCADE ON UPDATE CASCADE
  - `account_id` REFERENCES `accounts(account_id)` ON DELETE SET NULL ON UPDATE CASCADE
- **Candidate Keys**: `{goal_id}`

---

## 3. Entity Relationships and Cardinality Matrix

| Parent Entity | Child Entity | Relationship Name | Cardinality | Min..Max Participation | Foreign Key Column |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `USER` | `ACCOUNT` | Owns | 1 : $N$ | 1..1 : 0..$N$ | `accounts.user_id` |
| `USER` | `ACCOUNT` | Designates Default | 0..1 : 0..1 | 0..1 : 0..1 | `users.default_account_id` |
| `USER` | `CATEGORY` | Defines (Custom) | 1 : $N$ | 0..1 : 0..$N$ | `categories.user_id` (NULL for system) |
| `USER` | `TRANSACTION`| Logs / Owns | 1 : $N$ | 1..1 : 0..$N$ | `transactions.user_id` |
| `USER` | `BUDGET` | Plans | 1 : $N$ | 1..1 : 0..$N$ | `budgets.user_id` |
| `USER` | `SAVINGS_GOAL`| Tracks | 1 : $N$ | 1..1 : 0..$N$ | `savings_goals.user_id` |
| `ACCOUNT` | `TRANSACTION`| Records | 1 : $N$ | 1..1 : 0..$N$ | `transactions.account_id` |
| `ACCOUNT` | `SAVINGS_GOAL`| Funds (Optional) | 1 : $N$ | 0..1 : 0..$N$ | `savings_goals.account_id` |
| `CATEGORY`| `TRANSACTION`| Classifies | 1 : $N$ | 1..1 : 0..$N$ | `transactions.category_id` |
| `BUDGET` | `BUDGET_CATEGORY`| Includes | 1 : $N$ | 1..1 : 1..$N$ | `budget_categories.budget_id` |
| `CATEGORY`| `BUDGET_CATEGORY`| Covered By | 1 : $N$ | 1..1 : 0..$N$ | `budget_categories.category_id` |

---

## 4. Key Design Considerations and Trade-Offs

### 4.1 Resolution of Circular Foreign Key Dependency (`USER` ↔ `ACCOUNT`)
- **The Issue**: `USER.default_account_id` points to `ACCOUNT.account_id`, while `ACCOUNT.user_id` points to `USER.user_id`. When creating a brand-new user, no account exists yet, making it impossible to insert a non-null `default_account_id` referencing an `account_id` that cannot exist until the user is created.
- **The Solution**: 
  1. `users.default_account_id` is defined as **`NULLable`**.
  2. On user registration, a new `users` record is inserted with `default_account_id = NULL`.
  3. Once the user creates their first account (or multiple accounts), they can update `users.default_account_id` to point to their chosen default account.
  4. The constraint uses `ON DELETE SET NULL`: if the default account is deleted, the user's `default_account_id` becomes `NULL` without deleting the user.
  5. Application logic / business rules must ensure `default_account_id` points to an account where `account.user_id = user.user_id`.

### 4.2 Controlled Denormalization of `user_id` in `TRANSACTION`
- **Theoretical Dependency**: `transaction_id → account_id` and `account_id → user_id`, creating a transitive functional dependency `transaction_id → account_id → user_id`.
- **Engineering Decision**: `user_id` is intentionally retained in `TRANSACTION`.
  - **Security & Multi-Tenant Isolation**: FinTrack queries filter transactions by `user_id` in virtually every request (dashboards, monthly totals, export generation). Having `user_id` on `TRANSACTION` permits direct indexing `(user_id, transaction_date DESC)` and prevents multi-table join overhead on high-frequency queries.
  - **Data Integrity**: Integrity can be guarded by enforcing a composite foreign key `FOREIGN KEY (account_id, user_id) REFERENCES accounts(account_id, user_id)` or database-level check triggers.
