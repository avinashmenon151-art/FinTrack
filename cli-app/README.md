# FinTrack – Command-Line Interface (CLI)

The **FinTrack CLI** is a terminal-based interface for managing personal finances in FinTrack. It serves as a **secondary interface** alongside the primary web application, communicating directly with the shared PostgreSQL database via JDBC, backend models, and Data Access Objects (DAOs).

---

## 1. Architectural Role & Shared Database

> [!IMPORTANT]
> - **Secondary Interface**: The FinTrack Web Application (`frontend/` + `backend/`) remains the **primary** user interface. The CLI is designed as an alternative, lightweight console client for rapid ledger entries, offline or headless environments, and direct terminal management.
> - **Shared Database**: Both the Web Application and the CLI operate on the **exact same PostgreSQL database** (`fintrack`). Changes made in the CLI are immediately visible in the web app, and vice versa. There is **no separate database** for the CLI.
> - **Shared Domain Layer**: The CLI directly reuses the backend models (`model.*`), parameterized DAOs (`dao.*`), database connection manager (`util.DBConnection`), and BCrypt authentication library (`org.mindrot.jbcrypt.BCrypt`).

---

## 2. Key Features

1. **Secure Authentication**:
   - Authenticates against the PostgreSQL `users` table using OpenBSD Blowfish **BCrypt** (cost factor 12).
   - Masked password input via `java.io.Console.readPassword()` (with a safe `Scanner` fallback for IDE terminals).
   - Password hashes are never printed or logged; plaintext passwords are never stored.
   - User runtime session is strictly tracked in memory (`SessionState`).

2. **Real-Time Financial Dashboard**:
   - Total Net Worth aggregated across user accounts.
   - Total logged income, total logged expenses, and net savings balance.
   - Active savings goals counter and total target amount.
   - Overview of user accounts (with default account indicator) and 5 most recent transactions.
   - One-click export of financial summary reports to `cli-app/output/`.

3. **Multi-Tenant User Isolation**:
   - Every operation is strictly scoped to the authenticated user's ID.
   - Users cannot view, modify, or delete accounts, transactions, custom categories, budgets, or savings goals belonging to other users.
   - Attempts to access unowned IDs are cleanly rejected with user-friendly error notices.

4. **Account Management**:
   - View all accounts belonging to the authenticated user.
   - Create accounts with support for multiple types: `Checking`, `Savings`, `Cash`, `Credit Card`, `Investment`, `Loan`, `Other`.
   - Update account names and balances.
   - Set the user's default account.
   - Delete accounts (validates that no transactions or active goals depend on the account).

5. **Transaction Management**:
   - View chronological transaction history.
   - Filter transactions by specific account.
   - Filter transactions by custom date range (`YYYY-MM-DD`).
   - Record new Income or Expense transactions with real-time account and category lookups.
   - Update transaction amounts, dates, descriptions, categories, and accounts.
   - Delete transactions with confirmation.

6. **Category Management**:
   - View default system categories (flagged as **READ-ONLY**).
   - View user-defined custom categories.
   - Add custom categories with Income or Expense classification.
   - Edit names of custom categories.
   - Delete custom categories (system categories are protected from modification or deletion).

7. **Budget Planning & Category Allocations**:
   - Create monthly or periodic budgets with start and end dates (`end_date >= start_date`).
   - View detailed budget category allocations and total allocated funds.
   - Add category-specific spending caps to a budget.
   - Update category allocations.
   - Remove category allocations.
   - Update or delete entire budgets.

8. **Savings Goals Tracking**:
   - List savings goals with calculated progress percentages (`%`) and remaining amounts needed.
   - Create savings goals with target amounts, optional deadlines, and linked accounts.
   - Record deposits/contributions to update the saved amount.
   - Update goal status (`Active`, `Completed`, `Cancelled`).
   - Delete savings goals with confirmation.

---

## 3. Directory Structure

```text
cli-app/
├── bin/            # Compiled bytecode (.class files, ignored by Git)
├── output/         # Exported financial summaries (e.g. summary_user2_*.txt)
├── src/            # CLI source code
│   └── cli/
│       ├── AccountsView.java      # Account CRUD and default account setting
│       ├── BudgetsView.java       # Budgets and category allocation management
│       ├── CategoriesView.java    # System and custom category viewing/editing
│       ├── ConsoleUtil.java       # Terminal input helpers, formatting, password reader
│       ├── DashboardView.java     # Financial dashboard and text file exporter
│       ├── FinTrackCli.java       # Main entry point, DB check, and login flow
│       ├── SavingsGoalsView.java  # Savings goals, progress %, and deposits
│       ├── SessionState.java      # Runtime authenticated user state & isolation
│       └── TransactionsView.java   # Transaction CRUD, account/date range filters
└── README.md       # CLI documentation and operating instructions
```

---

## 4. Requirements & Prerequisites

- **Operating System**: Windows (PowerShell or CMD), macOS, or Linux.
- **Java Runtime**: JDK 8 or higher (tested with JDK 8 `1.8.0_504`).
- **PostgreSQL**: PostgreSQL 14 or higher running on `localhost:5432` with database `fintrack`.
- **Backend Libraries**: The CLI reuses JAR dependencies located in `backend/WEB-INF/lib/`:
  - `postgresql-42.7.13.jar` (PostgreSQL JDBC driver)
  - `jbcrypt-0.4.jar` (BCrypt password hashing)

---

## 5. Configuration

The CLI utilizes the backend `util.DBConnection` class, which respects standard environment variables or system properties:

| Setting | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- |
| **JDBC URL** | `DB_URL` | `jdbc:postgresql://localhost:5432/fintrack` | PostgreSQL connection string |
| **DB Username** | `DB_USER` | `postgres` | Database superuser or application user |
| **DB Password** | `DB_PASSWORD` | *(empty string)* | Database user password |

### Setting Environment Variables in Windows PowerShell:
```powershell
$env:DB_PASSWORD="YourPostgresPassword"
```

### Setting Environment Variables in Windows Command Prompt (CMD):
```cmd
set DB_PASSWORD=YourPostgresPassword
```

---

## 6. Compilation & Execution (Windows PowerShell)

Ensure your terminal is opened in the project root directory (`FinTrack/`).

### Step 1: Set Database Password (if required)
```powershell
$env:DB_PASSWORD="YourPostgresPassword"
```

### Step 2: Compile CLI Application
```powershell
# Create bin directory if it doesn't exist
if (!(Test-Path "cli-app\bin")) { New-Item -ItemType Directory -Path "cli-app\bin" | Out-Null }

# Compile CLI source files alongside backend DAOs, models, and utilities
$srcFiles = (Get-ChildItem -Recurse -Filter *.java cli-app\src | ForEach-Object { $_.FullName })
javac -sourcepath "cli-app\src;backend\src" -cp "backend\WEB-INF\lib\*" -d cli-app\bin $srcFiles
```

### Step 3: Run the CLI
```powershell
java -cp "cli-app\bin;backend\WEB-INF\lib\*" cli.FinTrackCli
```

---

## 7. Demo Credentials

The shared database includes demo accounts seeded via `database/seed.sql`:

| User Name | Email Address | Password | Default Account |
| :--- | :--- | :--- | :--- |
| **Rahul Sharma** | `rahul@example.com` | `TestPassword123!` | ICICI Premium Checking |
| **Avinash Menon** | `avinash@example.com` | `TestPassword123!` | HDFC Salary Account |
| **Ananya Nair** | `ananya@example.com` | `TestPassword123!` | ICICI Direct Account |

---

## 8. Example Menu Flow

### Startup & Login:
```text
================================================================
                 FINTRACK - FINANCIAL TRACKER                   
                  Command-Line Interface (CLI)                  
================================================================
  Secure personal finance management for your terminal.
  Connected to shared FinTrack PostgreSQL database.
================================================================

Connecting to FinTrack database... [CONNECTED]

================================================================================
  USER LOGIN
================================================================================
Enter your credentials to access FinTrack, or enter 0 at email to exit.

Email address: rahul@example.com
Password: ********
[SUCCESS] Welcome back, Rahul Sharma!
Signed in as: rahul@example.com (User ID: 2)
```

### Main Menu:
```text
================================================================================
  FINTRACK MAIN MENU - RAHUL SHARMA
================================================================================
 1. Dashboard
 2. Accounts
 3. Transactions
 4. Categories
 5. Budgets
 6. Savings Goals
 7. Logout
 8. Exit
--------------------------------------------------------------------------------
Select an option (1-8):
```

### Dashboard View:
```text
================================================================================
  FINANCIAL DASHBOARD
================================================================================
User:  Rahul Sharma (rahul@example.com)
Time:  2026-10-08 19:36:00
--------------------------------------------------------------------------------
  Total Net Worth (Accounts) : ₹ 272,000.00
  Total Logged Income       : ₹ 116,800.00
  Total Logged Expenses     : ₹ 50,000.00
  Net Savings Balance       : ₹ 66,800.00
  Active Savings Goals      : 1 (Total Target: ₹ 250,000.00)
--------------------------------------------------------------------------------
YOUR ACCOUNTS (2):
  - #4   ICICI Premium Checking (Checking    ) :  ₹ 62,000.00 [DEFAULT]
  - #5   Axis High-Yield Savings (Savings     ) : ₹ 210,000.00
--------------------------------------------------------------------------------
RECENT TRANSACTIONS (Latest 5):
  - 2026-10-04 | #18   | Expense | -₹ 1,800.00 | Dental Cleaning & Consultation
  - 2026-10-04 | #17   | Income  | +₹ 6,800.00 | Quarterly Mutual Fund Dividend
...
```

---

## 9. Current Limitations

1. **Interactive Terminal Input**: Designed for interactive terminals. Automated piped input scripts should ensure appropriate pauses or carriage returns between menu options.
2. **Account Balances**: As documented in the master project architecture, account balances reflect current balance snapshots. Inserting transactions records ledger entries without auto-mutating `accounts.balance` (consistent with backend design).
3. **Password Masking in IDEs**: Standard Java `System.console()` is null in some third-party IDE embedded run consoles. When executed inside such an IDE terminal, `ConsoleUtil` automatically falls back to `Scanner` to ensure functionality without throwing exceptions.
