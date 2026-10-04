# FinTrack – RESTful API Specification

## 1. Architectural Principles & Security Overview

FinTrack uses a RESTful Java Servlet architecture deployed on Apache Tomcat. All stateful identity management is handled server-side via HTTP sessions.

### Key Security & Convention Rules:
1. **Authenticated Session Authority**: Client identity is determined exclusively from the server-side HTTP session cookie (`JSESSIONID`).
2. **No Client-Supplied User IDs**: Clients must NEVER transmit `user_id` in request query strings or JSON payloads. Any client-supplied user identifiers are ignored; all database operations strictly bind to the authenticated session user ID.
3. **CORS & Credentials**: The backend enforces credentialed CORS (`Access-Control-Allow-Credentials: true`) with an explicit whitelist of trusted development origins (e.g., `http://localhost:5500`, `http://127.0.0.1:5500`, `http://localhost:3000`). Wildcard origins (`*`) are disallowed.
4. **Credential Confidentiality**: Password hashes (`password_hash`) and plaintext passwords are never serialized in API responses.
5. **JSON Field Compatibility**: Responses provide both standard `snake_case` aliases and legacy `camelCase` keys to ensure maximum compatibility.

---

## 2. Authentication Endpoints (`/api/auth/*`)

Handled by `controller.AuthServlet`.

### 2.1 User Registration
- **Endpoint**: `POST /api/auth/register`
- **Access**: Public
- **Request Body**:
  ```json
  {
    "name": "Avinash Menon",
    "email": "avinash@example.com",
    "password": "SecurePassword123!",
    "phone_number": "+91 9876543210"
  }
  ```
- **Responses**:
  - `201 Created`: User created and session established.
    ```json
    {
      "user_id": 1,
      "name": "Avinash Menon",
      "email": "avinash@example.com",
      "phone_number": "+91 9876543210",
      "default_account_id": null,
      "created_at": "2026-10-05T09:00:00Z"
    }
    ```
  - `400 Bad Request`: Validation failure (missing required fields, password < 6 characters).
  - `409 Conflict`: User with this email already exists.

### 2.2 User Login
- **Endpoint**: `POST /api/auth/login`
- **Access**: Public
- **Request Body**:
  ```json
  {
    "email": "avinash@example.com",
    "password": "SecurePassword123!"
  }
  ```
- **Responses**:
  - `200 OK`: Authentication successful, session cookie set. Returns User object.
  - `400 Bad Request`: Missing email or password.
  - `401 Unauthorized`: Invalid email or password.

### 2.3 User Logout
- **Endpoint**: `POST /api/auth/logout`
- **Access**: Public / Safe
- **Response**:
  - `200 OK`: `{"message": "Logged out successfully"}` (Session invalidated).

### 2.4 Authenticated Profile
- **Endpoint**: `GET /api/auth/me`
- **Access**: Protected (requires active session)
- **Response**:
  - `200 OK`: Current authenticated user representation.
  - `401 Unauthorized`: Session expired or unauthenticated.

---

## 3. Financial Accounts (`/api/accounts/*`)

Handled by `controller.AccountServlet`.

### 3.1 List User Accounts
- **Endpoint**: `GET /api/accounts`
- **Access**: Protected
- **Response**: `200 OK`
  ```json
  [
    {
      "account_id": 1,
      "user_id": 1,
      "account_name": "Checking Account",
      "account_type": "Checking",
      "balance": 25000.00,
      "created_at": "2026-10-01T09:00:00Z"
    }
  ]
  ```

### 3.2 Get Account by ID
- **Endpoint**: `GET /api/accounts/{id}`
- **Access**: Protected (Strict user ownership enforced)
- **Response**: `200 OK` or `404 Not Found`.

### 3.3 Create Account
- **Endpoint**: `POST /api/accounts`
- **Access**: Protected
- **Request Body**:
  ```json
  {
    "account_name": "HDFC Savings",
    "account_type": "Savings",
    "balance": 50000.00
  }
  ```
- **Response**: `201 Created`

### 3.4 Update Account
- **Endpoint**: `PUT /api/accounts/{id}`
- **Access**: Protected
- **Request Body**:
  ```json
  {
    "account_name": "HDFC Salary Savings",
    "account_type": "Savings",
    "balance": 52500.00
  }
  ```
- **Response**: `200 OK`

### 3.5 Set Default Account
- **Endpoint**: `PUT /api/accounts/{id}/default`
- **Access**: Protected
- **Response**: `200 OK`: `{"message": "Default account updated to 1"}`

### 3.6 Delete Account
- **Endpoint**: `DELETE /api/accounts/{id}`
- **Access**: Protected
- **Response**: `200 OK` on success, or `400 Bad Request` if linked foreign keys restrict deletion.

---

## 4. Categories (`/api/categories/*`)

Handled by `controller.CategoryServlet`.

### 4.1 List Available Categories
- **Endpoint**: `GET /api/categories`
- **Access**: Protected
- **Description**: Returns all shared system categories (`user_id IS NULL`) plus the current user's custom categories.
- **Response**: `200 OK`
  ```json
  [
    {
      "category_id": 1,
      "user_id": null,
      "category_name": "Salary",
      "category_type": "Income",
      "description": "Monthly employment salary"
    },
    {
      "category_id": 15,
      "user_id": 1,
      "category_name": "Pet Care",
      "category_type": "Expense",
      "description": "Veterinary and pet supplies"
    }
  ]
  ```

### 4.2 List System Categories Only
- **Endpoint**: `GET /api/categories/system`
- **Access**: Protected
- **Response**: `200 OK`

### 4.3 Create Custom Category
- **Endpoint**: `POST /api/categories`
- **Access**: Protected
- **Request Body**:
  ```json
  {
    "category_name": "Gym & Fitness",
    "category_type": "Expense",
    "description": "Membership and supplements"
  }
  ```
- **Response**: `201 Created`

### 4.4 Update Custom Category
- **Endpoint**: `PUT /api/categories/{id}`
- **Access**: Protected (Only custom categories owned by the session user)
- **Response**: `200 OK`

### 4.5 Delete Custom Category
- **Endpoint**: `DELETE /api/categories/{id}`
- **Access**: Protected (Only custom categories owned by the session user)
- **Response**: `200 OK`

---

## 5. Transactions (`/api/transactions/*`)

Handled by `controller.TransactionServlet`.

### 5.1 List & Filter Transactions
- **Endpoints**:
  - `GET /api/transactions` (All transactions for user)
  - `GET /api/transactions?account_id={accountId}` (Filter by owned account)
  - `GET /api/transactions?start_date=YYYY-MM-DD&end_date=YYYY-MM-DD` (Filter by date window)
- **Access**: Protected
- **Response**: `200 OK`
  ```json
  [
    {
      "transaction_id": 101,
      "user_id": 1,
      "account_id": 2,
      "category_id": 5,
      "amount": 750.00,
      "transaction_type": "Expense",
      "transaction_date": "2026-10-05",
      "description": "Groceries",
      "created_at": "2026-10-05T12:30:00Z"
    }
  ]
  ```

### 5.2 Get Transaction by ID
- **Endpoint**: `GET /api/transactions/{id}`
- **Access**: Protected (Strict user ownership verified)
- **Response**: `200 OK` or `404 Not Found`.

### 5.3 Create Transaction
- **Endpoint**: `POST /api/transactions`
- **Access**: Protected
- **Request Body**:
  ```json
  {
    "account_id": 2,
    "category_id": 5,
    "amount": 1250.00,
    "transaction_type": "Expense",
    "transaction_date": "2026-10-05",
    "description": "Weekly Supermarket"
  }
  ```
- **Response**: `201 Created`

### 5.4 Update Transaction
- **Endpoint**: `PUT /api/transactions/{id}`
- **Access**: Protected
- **Response**: `200 OK`

### 5.5 Delete Transaction
- **Endpoint**: `DELETE /api/transactions/{id}`
- **Access**: Protected
- **Response**: `200 OK`: `{"message": "Transaction 101 deleted successfully"}`

---

## 6. Budgets (`/api/budgets/*`)

Handled by `controller.BudgetServlet`.

### 6.1 List User Budgets
- **Endpoint**: `GET /api/budgets`
- **Access**: Protected
- **Response**: `200 OK`
  ```json
  [
    {
      "budget_id": 1,
      "user_id": 1,
      "budget_name": "October Household Budget",
      "start_date": "2026-10-01",
      "end_date": "2026-10-31",
      "created_at": "2026-10-01T09:00:00Z"
    }
  ]
  ```

### 6.2 Create Budget
- **Endpoint**: `POST /api/budgets`
- **Access**: Protected
- **Request Body**:
  ```json
  {
    "budget_name": "November 2026 Budget",
    "start_date": "2026-11-01",
    "end_date": "2026-11-30"
  }
  ```
- **Response**: `201 Created`

### 6.3 Update Budget
- **Endpoint**: `PUT /api/budgets/{id}`
- **Access**: Protected
- **Response**: `200 OK`

### 6.4 Delete Budget
- **Endpoint**: `DELETE /api/budgets/{id}`
- **Access**: Protected (Cascades associated category allocations)
- **Response**: `200 OK`

---

## 7. Budget Category Allocations (`/api/budgets/{budgetId}/categories/*`)

Handled by `controller.BudgetCategoryServlet` (delegated from `BudgetServlet`).

### 7.1 List Allocations for Budget
- **Endpoint**: `GET /api/budgets/{budgetId}/categories`
- **Access**: Protected
- **Response**: `200 OK`
  ```json
  [
    {
      "budget_id": 1,
      "category_id": 4,
      "allocated_amount": 8000.00
    },
    {
      "budget_id": 1,
      "category_id": 5,
      "allocated_amount": 5000.00
    }
  ]
  ```

### 7.2 Allocate Category to Budget
- **Endpoint**: `POST /api/budgets/{budgetId}/categories`
- **Access**: Protected
- **Request Body**:
  ```json
  {
    "category_id": 6,
    "allocated_amount": 4500.00
  }
  ```
- **Responses**:
  - `201 Created` on success.
  - `409 Conflict` if category is already mapped to this budget.

### 7.3 Update Category Allocation
- **Endpoint**: `PUT /api/budgets/{budgetId}/categories/{categoryId}`
- **Access**: Protected
- **Request Body**:
  ```json
  {
    "allocated_amount": 6000.00
  }
  ```
- **Response**: `200 OK`

### 7.4 Remove Category from Budget
- **Endpoint**: `DELETE /api/budgets/{budgetId}/categories/{categoryId}`
- **Access**: Protected
- **Response**: `200 OK`: `{"message": "Category 6 removed from budget 1"}`

---

## 8. Savings Goals (`/api/savings-goals/*`)

Handled by `controller.SavingsGoalServlet`.

### 8.1 List User Savings Goals
- **Endpoint**: `GET /api/savings-goals`
- **Access**: Protected
- **Response**: `200 OK`
  ```json
  [
    {
      "goal_id": 1,
      "user_id": 1,
      "account_id": 2,
      "goal_name": "Emergency Fund",
      "target_amount": 100000.00,
      "saved_amount": 35000.00,
      "target_date": "2026-12-31",
      "status": "Active",
      "created_at": "2026-09-01T10:00:00Z"
    }
  ]
  ```

### 8.2 Create Savings Goal
- **Endpoint**: `POST /api/savings-goals`
- **Access**: Protected
- **Request Body**:
  ```json
  {
    "goal_name": "New Laptop",
    "target_amount": 80000.00,
    "saved_amount": 10000.00,
    "target_date": "2027-03-31",
    "account_id": 2,
    "status": "Active"
  }
  ```
- **Response**: `201 Created`

### 8.3 Update Savings Goal
- **Endpoint**: `PUT /api/savings-goals/{id}`
- **Access**: Protected
- **Request Body**:
  ```json
  {
    "saved_amount": 20000.00,
    "status": "Active"
  }
  ```
- **Response**: `200 OK`

### 8.4 Delete Savings Goal
- **Endpoint**: `DELETE /api/savings-goals/{id}`
- **Access**: Protected
- **Response**: `200 OK`

---

## 9. Standard HTTP Error Responses

FinTrack controllers return uniform JSON error objects:
```json
{
  "error": "Descriptive error message"
}
```

| HTTP Status | Meaning | Typical Scenario |
|---|---|---|
| `400 Bad Request` | Malformed JSON or input validation failure | Missing fields, non-positive amounts, invalid dates |
| `401 Unauthorized` | Unauthenticated or expired session | Attempting to access protected API without cookie |
| `403 Forbidden` | Access denied | Cross-tenant access violation |
| `404 Not Found` | Resource does not exist | Invalid ID or ID belonging to another user |
| `409 Conflict` | Unique constraint violation | Email collision, duplicate budget category |
| `500 Internal Server Error` | Unhandled backend or database exception | Database connectivity interruption |
