# FinTrack – Backend Architecture & Operational Guide

This directory houses the server-side components of **FinTrack – Personal Finance Management System**, implemented using core **Java Servlets, JDBC, Apache Tomcat, and Session-based Authentication**.

---

## 1. System Requirements

| Component | Expected / Supported Version | Remarks |
| :--- | :--- | :--- |
| **Java Development Kit (JDK)** | Java 8 minimum (Java 17 or 21 LTS recommended) | Standard Java SE runtime |
| **Servlet Container / Server** | Apache Tomcat 9.0.x (or Tomcat 10.1.x) | Tomcat 9 natively supports `javax.servlet` (Servlet 4.0). Tomcat 10+ uses `jakarta.servlet`. |
| **Database Engine** | PostgreSQL 14+ (compatible up to PostgreSQL 18) | Relational backend with ACID transactions |
| **JDBC Driver** | Current stable PostgreSQL JDBC Type 4 Driver (42.7.x series, e.g. `postgresql-42.7.13.jar`) | Located in `backend/WEB-INF/lib/` |
| **Password Hashing Library** | BCrypt (`jbcrypt-0.4.jar` / `util.BCrypt`) | Application-scoped Blowfish BCrypt (cost factor 12) |
| **Target Database Name** | `fintrack` | Initialized via `database/schema.sql` and `database/seed.sql` |
| **Frontend Development Origin** | `http://localhost:5500` | Supported by `CorsFilter` with credentials enabled |

---

## 2. Directory Structure

```text
backend/
├── src/
│   ├── controller/         # Servlet REST controllers handling HTTP requests
│   │   ├── AuthServlet.java          # Authentication & session lifecycle controller
│   │   ├── UserServlet.java          # User profile management (strictly scoped to self)
│   │   ├── AccountServlet.java       # Financial accounts (session-scoped)
│   │   ├── CategoryServlet.java      # Categories (system-shared + user custom)
│   │   ├── TransactionServlet.java   # Transactions (session-scoped & ownership-verified)
│   │   ├── BudgetServlet.java        # Budgets (session-scoped)
│   │   ├── BudgetCategoryServlet.java# Budget category allocations (ownership-verified)
│   │   └── SavingsGoalServlet.java   # Savings goals (session-scoped)
│   ├── dao/                # Data Access Objects executing parameterised JDBC queries
│   │   ├── UserDAO.java
│   │   ├── AccountDAO.java
│   │   ├── CategoryDAO.java
│   │   ├── TransactionDAO.java
│   │   ├── BudgetDAO.java
│   │   ├── BudgetCategoryDAO.java
│   │   └── SavingsGoalDAO.java
│   ├── model/              # Domain POJO entities and enum classifiers
│   │   ├── User.java, Account.java, Category.java, Transaction.java
│   │   ├── Budget.java, BudgetCategory.java, SavingsGoal.java
│   │   └── TransactionType.java, CategoryType.java, SavingsGoalStatus.java
│   ├── util/               # Cross-cutting utilities
│   │   ├── AuthUtil.java     # Session identity helper & fixation protection
│   │   ├── BCrypt.java       # OpenBSD Blowfish BCrypt password hashing & verification
│   │   ├── DBConnection.java # Centralized JDBC connection manager
│   │   └── JsonUtil.java     # Zero-dependency JSON serializer and parser (safe user sanitization)
│   └── filter/             # Servlet filters
│       ├── CorsFilter.java           # Credentialed CORS filter (development origin support)
│       └── AuthenticationFilter.java # Session gatekeeper returning 401 for unauthorized access
├── WEB-INF/
│   ├── web.xml             # Deployment descriptor mapping servlets, filters, and cookie config
│   ├── classes/            # Compiled .class bytecode destination (generated during build)
│   └── lib/                # Application-scoped dependencies (postgresql-42.7.13.jar, jbcrypt-0.4.jar)
└── README.md               # Backend documentation and operational manual
```

---

## 3. Architecture & Security Flow

```text
HTTP Request (JSON + Cookie)
       │
       ▼
CorsFilter (Origin reflection, Access-Control-Allow-Credentials: true, preflight OPTIONS)
       │
       ▼
AuthenticationFilter (Validates active session for protected endpoints, returns 401 without hitting DAOs)
       │
       ▼
Controller / Servlet (Extracts AUTHENTICATED_USER_ID from session, validates ownership, invokes DAO)
       │
       ▼
DAO (Parameterized PreparedStatement, strict relational constraints)
       │
       ▼
DBConnection (Thread-safe JDBC connection pooling / retrieval)
       │
       ▼
PostgreSQL Database ('fintrack')
```

---

## 4. Authentication & Session Management

FinTrack uses **standard Java Servlet HTTP Sessions (`HttpSession`)** and **BCrypt** password hashing. Neither JWT nor heavyweight security frameworks (Spring Security, OAuth) are introduced.

### 4.1 Password Hashing (BCrypt)
- **Algorithm**: Standard OpenBSD Blowfish BCrypt (`$2a$` prefix, cost factor `12`).
- **Registration**: Plaintext password &rarr; `BCrypt.hashpw(password, BCrypt.gensalt(12))` &rarr; `users.password_hash`.
- **Login**: Plaintext password &rarr; `BCrypt.checkpw(password, user.getPasswordHash())` &rarr; Authenticated Session.
- **Safety**: Passwords are never stored in plaintext and never logged.
- **Sanitization**: `password_hash` is **never** serialized or returned in any JSON response. `JsonUtil` guarantees this across all endpoints.

### 4.2 Dependency Configuration
- FinTrack includes a self-contained, binary-compatible BCrypt implementation in `backend/src/util/BCrypt.java`.
- Alternatively, standard `jbcrypt-0.4.jar` (Maven coordinate: `org.mindrot:jbcrypt:0.4`) can be dropped directly into `backend/WEB-INF/lib/`.

### 4.3 Session Identity (`AUTHENTICATED_USER_ID`)
- Only the integer user ID is stored in the session under the attribute key:
  ```java
  public static final String SESSION_USER_ID = "AUTHENTICATED_USER_ID";
  ```
- **Prohibited in Session**: Passwords, password hashes, database connection handles, and large model objects are never stored in the session.
- **Session Fixation Protection**: On successful authentication in `AuthUtil.setAuthenticatedUser()`, `request.changeSessionId()` is invoked to prevent session fixation attacks.
- **Cookie Security**:
  - `HttpOnly = true` configured in `backend/WEB-INF/web.xml` to prevent XSS script access to session tokens.
  - Tracking mode explicitly set to `COOKIE`.
  - In production HTTPS environments, `<secure>true</secure>` should be enabled.

### 4.4 Removal of Client `user_id` Trust
Protected endpoints **do not trust** any client-supplied `user_id` query parameter or body property.
- Ownership is derived exclusively via `AuthUtil.getAuthenticatedUserId(request)`.
- Creating an account, transaction, custom category, budget, or savings goal automatically binds `userId = authenticatedUserId`.
- Modifying or deleting resources strictly verifies that the resource belongs to `authenticatedUserId`. Attempts to manipulate another user's records result in `404 Not Found` or `403 Forbidden`.

---

## 5. API Endpoints Reference

### 5.1 Authentication Endpoints (`/api/auth/*` - Public & Session Lifecycle)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Public | Register new user with BCrypt hash (body: `name`, `email`, `password`, optional `phone_number`). Returns `201 Created` with safe user profile. |
| `POST` | `/api/auth/login` | Public | Authenticate user with email and password. Establishes authenticated `HttpSession` and returns `200 OK` with safe user profile. Returns `401 Unauthorized` on failure without revealing email existence. |
| `POST` | `/api/auth/logout` | Authenticated / Public | Invalidates the active HTTP session. Returns `200 OK` message. |
| `GET` | `/api/auth/me` | Protected | Returns the currently authenticated user's profile. Returns `401 Unauthorized` if session is missing. |

### 5.2 User Management (`/api/users/*` - Protected)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/users/me` or `/api/users/{id}` | Retrieve profile for authenticated user only (`{id}` must match authenticated session). |
| `PUT` | `/api/users/me` or `/api/users/{id}` | Update profile for authenticated user only. Plaintext passwords submitted are BCrypt-hashed before persistence. |
| `DELETE`| `/api/users/me` or `/api/users/{id}` | Delete authenticated user account and invalidate session. |

### 5.3 Financial Accounts (`/api/accounts/*` - Protected)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/accounts` | List all accounts owned by the authenticated user. |
| `GET` | `/api/accounts/{id}` | Retrieve single account (enforces authenticated user ownership). |
| `POST` | `/api/accounts` | Create new account (body: `account_name`, `account_type`, `balance`). `user_id` is assigned strictly from session. |
| `PUT` | `/api/accounts/{id}` | Update account details (enforces authenticated user ownership). |
| `PUT` | `/api/accounts/{id}/default` | Designate account as default for authenticated user (strictly verifies account ownership). |
| `DELETE`| `/api/accounts/{id}` | Delete financial account (enforces authenticated user ownership). |

### 5.4 Categories (`/api/categories/*` - Protected)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/categories` | Retrieve available categories (system categories + authenticated user's custom categories). |
| `GET` | `/api/categories/system` | Retrieve system-wide shared categories (`user_id IS NULL`). |
| `GET` | `/api/categories/{id}` | Retrieve category (accessible only if system category or owned by authenticated user). |
| `POST` | `/api/categories` | Create custom category (body: `category_name`, `category_type`, optional `description`). Owned by authenticated user. |
| `PUT` | `/api/categories/{id}` | Update custom category (system categories cannot be edited; another user's category cannot be accessed). |
| `DELETE`| `/api/categories/{id}` | Delete custom category (system categories cannot be deleted; another user's category cannot be accessed). |

### 5.5 Transactions (`/api/transactions/*` - Protected)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/transactions` | List authenticated user's transaction history. |
| `GET` | `/api/transactions?account_id={accId}` | Filter transactions by account (verifies account belongs to authenticated user). |
| `GET` | `/api/transactions?start_date=...&end_date=...` | Filter transactions by date range. |
| `GET` | `/api/transactions/{id}` | Retrieve single transaction (enforces authenticated user ownership). |
| `POST` | `/api/transactions` | Record transaction (body: `account_id`, `category_id`, `amount > 0`, `transaction_type`, `transaction_date`, `description`). Verifies account belongs to user and category is system or user-owned. |
| `PUT` | `/api/transactions/{id}` | Update transaction (enforces authenticated user ownership). |
| `DELETE`| `/api/transactions/{id}` | Delete transaction (enforces authenticated user ownership). |

### 5.6 Budgets (`/api/budgets/*` - Protected)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/budgets` | List all budgets for authenticated user. |
| `GET` | `/api/budgets/{id}` | Retrieve single budget (enforces authenticated user ownership). |
| `POST` | `/api/budgets` | Create budget (body: `budget_name`, `start_date`, `end_date`). Owned by authenticated user. |
| `PUT` | `/api/budgets/{id}` | Update budget (enforces authenticated user ownership). |
| `DELETE`| `/api/budgets/{id}` | Delete budget (enforces authenticated user ownership). |

### 5.7 Budget Category Allocations (`/api/budgets/{budgetId}/categories` or `/api/budget-categories/*` - Protected)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/budget-categories/{budgetId}` | List category allocations (strictly verifies budget belongs to authenticated user). |
| `POST` | `/api/budget-categories/{budgetId}` | Add allocation (body: `category_id`, `allocated_amount > 0`). Verifies budget ownership and category eligibility. |
| `PUT` | `/api/budget-categories/{budgetId}/{catId}`| Update allocated amount (strictly verifies budget belongs to authenticated user). |
| `DELETE`| `/api/budget-categories/{budgetId}/{catId}`| Remove category from budget (strictly verifies budget belongs to authenticated user). |

### 5.8 Savings Goals (`/api/savings-goals/*` - Protected)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/savings-goals` | List savings goals for authenticated user. |
| `GET` | `/api/savings-goals/{id}` | Retrieve single goal (enforces authenticated user ownership). |
| `POST` | `/api/savings-goals` | Create goal (body: `goal_name`, `target_amount > 0`, `saved_amount >= 0`, `status`, optional `account_id`). If `account_id` provided, verifies account belongs to authenticated user. |
| `PUT` | `/api/savings-goals/{id}` | Update goal (enforces authenticated user ownership). |
| `DELETE`| `/api/savings-goals/{id}` | Delete goal (enforces authenticated user ownership). |

---

## 6. CORS & Credential Configuration

Cross-Origin Resource Sharing is managed by [backend/src/filter/CorsFilter.java](file:///c:/Users/User1/FinTrack/FinTrack/backend/src/filter/CorsFilter.java):
- **Development Frontend Origin**: Defaults to `http://localhost:5500` (e.g., Live Server / Vite development server) and dynamically mirrors the request's `Origin` header.
- **Credentials Enabled**: `Access-Control-Allow-Credentials: true` is sent on all API responses so browsers include cookies (`JSESSIONID`) across cross-origin requests.
- **Wildcard Avoidance**: The wildcard `*` is strictly avoided in `Access-Control-Allow-Origin` because modern browsers reject credentialed requests with wildcard origins.
- **Preflight Support**: Handles HTTP `OPTIONS` requests by returning `200 OK` with allowable headers (`Content-Type`, `Authorization`, `X-Requested-With`, `Accept`, `Cookie`) and methods (`GET, POST, PUT, DELETE, OPTIONS`).

---

## 7. Build and Deployment Instructions

1. Ensure the PostgreSQL JDBC driver (`postgresql-42.7.13.jar`) is in `backend/WEB-INF/lib/`.
2. Compile Java classes:
   ```bash
   mkdir -p WEB-INF/classes
   javac -cp "WEB-INF/lib/*;<TOMCAT_HOME>/lib/servlet-api.jar" -d WEB-INF/classes src/**/*.java
   ```
3. Deploy the application:
   - Copy `backend/` directory to `<TOMCAT_HOME>/webapps/fintrack` or create a WAR package (`jar -cvf fintrack.war -C backend/ .`).
4. Start Tomcat:
   ```bash
   <TOMCAT_HOME>/bin/startup.bat
   ```
5. API endpoints will be accessible at:
   ```text
   http://localhost:8080/fintrack/api/...
   ```
