# FinTrack – Database Layer

## Overview

The database layer of **FinTrack** utilizes **PostgreSQL** to provide relational persistence, ACID transaction support, and data integrity for all financial records.

---

## Directory Organization

```text
database/
├── schema.sql      # DDL scripts defining tables, primary/foreign keys, indexes, and constraints
├── seed.sql        # Initial reference data (default categories, sample transactions for testing)
└── README.md       # Database setup guide and documentation
```

---

## Core Entities (Planned)

- **Users**: Authentication credentials and user profile information.
- **Accounts**: Banking, credit, and cash accounts tied to specific users.
- **Transactions**: Logged credits and debits linked to accounts and categories.
- **Categories**: Taxonomies for income and expense classification.
- **Budgets**: Category-based periodic spending caps.
- **Savings Goals**: Target amounts, milestones, and target completion dates.

---

## Execution Guidelines

1. Ensure PostgreSQL is installed and the database service is running.
2. Create the FinTrack database:
   ```sql
   CREATE DATABASE fintrack_db;
   ```
3. Execute `schema.sql` to establish tables and constraints.
4. Execute `seed.sql` to load baseline lookup data.
