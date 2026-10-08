# FinTrack – Personal Finance Management System

A full-stack personal finance management application designed to help users track expenses, manage budgets, monitor income, and achieve savings goals.

---

## 1. Project Overview

**FinTrack** is designed as a modular, three-tier enterprise-grade personal finance system. Built using a lightweight, standard-compliant architecture, FinTrack combines a high-performance **Java Servlet/JDBC** backend with a modern, responsive **Vanilla HTML5/CSS3/JavaScript (ES6+)** web client and a relational **PostgreSQL** persistence layer.

The project emphasizes clean architecture, multi-tenant session isolation, strict relational integrity (composite foreign keys), and complete absence of heavyweight external frameworks (no Spring, no React, no ORM overhead).

---

## 2. Technology Stack

| Layer | Technology / Tools | Details |
| :--- | :--- | :--- |
| **Backend** | Java Servlets (Servlet 4.0 / `javax.servlet`), JDBC | Core Java SE runtime; raw parameterized PreparedStatement queries |
| **Web Server / Container** | Apache Tomcat 9.0.x (or Tomcat 10.1.x) | HTTP servlet container and session manager |
| **Database** | PostgreSQL 14+ (tested up to PostgreSQL 18) | Relational database with composite constraints and ACID transactions |
| **Security & Auth** | Standard HTTP Sessions (`HttpSession`), BCrypt | Cost factor 12 password hashing; `HttpOnly` session cookies |
| **Frontend** | HTML5, CSS3, Vanilla JavaScript (ES6+) | Asynchronous Fetch client, responsive dark-theme design |
| **CLI Application** | Java (Core CLI) | Scaffolded console utility module |

---

## 3. Project Structure

```text
FinTrack/
├── backend/                # Server-side components (Servlets, DAOs, Models, Filters, web.xml)
│   ├── src/
│   │   ├── controller/     # AuthServlet, AccountServlet, TransactionServlet, BudgetServlet, etc.
│   │   ├── dao/            # Parameterized JDBC DAOs with multi-tenant ownership enforcement
│   │   ├── filter/         # CorsFilter (credentialed CORS), AuthenticationFilter (session gatekeeper)
│   │   ├── model/          # User, Account, Transaction, Category, Budget, SavingsGoal POJOs & Enums
│   │   └── util/           # DBConnection, AuthUtil, BCrypt, JsonUtil
│   ├── WEB-INF/            # Deployment descriptor (web.xml), lib/, classes/
│   └── README.md           # Backend architecture, API, and deployment documentation
├── database/               # Relational persistence layer
│   ├── schema.sql          # DDL: ENUMs, tables, composite constraints, indexes, triggers
│   ├── seed.sql            # Seed dataset: system categories, demo users, transactions, budgets
│   └── README.md           # Database setup, reset procedure, and integrity documentation
├── frontend/               # Presentation layer (Vanilla HTML5, CSS3, ES6 JavaScript)
│   ├── assets/             # Brand logos and iconography
│   ├── css/style.css       # Unified modern dark-theme stylesheet
│   ├── js/                 # api.js, auth.js, dashboard.js, accounts.js, transactions.js, etc.
│   ├── index.html          # Sign-in portal
│   ├── register.html       # User registration
│   ├── dashboard.html      # Central financial overview and analytics
│   ├── accounts.html       # Financial accounts & balances
│   ├── categories.html     # System & custom categories
│   ├── transactions.html   # Transaction ledger & filtering
│   ├── budgets.html        # Budget planning & category allocation
│   ├── savings-goals.html  # Target savings goals & account links
│   └── README.md           # Frontend guide, serving options, and troubleshooting
├── cli-app/                # Console-based utility module (scaffolded)
│   ├── src/                # Console commands and views
│   ├── output/             # Statement exports directory
│   └── README.md           # CLI documentation
├── docs/                   # Engineering documentation
│   ├── API.md              # Comprehensive REST API endpoint contract
│   ├── relational-schema.md# Schema data dictionary
│   ├── normalization.md    # 3NF normalization proofs
│   └── ER-Diagram.png      # Entity-Relationship diagram
├── .gitignore              # Git ignore rules for bytecode, logs, and environments
└── README.md               # Master project overview and execution manual
```

---

## 4. Database Setup

FinTrack requires a running PostgreSQL instance (v14+).

### Step 1: Create Database
```bash
createdb -U postgres fintrack
```
*Or execute in `psql`:*
```sql
CREATE DATABASE fintrack;
```

### Step 2: Execute Schema DDL
Execute `database/schema.sql` against the `fintrack` database:
```bash
psql -U postgres -d fintrack -f database/schema.sql
```
*This defines ENUM types, core tables, the circular user-default-account foreign key, and the multi-column composite ownership constraints on `transactions`.*

### Step 3: Execute Seed Data
Load default lookup categories and realistic test accounts:
```bash
psql -U postgres -d fintrack -f database/seed.sql
```

---

## 5. Backend Setup & Execution

### Prerequisites
- **JDK**: Java 8 or higher (Java 17 / 21 LTS recommended).
- **Tomcat**: Apache Tomcat 9.0.x (natively supports `javax.servlet` / Servlet 4.0).
- **PostgreSQL JDBC Driver**: Download `postgresql-42.7.x.jar` and place it in `backend/WEB-INF/lib/`.

### Configuration
`util.DBConnection` reads configuration from environment variables or Java system properties:
```bash
# Environment variables (optional, defaults to postgres user on localhost:5432)
export DB_URL="jdbc:postgresql://localhost:5432/fintrack"
export DB_USER="postgres"
export DB_PASSWORD="YourPostgresPassword"
```

### Compilation & Deployment
1. Navigate to the `backend/` directory:
   ```bash
   cd backend
   ```
2. Compile Java sources into `WEB-INF/classes`:
   ```bash
   mkdir -p WEB-INF/classes
   javac -cp "WEB-INF/lib/*;<TOMCAT_HOME>/lib/servlet-api.jar" -d WEB-INF/classes src/**/*.java
   ```
3. Deploy to Tomcat:
   - Deploy as an unpacked webapp: Copy the `backend/` directory to `<TOMCAT_HOME>/webapps/fintrack`.
   - *Or* package into a WAR archive:
     ```bash
     jar -cvf fintrack.war -C backend/ .
     ```
     Place `fintrack.war` into `<TOMCAT_HOME>/webapps/`.
4. Start Apache Tomcat:
   ```bash
   <TOMCAT_HOME>/bin/startup.bat      # Windows
   <TOMCAT_HOME>/bin/startup.sh       # Linux / macOS
   ```
   The backend API will be live at `http://localhost:8080/fintrack/api/...` (or `http://localhost:8080/api/...` if deployed to ROOT).

---

## 6. Frontend Setup & Execution

> [!IMPORTANT]
> **DO NOT OPEN PAGES USING `file://` PROTOCOL.**
> Web browsers do not attach cross-origin session cookies (`credentials: "include"`) when accessing local file URLs (`file:///C:/...`).
> The frontend **must** be served over HTTP.

### Running with Python
1. Open a terminal in the `frontend/` directory:
   ```bash
   cd frontend
   python -m http.server 5500
   ```
2. Open your browser and navigate to:
   ```text
   http://localhost:5500
   ```

### Running with VS Code Live Server
- Open the FinTrack project in VS Code.
- Right-click `frontend/index.html` and click **"Open with Live Server"** (default port `5500`).

---

## 7. Authentication & Security Model

1. **Session-Based Authentication**:
   - Authentication establishes a standard server-side `HttpSession`.
   - The session token is transmitted via an `HttpOnly` cookie (`JSESSIONID`).
   - No sensitive tokens or passwords are stored in browser storage (`localStorage` / `sessionStorage`).
2. **Password Security**:
   - User passwords are encrypted with OpenBSD Blowfish **BCrypt** (cost factor 12).
   - Password hashes (`password_hash`) are **never serialized or exposed** in API responses.
3. **Session-Bound Ownership**:
   - Controllers **never trust client-supplied `user_id` values**.
   - Every read, create, update, and delete operation is strictly scoped to `AUTHENTICATED_USER_ID` extracted from the server session.
4. **Relational Isolation**:
   - `transactions` enforces a composite foreign key `(account_id, user_id)` referencing `accounts(account_id, user_id)`.
   - Cross-user data manipulation attempts are rejected with `401 Unauthorized`, `403 Forbidden`, or `404 Not Found`.

---

## 8. Seeded Demo Accounts

For demonstration, development, and evaluation purposes, the following sample profiles are provided in `database/seed.sql`:

| User Name | Email Address | Demo Password | Default Account |
| :--- | :--- | :--- | :--- |
| **Avinash Menon** | `avinash@example.com` | `TestPassword123!` | HDFC Salary Account (Checking) |
| **Rahul Sharma** | `rahul@example.com` | `TestPassword123!` | SBI Savings Account (Savings) |
| **Ananya Nair** | `ananya@example.com` | `TestPassword123!` | ICICI Direct Account (Checking) |

*You can also register a brand-new user at any time via `register.html`.*

---

## 9. Architectural Note: Account Balances

- **Current Implementation (Snapshot Balance)**: `accounts.balance` reflects the balance manually specified by the user or updated through account settings. Creating, editing, or deleting records in `transactions` operates on the transaction ledger without mutating `accounts.balance`.
- **Reasoning**: Accommodates initial bank balance snapshots without requiring opening historical adjustment entries.
- **Future Roadmap**: Automated ledger synchronization via atomic database triggers (`AFTER INSERT/UPDATE/DELETE ON transactions`) and row locking (`FOR UPDATE`).

---

## 10. CLI Module Status

The `cli-app/` directory houses the **FinTrack Command-Line Interface (CLI)**, a functional secondary interface sharing the same PostgreSQL database, backend DAOs, and BCrypt security layer. See [cli-app/README.md](cli-app/README.md) for full compilation, configuration, and terminal execution instructions.
