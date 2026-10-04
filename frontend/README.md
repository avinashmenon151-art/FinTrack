# FinTrack – Frontend Guide & Operational Manual

This directory contains the user interface for **FinTrack – Personal Finance Management System**, developed with clean, vanilla HTML5, CSS3, and JavaScript using modern asynchronous Fetch APIs.

---

## 1. Directory Structure

```text
frontend/
├── assets/                 # Icons, branding assets, and images
├── css/
│   └── style.css           # Modern dark-theme stylesheet
├── js/
│   ├── api.js              # Centralized API fetch client (credentials: "include")
│   └── auth.js             # Authentication helpers and session route guards
├── index.html              # Sign-In / Login page
├── register.html           # User account registration page
├── dashboard.html          # Authenticated user dashboard
├── accounts.html           # Accounts view (Placeholder for next steps)
├── transactions.html       # Transactions view (Placeholder for next steps)
├── categories.html         # Categories view (Placeholder for next steps)
├── budgets.html            # Budgets view (Placeholder for next steps)
└── savings-goals.html      # Savings goals view (Placeholder for next steps)
```

---

## 2. Serving the Frontend (Development)

> [!IMPORTANT]
> **DO NOT OPEN PAGES USING `file://` PROTOCOL.**
> Browsers prohibit cross-origin session cookies (`credentials: "include"`) when loaded from local file URLs (`file:///...`).
> The frontend **must** be served over HTTP through a local development server.

### Recommended Local Servers

#### Option A: Python Built-In HTTP Server (Recommended)
Open a terminal in the `frontend/` folder and run:
```bash
# Serve frontend at http://localhost:5500
python -m http.server 5500
```
Then navigate in your browser to:
```text
http://localhost:5500
```

#### Option B: VS Code Live Server
- Open the project in VS Code.
- Right-click `frontend/index.html` and choose **"Open with Live Server"** (default port `5500`).

#### Option C: Node.js `http-server` / `serve`
```bash
npx serve -l 5500 frontend
```

---

## 3. Backend Integration & Configuration

The frontend is pre-configured to communicate with the Java Servlet backend running at:
```javascript
const API_BASE_URL = window.FINTRACK_API_BASE_URL || "http://localhost:8080";
```

### Endpoints Utilized in Step 9:
- `POST /api/auth/register` - Create user account with server-side BCrypt hashing
- `POST /api/auth/login` - Authenticate credentials and establish HTTP session cookie
- `POST /api/auth/logout` - Invalidate server session
- `GET /api/auth/me` - Validate session and retrieve current user profile

---

## 4. Security Architecture

1. **HttpOnly Cookie Authentication**:
   - Authentication relies strictly on standard Java Servlet `HttpSession` cookies (`JSESSIONID`).
   - `credentials: "include"` is configured on all API requests in `js/api.js`.
   - Neither passwords, session IDs, nor tokens are stored in `localStorage` or `sessionStorage`.
2. **Session Guards**:
   - `requireAuth()`: Guards protected pages (`dashboard.html`). If `GET /api/auth/me` fails or returns 401, the user is redirected to `index.html`.
   - `redirectIfAuthenticated()`: Guards public auth pages (`index.html`, `register.html`). If an active session already exists, users are automatically redirected to `dashboard.html`.
3. **Safe User Serialization**:
   - `password_hash` is never exposed by the backend or handled by the frontend.
