# FinTrack – Frontend Guide & Operational Manual

This directory contains the client-side presentation layer for **FinTrack – Personal Finance Management System**, engineered using clean, modern **HTML5, CSS3, and Vanilla JavaScript (ES6+)** with asynchronous Fetch APIs.

---

## 1. Directory Structure

```text
frontend/
├── assets/                 # Icons, branding assets, and images
├── css/
│   └── style.css           # Unified modern dark-theme stylesheet
├── js/
│   ├── api.js              # Centralized API fetch client (credentials: "include", error toast)
│   ├── auth.js             # Session guards (requireAuth, redirectIfAuthenticated, logout)
│   ├── dashboard.js        # Dashboard summary cards, recent transactions, and accounts snapshot
│   ├── accounts.js         # Financial accounts CRUD, balance display, and default designation
│   ├── categories.js       # System category views and custom category management
│   ├── transactions.js     # Transaction ledger, type/date filtering, modal CRUD
│   ├── budgets.js          # Monthly budget planning, category allocations, progress bars
│   └── savings-goals.js    # Savings goal tracking, linked accounts, progress indicators
├── index.html              # Sign-In / Login page
├── register.html           # User account registration page
├── dashboard.html          # High-level financial overview and quick actions
├── accounts.html           # Bank accounts, cards, and cash management
├── categories.html         # Spending and income categories management
├── transactions.html       # Detailed income and expense transaction ledger
├── budgets.html            # Category budget allocation and spending caps
└── savings-goals.html      # Financial target milestones and progress tracking
```

---

## 2. Serving the Frontend (Development)

> [!IMPORTANT]
> **DO NOT OPEN PAGES USING `file://` PROTOCOL.**
> Browsers prohibit cross-origin session cookies (`credentials: "include"`) when pages are loaded from local file URLs (`file:///C:/...`).
> The frontend **must** be served over HTTP through a local development server.

### Recommended Local Servers

#### Option A: Python Built-In HTTP Server (Recommended)
Open a terminal in the `frontend/` directory and execute:
```bash
python -m http.server 5500
```
Navigate in your browser to:
```text
http://localhost:5500
```

#### Option B: VS Code Live Server
1. Open the project in VS Code.
2. Right-click `frontend/index.html` and choose **"Open with Live Server"** (default port `5500`).

#### Option C: Node.js `http-server` / `serve`
```bash
npx serve -l 5500 frontend
```

---

## 3. Backend Integration & Configuration

The client connects to the Java Servlet backend via `frontend/js/api.js`:
```javascript
const API_BASE_URL = window.FINTRACK_API_BASE_URL || "http://localhost:8080";
```

If your Tomcat server runs under an application context path (such as `/fintrack`), you can override the base URL globally in the browser console or prior to script execution:
```javascript
window.FINTRACK_API_BASE_URL = "http://localhost:8080/fintrack";
```

### Integrated Backend Endpoints
- **Authentication**: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`
- **Accounts**: `GET /api/accounts`, `POST /api/accounts`, `PUT /api/accounts/{id}`, `PUT /api/accounts/{id}/default`, `DELETE /api/accounts/{id}`
- **Categories**: `GET /api/categories`, `POST /api/categories`, `PUT /api/categories/{id}`, `DELETE /api/categories/{id}`
- **Transactions**: `GET /api/transactions`, `POST /api/transactions`, `PUT /api/transactions/{id}`, `DELETE /api/transactions/{id}`
- **Budgets**: `GET /api/budgets`, `POST /api/budgets`, `PUT /api/budgets/{id}`, `DELETE /api/budgets/{id}`
- **Budget Categories**: `GET /api/budget-categories/{budgetId}`, `POST /api/budget-categories/{budgetId}`, `PUT /api/budget-categories/{budgetId}/{catId}`, `DELETE /api/budget-categories/{budgetId}/{catId}`
- **Savings Goals**: `GET /api/savings-goals`, `POST /api/savings-goals`, `PUT /api/savings-goals/{id}`, `DELETE /api/savings-goals/{id}`

---

## 4. Security Architecture

1. **HttpOnly Cookie Authentication**:
   - Authentication relies strictly on standard Java Servlet `HttpSession` cookies (`JSESSIONID`).
   - `credentials: "include"` is configured on all API requests in `frontend/js/api.js`.
   - Neither passwords, session IDs, nor tokens are stored in `localStorage` or `sessionStorage`.
2. **Strict Session-Based Ownership**:
   - Client scripts never submit a `user_id` parameter. Ownership is derived exclusively by the backend session filter.
3. **Session Route Guards**:
   - `requireAuth()`: Guards protected pages (`dashboard.html`, `accounts.html`, `categories.html`, `transactions.html`, `budgets.html`, `savings-goals.html`). If `GET /api/auth/me` returns 401 or network fails, the user is redirected to `index.html`.
   - `redirectIfAuthenticated()`: Guards public auth pages (`index.html`, `register.html`). If an active session already exists, users are automatically forwarded to `dashboard.html`.
4. **Data Sanitization**:
   - `password_hash` is never exposed or processed by the frontend.
   - Text inputs are rendered using `textContent` and parameterized templates to prevent Cross-Site Scripting (XSS).

---

## 5. Troubleshooting Common Frontend Issues

| Issue | Cause | Solution |
| :--- | :--- | :--- |
| **Login succeeds but dashboard redirects to login** | Session cookie not preserved due to opening via `file://`. | Serve frontend via `python -m http.server 5500` or VS Code Live Server. |
| **CORS policy error in browser console** | Frontend running on an origin not listed in `CorsFilter` whitelist. | Use standard origin `http://localhost:5500` or add your custom origin to `backend/src/filter/CorsFilter.java`. |
| **API requests return 404 Not Found** | Backend deployed with context path (e.g. `/fintrack`) while API base points to root. | Set `window.FINTRACK_API_BASE_URL = "http://localhost:8080/fintrack";` in HTML or adjust Tomcat `ROOT.war` mapping. |
| **Network Error / Failed to fetch** | Backend Tomcat server is not running on port 8080. | Verify Tomcat is started: visit `http://localhost:8080` in browser. |
