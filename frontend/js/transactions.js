/**
 * FinTrack – Transactions Management Module
 * Connects frontend/transactions.html to the authenticated Java Servlet backend.
 * All ownership is derived from the server session; user_id is never sent.
 */

// Application State
let currentUser = null;
let currentDefaultAccountId = null;
let accounts = [];
let categories = [];
let transactions = [];
let displayedTransactions = [];
let editingTransactionId = null;
let deletingTransactionId = null;

// Filter State
let filterState = {
  accountId: "",
  type: "",
  startDate: "",
  endDate: ""
};

// INR Currency Formatter
const inrFormatter = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

/**
 * Formats a numeric value to Indian Rupee currency string.
 */
function formatCurrency(amount) {
  const num = Number(amount);
  if (isNaN(num)) return "₹0.00";
  return inrFormatter.format(num);
}

/**
 * Formats a date string (YYYY-MM-DD) into readable format (e.g. 05 Oct 2026).
 */
function formatDate(dateStr) {
  if (!dateStr) return "—";
  try {
    const parts = dateStr.split("-");
    if (parts.length === 3) {
      const year = parseInt(parts[0], 10);
      const month = parseInt(parts[1], 10) - 1;
      const day = parseInt(parts[2], 10);
      const date = new Date(year, month, day);
      return date.toLocaleDateString("en-GB", {
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

/**
 * Returns today's date formatted as YYYY-MM-DD in local time.
 */
function getTodayDateString() {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, "0");
  const day = String(today.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * Displays a toast notification on the page.
 */
let toastTimeout = null;
function showToast(message, type = "success") {
  const toastEl = document.getElementById("pageToast");
  if (!toastEl) return;

  if (toastTimeout) {
    clearTimeout(toastTimeout);
  }

  toastEl.className = type === "error" ? "alert alert-error" : "alert alert-success";
  toastEl.textContent = message;
  toastEl.style.display = "flex";

  toastTimeout = setTimeout(() => {
    toastEl.style.display = "none";
  }, 4500);
}

/**
 * Displays or hides modal-level validation error messages.
 */
function showModalError(message) {
  const modalErr = document.getElementById("modalError");
  if (!modalErr) return;
  if (message) {
    modalErr.textContent = message;
    modalErr.style.display = "block";
  } else {
    modalErr.textContent = "";
    modalErr.style.display = "none";
  }
}

/**
 * Displays or hides filter-level validation error messages.
 */
function showFilterError(message) {
  const filterErr = document.getElementById("filterError");
  if (!filterErr) return;
  if (message) {
    filterErr.textContent = message;
    filterErr.style.display = "block";
  } else {
    filterErr.textContent = "";
    filterErr.style.display = "none";
  }
}

/**
 * Initializes the Transactions page.
 * Enforces session authentication via requireAuth().
 */
async function initTransactionsPage() {
  currentUser = await requireAuth();
  if (!currentUser) return; // Redirection handled in requireAuth()

  // Display user header info
  const displayName = currentUser.name || "FinTrack User";
  const userAvatar = document.getElementById("userAvatar");
  const userName = document.getElementById("userName");
  if (userAvatar) userAvatar.textContent = displayName.trim().charAt(0).toUpperCase() || "U";
  if (userName) userName.textContent = displayName;

  currentDefaultAccountId = currentUser.default_account_id ?? currentUser.defaultAccountId ?? null;

  // Setup event listeners
  setupEventListeners();

  // Load supporting dropdown data (accounts & categories) first
  await Promise.all([loadAccounts(), loadCategories()]);

  // Load transactions
  await loadTransactions();
}

/**
 * Binds DOM event listeners for buttons, forms, and modals.
 */
function setupEventListeners() {
  // Sign Out
  const logoutBtn = document.getElementById("logoutBtn");
  if (logoutBtn) {
    logoutBtn.addEventListener("click", logout);
  }

  // Add Transaction Buttons
  const addTransactionBtn = document.getElementById("addTransactionBtn");
  if (addTransactionBtn) {
    addTransactionBtn.addEventListener("click", openAddModal);
  }

  const emptyAddBtn = document.getElementById("emptyAddBtn");
  if (emptyAddBtn) {
    emptyAddBtn.addEventListener("click", openAddModal);
  }

  // Modal Close & Cancel
  const closeTxModalBtn = document.getElementById("closeTxModalBtn");
  if (closeTxModalBtn) {
    closeTxModalBtn.addEventListener("click", closeTxModal);
  }

  const cancelTxBtn = document.getElementById("cancelTxBtn");
  if (cancelTxBtn) {
    cancelTxBtn.addEventListener("click", closeTxModal);
  }

  // Type change in modal to update category list
  const txTypeSelect = document.getElementById("txType");
  if (txTypeSelect) {
    txTypeSelect.addEventListener("change", (e) => {
      updateCategoryOptions(e.target.value);
    });
  }

  // Save Transaction Form Submission
  const transactionForm = document.getElementById("transactionForm");
  if (transactionForm) {
    transactionForm.addEventListener("submit", saveTransaction);
  }

  // Filter Form Submission
  const filterForm = document.getElementById("filterForm");
  if (filterForm) {
    filterForm.addEventListener("submit", applyFilters);
  }

  const clearFiltersBtn = document.getElementById("clearFiltersBtn");
  if (clearFiltersBtn) {
    clearFiltersBtn.addEventListener("click", clearFilters);
  }

  const emptyClearFiltersBtn = document.getElementById("emptyClearFiltersBtn");
  if (emptyClearFiltersBtn) {
    emptyClearFiltersBtn.addEventListener("click", clearFilters);
  }

  // Delete Modal Buttons
  const closeDeleteModalBtn = document.getElementById("closeDeleteModalBtn");
  if (closeDeleteModalBtn) {
    closeDeleteModalBtn.addEventListener("click", closeDeleteModal);
  }

  const cancelDeleteBtn = document.getElementById("cancelDeleteBtn");
  if (cancelDeleteBtn) {
    cancelDeleteBtn.addEventListener("click", closeDeleteModal);
  }

  const confirmDeleteBtn = document.getElementById("confirmDeleteBtn");
  if (confirmDeleteBtn) {
    confirmDeleteBtn.addEventListener("click", deleteTransaction);
  }

  // Close modals on clicking outside backdrop
  window.addEventListener("click", (e) => {
    const txModal = document.getElementById("transactionModal");
    const delModal = document.getElementById("deleteModal");
    if (e.target === txModal) closeTxModal();
    if (e.target === delModal) closeDeleteModal();
  });
}

/**
 * Loads user accounts from GET /api/accounts to populate dropdowns.
 */
async function loadAccounts() {
  try {
    accounts = await apiGet("/api/accounts");
    if (!Array.isArray(accounts)) accounts = [];

    // Populate Filter Account Select
    const filterAccount = document.getElementById("filterAccount");
    if (filterAccount) {
      filterAccount.innerHTML = '<option value="">All Accounts</option>';
      accounts.forEach((acc) => {
        const id = acc.account_id ?? acc.accountId;
        const name = acc.account_name ?? acc.accountName;
        const opt = document.createElement("option");
        opt.value = id;
        opt.textContent = name;
        filterAccount.appendChild(opt);
      });
    }

    // Populate Modal Account Select
    const txAccount = document.getElementById("txAccount");
    if (txAccount) {
      txAccount.innerHTML = '<option value="">Select Account</option>';
      accounts.forEach((acc) => {
        const id = acc.account_id ?? acc.accountId;
        const name = acc.account_name ?? acc.accountName;
        const opt = document.createElement("option");
        opt.value = id;
        opt.textContent = name;
        txAccount.appendChild(opt);
      });
    }
  } catch (error) {
    showToast("Failed to load financial accounts for selection.", "error");
  }
}

/**
 * Loads categories from GET /api/categories to populate modal categories.
 */
async function loadCategories() {
  try {
    categories = await apiGet("/api/categories");
    if (!Array.isArray(categories)) categories = [];
  } catch (error) {
    showToast("Failed to load categories for selection.", "error");
  }
}

/**
 * Filters and updates category options in modal according to selected transaction type.
 *
 * @param {string} selectedType - "Income" or "Expense"
 * @param {number|string|null} preselectedCategoryId - Optional category ID to preselect
 */
function updateCategoryOptions(selectedType, preselectedCategoryId = null) {
  const txCategory = document.getElementById("txCategory");
  if (!txCategory) return;

  txCategory.innerHTML = '<option value="">Select Category</option>';

  const targetType = (selectedType || "").trim().toLowerCase();
  const filtered = categories.filter((cat) => {
    const cType = (cat.category_type || cat.categoryType || "").trim().toLowerCase();
    return cType === targetType;
  });

  if (filtered.length === 0) {
    const opt = document.createElement("option");
    opt.value = "";
    opt.textContent = `No ${selectedType} categories found`;
    opt.disabled = true;
    txCategory.appendChild(opt);
    return;
  }

  filtered.forEach((cat) => {
    const catId = cat.category_id ?? cat.categoryId;
    const catName = cat.category_name ?? cat.categoryName;
    const isCustom = (cat.user_id ?? cat.userId) !== null && (cat.user_id ?? cat.userId) !== undefined;

    const opt = document.createElement("option");
    opt.value = catId;
    opt.textContent = isCustom ? `${catName} (Custom)` : catName;

    if (preselectedCategoryId !== null && String(catId) === String(preselectedCategoryId)) {
      opt.selected = true;
    }

    txCategory.appendChild(opt);
  });
}

/**
 * Loads transactions from GET /api/transactions with backend query parameter support.
 * Backend supports:
 *   account_id={accountId}
 *   start_date={YYYY-MM-DD}&end_date={YYYY-MM-DD}
 */
async function loadTransactions() {
  const loadingIndicator = document.getElementById("loadingIndicator");
  const emptyState = document.getElementById("emptyState");
  const filteredEmptyState = document.getElementById("filteredEmptyState");
  const tableWrapper = document.getElementById("transactionsTableWrapper");

  if (loadingIndicator) loadingIndicator.style.display = "block";
  if (emptyState) emptyState.style.display = "none";
  if (filteredEmptyState) filteredEmptyState.style.display = "none";
  if (tableWrapper) tableWrapper.style.display = "none";

  try {
    let endpoint = "/api/transactions";
    const hasAccountFilter = Boolean(filterState.accountId);
    const hasDateFilter = Boolean(filterState.startDate && filterState.endDate);

    // Build supported backend parameters
    if (hasDateFilter) {
      endpoint += `?start_date=${encodeURIComponent(filterState.startDate)}&end_date=${encodeURIComponent(filterState.endDate)}`;
      // If account is also filtered, account filtering is performed client-side
    } else if (hasAccountFilter) {
      endpoint += `?account_id=${encodeURIComponent(filterState.accountId)}`;
    }

    const result = await apiGet(endpoint);
    transactions = Array.isArray(result) ? result : [];

    // Apply any remaining filters client-side (e.g. type, or secondary account/date filter)
    applyClientSideFiltering();

    if (loadingIndicator) loadingIndicator.style.display = "none";

    renderTransactions();
    renderSummary();

  } catch (error) {
    if (loadingIndicator) loadingIndicator.style.display = "none";
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showToast(error.message || "Failed to load transactions. Please check your connection.", "error");
  }
}

/**
 * Filters loaded transactions client-side for parameters not covered by the single backend query.
 */
function applyClientSideFiltering() {
  displayedTransactions = transactions.filter((tx) => {
    // 1. Transaction Type filter
    if (filterState.type) {
      const txType = (tx.transaction_type || tx.transactionType || "").trim().toLowerCase();
      if (txType !== filterState.type.toLowerCase()) {
        return false;
      }
    }

    // 2. Account filter (if date range query was sent to backend, account must be filtered here)
    if (filterState.accountId) {
      const txAccountId = String(tx.account_id ?? tx.accountId);
      if (txAccountId !== String(filterState.accountId)) {
        return false;
      }
    }

    // 3. Date range filter (if account_id query was sent to backend, date must be filtered here)
    if (filterState.startDate && filterState.endDate) {
      const txDate = tx.transaction_date || tx.transactionDate;
      if (txDate) {
        if (txDate < filterState.startDate || txDate > filterState.endDate) {
          return false;
        }
      }
    }

    return true;
  });
}

/**
 * Renders the transactions table and toggles empty states.
 */
function renderTransactions() {
  const emptyState = document.getElementById("emptyState");
  const filteredEmptyState = document.getElementById("filteredEmptyState");
  const tableWrapper = document.getElementById("transactionsTableWrapper");
  const tbody = document.getElementById("transactionsTableBody");

  if (!tbody) return;
  tbody.innerHTML = "";

  const isFilteringActive = Boolean(
    filterState.accountId || filterState.type || filterState.startDate || filterState.endDate
  );

  if (transactions.length === 0 && !isFilteringActive) {
    if (emptyState) emptyState.style.display = "block";
    if (filteredEmptyState) filteredEmptyState.style.display = "none";
    if (tableWrapper) tableWrapper.style.display = "none";
    return;
  }

  if (displayedTransactions.length === 0) {
    if (emptyState) emptyState.style.display = "none";
    if (filteredEmptyState) filteredEmptyState.style.display = "block";
    if (tableWrapper) tableWrapper.style.display = "none";
    return;
  }

  if (emptyState) emptyState.style.display = "none";
  if (filteredEmptyState) filteredEmptyState.style.display = "none";
  if (tableWrapper) tableWrapper.style.display = "block";

  // Create lookup maps for fast account & category name resolution
  const accountMap = new Map();
  accounts.forEach((a) => {
    const id = String(a.account_id ?? a.accountId);
    accountMap.set(id, a.account_name ?? a.accountName);
  });

  const categoryMap = new Map();
  categories.forEach((c) => {
    const id = String(c.category_id ?? c.categoryId);
    categoryMap.set(id, c.category_name ?? c.categoryName);
  });

  displayedTransactions.forEach((tx) => {
    const txId = tx.transaction_id ?? tx.transactionId;
    const txDate = tx.transaction_date || tx.transactionDate;
    const txType = tx.transaction_type || tx.transactionType || "Expense";
    const accountId = String(tx.account_id ?? tx.accountId);
    const categoryId = String(tx.category_id ?? tx.categoryId);
    const description = tx.description ? tx.description.trim() : "";
    const amount = Number(tx.amount);

    const accountName = accountMap.get(accountId) || `Account #${accountId}`;
    const categoryName = categoryMap.get(categoryId) || `Category #${categoryId}`;

    const isIncome = txType.toLowerCase() === "income";
    const typeBadgeClass = isIncome ? "table-badge badge-income" : "table-badge badge-expense";
    const amountClass = isIncome ? "amount-income" : "amount-expense";
    const amountPrefix = isIncome ? "+ " : "- ";

    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${formatDate(txDate)}</td>
      <td><span class="${typeBadgeClass}">${txType}</span></td>
      <td><strong>${escapeHtml(categoryName)}</strong></td>
      <td>${escapeHtml(accountName)}</td>
      <td>${description ? escapeHtml(description) : '<span class="text-muted">—</span>'}</td>
      <td style="text-align: right;"><span class="${amountClass}">${amountPrefix}${formatCurrency(amount)}</span></td>
      <td style="text-align: center;">
        <div class="actions-cell" style="justify-content: center;">
          <button type="button" class="btn btn-secondary btn-sm" onclick="openEditModal(${txId})">Edit</button>
          <button type="button" class="btn btn-danger btn-sm" onclick="confirmDelete(${txId})">Delete</button>
        </div>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

/**
 * Calculates and updates displayed summary totals: Total Income, Total Expenses, Net.
 * Avoids JavaScript floating-point errors by accumulating amounts in integer cents.
 */
function renderSummary() {
  let incomeCents = 0;
  let expenseCents = 0;

  displayedTransactions.forEach((tx) => {
    const num = Number(tx.amount);
    if (isNaN(num)) return;
    const cents = Math.round(num * 100);
    const type = (tx.transaction_type || tx.transactionType || "").trim().toLowerCase();
    if (type === "income") {
      incomeCents += cents;
    } else if (type === "expense") {
      expenseCents += cents;
    }
  });

  const netCents = incomeCents - expenseCents;

  const totalIncomeEl = document.getElementById("totalIncome");
  const totalExpensesEl = document.getElementById("totalExpenses");
  const netAmountEl = document.getElementById("netAmount");

  if (totalIncomeEl) {
    totalIncomeEl.textContent = formatCurrency(incomeCents / 100);
  }
  if (totalExpensesEl) {
    totalExpensesEl.textContent = formatCurrency(expenseCents / 100);
  }
  if (netAmountEl) {
    const netFormatted = formatCurrency(Math.abs(netCents) / 100);
    netAmountEl.textContent = netCents < 0 ? `-${netFormatted}` : netFormatted;

    // Apply color tone
    netAmountEl.classList.remove("stat-net-positive", "stat-net-negative");
    if (netCents > 0) {
      netAmountEl.classList.add("stat-net-positive");
    } else if (netCents < 0) {
      netAmountEl.classList.add("stat-net-negative");
    }
  }
}

/**
 * Opens Add Transaction Modal.
 */
function openAddModal() {
  editingTransactionId = null;
  showModalError("");

  const modalTitle = document.getElementById("modalTitle");
  if (modalTitle) modalTitle.textContent = "Add Transaction";

  const saveBtn = document.getElementById("saveTxBtn");
  if (saveBtn) saveBtn.textContent = "Save Transaction";

  const form = document.getElementById("transactionForm");
  if (form) form.reset();

  // Default transaction date to today in local time
  const txDateInput = document.getElementById("txDate");
  if (txDateInput) txDateInput.value = getTodayDateString();

  // Default type to Expense
  const txTypeSelect = document.getElementById("txType");
  if (txTypeSelect) txTypeSelect.value = "Expense";

  // Pre-select default account if configured
  const txAccount = document.getElementById("txAccount");
  if (txAccount) {
    if (currentDefaultAccountId) {
      txAccount.value = currentDefaultAccountId;
    } else if (accounts.length > 0) {
      const firstId = accounts[0].account_id ?? accounts[0].accountId;
      txAccount.value = firstId;
    }
  }

  // Update categories for Expense
  updateCategoryOptions("Expense");

  const modal = document.getElementById("transactionModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Opens Edit Transaction Modal with existing values populated.
 *
 * @param {number|string} transactionId
 */
function openEditModal(transactionId) {
  const tx = transactions.find((item) => {
    const id = item.transaction_id ?? item.transactionId;
    return String(id) === String(transactionId);
  });

  if (!tx) {
    showToast("Transaction not found. Please refresh the page.", "error");
    return;
  }

  editingTransactionId = transactionId;
  showModalError("");

  const modalTitle = document.getElementById("modalTitle");
  if (modalTitle) modalTitle.textContent = "Edit Transaction";

  const saveBtn = document.getElementById("saveTxBtn");
  if (saveBtn) saveBtn.textContent = "Update Transaction";

  const txType = tx.transaction_type || tx.transactionType || "Expense";
  const txTypeSelect = document.getElementById("txType");
  if (txTypeSelect) txTypeSelect.value = txType;

  const txAccount = document.getElementById("txAccount");
  const accountId = tx.account_id ?? tx.accountId;
  if (txAccount) txAccount.value = accountId;

  const categoryId = tx.category_id ?? tx.categoryId;
  updateCategoryOptions(txType, categoryId);

  const txAmount = document.getElementById("txAmount");
  if (txAmount) txAmount.value = Number(tx.amount).toFixed(2);

  const txDateInput = document.getElementById("txDate");
  const rawDate = tx.transaction_date || tx.transactionDate;
  if (txDateInput) txDateInput.value = rawDate || "";

  const txDesc = document.getElementById("txDescription");
  if (txDesc) txDesc.value = tx.description || "";

  const modal = document.getElementById("transactionModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Closes the Add/Edit Transaction modal.
 */
function closeTxModal() {
  const modal = document.getElementById("transactionModal");
  if (modal) modal.style.display = "none";
  editingTransactionId = null;
  showModalError("");
}

/**
 * Saves a new or edited transaction.
 * Enforces frontend validation and dispatches POST /api/transactions or PUT /api/transactions/{id}.
 * NEVER submits client-supplied user_id, transaction_id, or created_at.
 */
async function saveTransaction(e) {
  e.preventDefault();
  showModalError("");

  const typeSelect = document.getElementById("txType");
  const accountSelect = document.getElementById("txAccount");
  const categorySelect = document.getElementById("txCategory");
  const amountInput = document.getElementById("txAmount");
  const dateInput = document.getElementById("txDate");
  const descInput = document.getElementById("txDescription");
  const saveBtn = document.getElementById("saveTxBtn");

  const txType = typeSelect ? typeSelect.value.trim() : "";
  const accountIdStr = accountSelect ? accountSelect.value.trim() : "";
  const categoryIdStr = categorySelect ? categorySelect.value.trim() : "";
  const amountStr = amountInput ? amountInput.value.trim() : "";
  const dateStr = dateInput ? dateInput.value.trim() : "";
  const description = descInput ? descInput.value.trim() : "";

  // Validation
  if (txType !== "Income" && txType !== "Expense") {
    showModalError("Please select a valid transaction type (Income or Expense).");
    return;
  }

  if (!accountIdStr) {
    showModalError("Please select an account.");
    if (accountSelect) accountSelect.focus();
    return;
  }

  if (!categoryIdStr) {
    showModalError("Please select a category.");
    if (categorySelect) categorySelect.focus();
    return;
  }

  const amountNum = parseFloat(amountStr);
  if (isNaN(amountNum) || amountNum <= 0) {
    showModalError("Amount must be a positive number greater than 0.");
    if (amountInput) amountInput.focus();
    return;
  }

  if (!dateStr) {
    showModalError("Please select a transaction date.");
    if (dateInput) dateInput.focus();
    return;
  }

  // Construct secure payload without user_id, transaction_id, or created_at
  const payload = {
    account_id: parseInt(accountIdStr, 10),
    category_id: parseInt(categoryIdStr, 10),
    amount: amountNum,
    transaction_type: txType,
    transaction_date: dateStr,
    description: description || null
  };

  if (saveBtn) {
    saveBtn.disabled = true;
    saveBtn.textContent = "Saving...";
  }

  try {
    if (editingTransactionId) {
      await apiPut(`/api/transactions/${editingTransactionId}`, payload);
      showToast("Transaction updated successfully.", "success");
    } else {
      await apiPost("/api/transactions", payload);
      showToast("Transaction recorded successfully.", "success");
    }

    closeTxModal();
    await loadTransactions();

  } catch (error) {
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showModalError(error.message || "Failed to save transaction. Please check your inputs.");
  } finally {
    if (saveBtn) {
      saveBtn.disabled = false;
      saveBtn.textContent = editingTransactionId ? "Update Transaction" : "Save Transaction";
    }
  }
}

/**
 * Prepares and displays the Delete Confirmation modal.
 *
 * @param {number|string} transactionId
 */
function confirmDelete(transactionId) {
  const tx = transactions.find((item) => {
    const id = item.transaction_id ?? item.transactionId;
    return String(id) === String(transactionId);
  });

  if (!tx) {
    showToast("Transaction not found.", "error");
    return;
  }

  deletingTransactionId = transactionId;

  // Populate transaction details in modal
  const detailsBox = document.getElementById("deleteItemDetails");
  if (detailsBox) {
    const txType = tx.transaction_type || tx.transactionType || "Expense";
    const isIncome = txType.toLowerCase() === "income";
    const amountClass = isIncome ? "amount-income" : "amount-expense";
    const amountPrefix = isIncome ? "+ " : "- ";

    const catName = categories.find((c) => String(c.category_id ?? c.categoryId) === String(tx.category_id ?? tx.categoryId));
    const accName = accounts.find((a) => String(a.account_id ?? a.accountId) === String(tx.account_id ?? tx.accountId));

    detailsBox.innerHTML = `
      <div><strong>Date:</strong> ${formatDate(tx.transaction_date || tx.transactionDate)}</div>
      <div><strong>Type:</strong> ${escapeHtml(txType)}</div>
      <div><strong>Category:</strong> ${escapeHtml(catName ? (catName.category_name ?? catName.categoryName) : `ID #${tx.category_id ?? tx.categoryId}`)}</div>
      <div><strong>Account:</strong> ${escapeHtml(accName ? (accName.account_name ?? accName.accountName) : `ID #${tx.account_id ?? tx.accountId}`)}</div>
      ${tx.description ? `<div><strong>Description:</strong> ${escapeHtml(tx.description)}</div>` : ""}
      <div style="margin-top: 0.35rem;"><strong>Amount:</strong> <span class="${amountClass}">${amountPrefix}${formatCurrency(tx.amount)}</span></div>
    `;
  }

  const modal = document.getElementById("deleteModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Closes the Delete Confirmation modal.
 */
function closeDeleteModal() {
  const modal = document.getElementById("deleteModal");
  if (modal) modal.style.display = "none";
  deletingTransactionId = null;
}

/**
 * Executes transaction deletion via DELETE /api/transactions/{id}.
 */
async function deleteTransaction() {
  if (!deletingTransactionId) return;

  const confirmBtn = document.getElementById("confirmDeleteBtn");
  if (confirmBtn) {
    confirmBtn.disabled = true;
    confirmBtn.textContent = "Deleting...";
  }

  try {
    await apiDelete(`/api/transactions/${deletingTransactionId}`);
    closeDeleteModal();
    showToast("Transaction deleted successfully.", "success");
    await loadTransactions();

  } catch (error) {
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showToast(error.message || "Failed to delete transaction. Please try again.", "error");
  } finally {
    if (confirmBtn) {
      confirmBtn.disabled = false;
      confirmBtn.textContent = "Delete Transaction";
    }
  }
}

/**
 * Applies filters based on Account, Type, Start Date, and End Date.
 */
async function applyFilters(e) {
  if (e) e.preventDefault();
  showFilterError("");

  const accountSelect = document.getElementById("filterAccount");
  const typeSelect = document.getElementById("filterType");
  const startDateInput = document.getElementById("filterStartDate");
  const endDateInput = document.getElementById("filterEndDate");

  const accountId = accountSelect ? accountSelect.value.trim() : "";
  const type = typeSelect ? typeSelect.value.trim() : "";
  const startDate = startDateInput ? startDateInput.value.trim() : "";
  const endDate = endDateInput ? endDateInput.value.trim() : "";

  // Validate dates: if one date is provided, both must be provided
  if ((startDate && !endDate) || (!startDate && endDate)) {
    showFilterError("Please select both Start Date and End Date to filter by date range.");
    return;
  }

  if (startDate && endDate && startDate > endDate) {
    showFilterError("Start Date cannot be after End Date.");
    return;
  }

  filterState = {
    accountId,
    type,
    startDate,
    endDate
  };

  await loadTransactions();
}

/**
 * Clears all active filters and reloads transactions.
 */
async function clearFilters() {
  showFilterError("");

  const accountSelect = document.getElementById("filterAccount");
  const typeSelect = document.getElementById("filterType");
  const startDateInput = document.getElementById("filterStartDate");
  const endDateInput = document.getElementById("filterEndDate");

  if (accountSelect) accountSelect.value = "";
  if (typeSelect) typeSelect.value = "";
  if (startDateInput) startDateInput.value = "";
  if (endDateInput) endDateInput.value = "";

  filterState = {
    accountId: "",
    type: "",
    startDate: "",
    endDate: ""
  };

  await loadTransactions();
}

/**
 * Sanitizes strings for safe HTML rendering to prevent XSS.
 */
function escapeHtml(str) {
  if (!str) return "";
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}

// Global exposure for inline HTML event handlers (e.g. edit, delete)
window.openEditModal = openEditModal;
window.confirmDelete = confirmDelete;

// Initialize on DOMContentLoaded
document.addEventListener("DOMContentLoaded", initTransactionsPage);
