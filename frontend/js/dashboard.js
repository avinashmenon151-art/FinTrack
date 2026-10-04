/**
 * FinTrack – Dashboard Overview Module
 * Connects frontend/dashboard.html to the authenticated Java Servlet backend.
 * Derives user identity exclusively from the session; never submits or trusts user_id.
 */

// Application State
let currentUser = null;
let accounts = [];
let transactions = [];
let budgets = [];
let savingsGoals = [];

// INR Currency Formatter
const inrFormatter = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

function formatCurrency(amount) {
  const num = Number(amount);
  if (isNaN(num)) return "₹0.00";
  return inrFormatter.format(num);
}

function formatDate(dateStr) {
  if (!dateStr) return "—";
  try {
    const parts = dateStr.split("-");
    if (parts.length === 3) {
      const year = parseInt(parts[0], 10);
      const month = parseInt(parts[1], 10) - 1;
      const day = parseInt(parts[2], 10);
      return new Date(year, month, day).toLocaleDateString("en-GB", {
        day: "2-digit",
        month: "short",
        year: "numeric"
      });
    }
    return dateStr;
  } catch (e) {
    return dateStr;
  }
}

function getTodayDateString() {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, "0");
  const day = String(today.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function getCurrentMonthPrefix() {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, "0");
  return `${year}-${month}`;
}

/**
 * Initializes the Dashboard page.
 */
async function initDashboardPage() {
  // 1. Session Guard: verify authenticated session via /api/auth/me
  currentUser = await requireAuth();
  if (!currentUser) return; // Redirection handled in requireAuth()

  // 2. Render user info
  const displayName = currentUser.name || "FinTrack User";
  const userAvatar = document.getElementById("userAvatar");
  const userName = document.getElementById("userName");
  const welcomeUserName = document.getElementById("welcomeUserName");
  const userEmail = document.getElementById("userEmail");

  if (userAvatar) userAvatar.textContent = displayName.trim().charAt(0).toUpperCase() || "U";
  if (userName) userName.textContent = displayName;
  if (welcomeUserName) welcomeUserName.textContent = displayName;
  if (userEmail) userEmail.textContent = currentUser.email || "";

  // 3. Make dashboard visible
  const body = document.getElementById("dashboardBody");
  if (body) body.style.display = "block";

  // 4. Setup Logout handler
  const logoutBtn = document.getElementById("logoutBtn");
  if (logoutBtn) {
    logoutBtn.addEventListener("click", logout);
  }

  // 5. Load dashboard data from existing backend APIs in parallel
  await loadDashboardData();
}

/**
 * Loads financial data from backend APIs to populate real dashboard metrics.
 */
async function loadDashboardData() {
  const loadingEl = document.getElementById("dashboardLoading");
  if (loadingEl) loadingEl.style.display = "block";

  try {
    const [accs, txs, bdgs, goals] = await Promise.all([
      apiGet("/api/accounts").catch(() => []),
      apiGet("/api/transactions").catch(() => []),
      apiGet("/api/budgets").catch(() => []),
      apiGet("/api/savings-goals").catch(() => [])
    ]);

    accounts = Array.isArray(accs) ? accs : [];
    transactions = Array.isArray(txs) ? txs : [];
    budgets = Array.isArray(bdgs) ? bdgs : [];
    savingsGoals = Array.isArray(goals) ? goals : [];

    if (loadingEl) loadingEl.style.display = "none";

    renderMetrics();
    renderRecentTransactions();
    renderAccountsOverview();

  } catch (err) {
    if (loadingEl) loadingEl.style.display = "none";
  }
}

/**
 * Computes and populates dashboard top summary cards with live data.
 */
function renderMetrics() {
  // 1. Total Balance across all user accounts
  let totalBalanceCents = 0;
  accounts.forEach((acc) => {
    const bal = Number(acc.balance);
    if (!isNaN(bal)) {
      totalBalanceCents += Math.round(bal * 100);
    }
  });

  const totalBalanceEl = document.getElementById("metricTotalBalance");
  const totalBalanceMeta = document.getElementById("metricTotalBalanceMeta");
  if (totalBalanceEl) totalBalanceEl.textContent = formatCurrency(totalBalanceCents / 100);
  if (totalBalanceMeta) totalBalanceMeta.textContent = `Across ${accounts.length} account${accounts.length === 1 ? '' : 's'}`;

  // 2. Current Month Income & Expenses
  const currentMonth = getCurrentMonthPrefix();
  let monthIncomeCents = 0;
  let monthExpenseCents = 0;

  transactions.forEach((tx) => {
    const dateStr = tx.transaction_date || tx.transactionDate || "";
    if (dateStr.startsWith(currentMonth)) {
      const amt = Number(tx.amount);
      if (!isNaN(amt)) {
        const type = (tx.transaction_type || tx.transactionType || "").trim().toLowerCase();
        if (type === "income") monthIncomeCents += Math.round(amt * 100);
        if (type === "expense") monthExpenseCents += Math.round(amt * 100);
      }
    }
  });

  const monthIncomeEl = document.getElementById("metricMonthIncome");
  const monthExpenseEl = document.getElementById("metricMonthExpense");
  if (monthIncomeEl) monthIncomeEl.textContent = formatCurrency(monthIncomeCents / 100);
  if (monthExpenseEl) monthExpenseEl.textContent = formatCurrency(monthExpenseCents / 100);

  // 3. Active Savings Goals
  let activeGoalsCount = 0;
  let activeGoalsSavedCents = 0;
  savingsGoals.forEach((goal) => {
    const status = (goal.status || "Active").trim().toLowerCase();
    if (status === "active") {
      activeGoalsCount++;
      const saved = Number(goal.saved_amount ?? goal.savedAmount);
      if (!isNaN(saved)) activeGoalsSavedCents += Math.round(saved * 100);
    }
  });

  const activeGoalsEl = document.getElementById("metricActiveGoals");
  const activeGoalsMeta = document.getElementById("metricActiveGoalsMeta");
  if (activeGoalsEl) activeGoalsEl.textContent = activeGoalsCount;
  if (activeGoalsMeta) activeGoalsMeta.textContent = `${formatCurrency(activeGoalsSavedCents / 100)} saved towards targets`;
}

/**
 * Renders the 5 most recent transactions.
 */
function renderRecentTransactions() {
  const container = document.getElementById("recentTransactionsList");
  if (!container) return;

  if (transactions.length === 0) {
    container.innerHTML = `
      <div class="placeholder-box" style="padding: 1.5rem; text-align: center;">
        <p class="text-muted" style="margin: 0; font-size: 0.9rem;">
          No transactions recorded yet. <a href="transactions.html">Add your first transaction</a>.
        </p>
      </div>
    `;
    return;
  }

  // Sort descending by date
  const sorted = [...transactions].sort((a, b) => {
    const dateA = a.transaction_date || a.transactionDate || "";
    const dateB = b.transaction_date || b.transactionDate || "";
    return dateB.localeCompare(dateA);
  });

  const recent = sorted.slice(0, 5);

  let html = `
    <table class="transaction-table">
      <thead>
        <tr>
          <th>Date</th>
          <th>Type</th>
          <th>Description</th>
          <th style="text-align: right;">Amount</th>
        </tr>
      </thead>
      <tbody>
  `;

  recent.forEach((tx) => {
    const dateStr = tx.transaction_date || tx.transactionDate;
    const type = (tx.transaction_type || tx.transactionType || "Expense").trim();
    const isIncome = type.toLowerCase() === "income";
    const amount = Number(tx.amount);
    const desc = tx.description ? tx.description.trim() : (isIncome ? "Income" : "Expense");

    html += `
      <tr>
        <td>${formatDate(dateStr)}</td>
        <td><span class="table-badge ${isIncome ? 'badge-income' : 'badge-expense'}">${type}</span></td>
        <td>${escapeHtml(desc)}</td>
        <td style="text-align: right;"><span class="${isIncome ? 'amount-income' : 'amount-expense'}">${isIncome ? '+ ' : '- '}${formatCurrency(amount)}</span></td>
      </tr>
    `;
  });

  html += `
      </tbody>
    </table>
    <div style="text-align: right; margin-top: 0.75rem;">
      <a href="transactions.html" style="font-size: 0.85rem; font-weight: 500;">View All Transactions →</a>
    </div>
  `;

  container.innerHTML = html;
}

/**
 * Renders account balances overview snapshot.
 */
function renderAccountsOverview() {
  const container = document.getElementById("accountsOverviewList");
  if (!container) return;

  if (accounts.length === 0) {
    container.innerHTML = `
      <div class="placeholder-box" style="padding: 1.5rem; text-align: center;">
        <p class="text-muted" style="margin: 0; font-size: 0.9rem;">
          No accounts found. <a href="accounts.html">Add an account</a> to start tracking balances.
        </p>
      </div>
    `;
    return;
  }

  let html = `<div style="display: flex; flex-direction: column; gap: 0.6rem;">`;
  accounts.forEach((acc) => {
    const name = acc.account_name ?? acc.accountName;
    const type = acc.account_type ?? acc.accountType;
    const balance = Number(acc.balance);
    const isDefault = currentUser && (currentUser.default_account_id ?? currentUser.defaultAccountId) === (acc.account_id ?? acc.accountId);

    html += `
      <div class="budget-cat-item" style="padding: 0.65rem 0.85rem; background: rgba(15, 23, 42, 0.5); border: 1px solid var(--border-color);">
        <div>
          <strong>${escapeHtml(name)}</strong>
          ${isDefault ? '<span class="account-default-badge" style="font-size: 0.65rem; margin-left: 0.35rem;">Default</span>' : ''}
          <div class="text-muted" style="font-size: 0.75rem;">${escapeHtml(type)}</div>
        </div>
        <strong style="font-size: 1rem; color: var(--text-primary); font-feature-settings: 'tnum';">${formatCurrency(balance)}</strong>
      </div>
    `;
  });

  html += `
    </div>
    <div style="text-align: right; margin-top: 0.75rem;">
      <a href="accounts.html" style="font-size: 0.85rem; font-weight: 500;">Manage Accounts →</a>
    </div>
  `;

  container.innerHTML = html;
}

function escapeHtml(str) {
  if (!str) return "";
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}

document.addEventListener("DOMContentLoaded", initDashboardPage);
