/**
 * FinTrack – Budgets Management Module
 * Connects frontend/budgets.html to the authenticated Java Servlet backend.
 * All ownership is derived from the server session; user_id is never sent.
 */

// Application State
let currentUser = null;
let budgets = [];
let categories = [];
let transactions = [];
let budgetCategoriesMap = new Map(); // budgetId -> Array of BudgetCategory

let editingBudgetId = null;
let deletingBudgetId = null;
let managingBudgetId = null;
let editingAllocationCategoryId = null;
let removingCategoryInfo = null; // { budgetId, categoryId, categoryName, allocatedAmount }

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
 * Formats a date string (YYYY-MM-DD) into readable format (e.g. 01 Oct 2026).
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
 * Returns default start and end dates for the current calendar month.
 */
function getCurrentMonthBounds() {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth();

  const start = new Date(year, month, 1);
  const end = new Date(year, month + 1, 0);

  const startMonthStr = String(start.getMonth() + 1).padStart(2, "0");
  const startDayStr = String(start.getDate()).padStart(2, "0");
  const endMonthStr = String(end.getMonth() + 1).padStart(2, "0");
  const endDayStr = String(end.getDate()).padStart(2, "0");

  return {
    startDate: `${year}-${startMonthStr}-${startDayStr}`,
    endDate: `${year}-${endMonthStr}-${endDayStr}`
  };
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
 * Displays or hides manager-modal error messages.
 */
function showManagerError(message) {
  const managerErr = document.getElementById("managerModalError");
  if (!managerErr) return;
  if (message) {
    managerErr.textContent = message;
    managerErr.style.display = "block";
  } else {
    managerErr.textContent = "";
    managerErr.style.display = "none";
  }
}

/**
 * Initializes the Budgets page.
 * Enforces session authentication via requireAuth().
 */
async function initBudgetsPage() {
  currentUser = await requireAuth();
  if (!currentUser) return; // Redirection handled in requireAuth()

  // Display user header info
  const displayName = currentUser.name || "FinTrack User";
  const userAvatar = document.getElementById("userAvatar");
  const userName = document.getElementById("userName");
  if (userAvatar) userAvatar.textContent = displayName.trim().charAt(0).toUpperCase() || "U";
  if (userName) userName.textContent = displayName;

  // Setup event listeners
  setupEventListeners();

  // Load supporting categories and transactions first
  await Promise.all([loadCategories(), loadTransactions()]);

  // Load budgets and their allocations
  await loadBudgets();
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

  // Create Budget Buttons
  const addBudgetBtn = document.getElementById("addBudgetBtn");
  if (addBudgetBtn) {
    addBudgetBtn.addEventListener("click", openAddBudgetModal);
  }

  const emptyCreateBtn = document.getElementById("emptyCreateBtn");
  if (emptyCreateBtn) {
    emptyCreateBtn.addEventListener("click", openAddBudgetModal);
  }

  // Budget Modal Close & Cancel
  const closeBudgetModalBtn = document.getElementById("closeBudgetModalBtn");
  if (closeBudgetModalBtn) {
    closeBudgetModalBtn.addEventListener("click", closeBudgetModal);
  }

  const cancelBudgetBtn = document.getElementById("cancelBudgetBtn");
  if (cancelBudgetBtn) {
    cancelBudgetBtn.addEventListener("click", closeBudgetModal);
  }

  // Budget Form Submission
  const budgetForm = document.getElementById("budgetForm");
  if (budgetForm) {
    budgetForm.addEventListener("submit", saveBudget);
  }

  // Delete Budget Modal Buttons
  const closeDeleteBudgetModalBtn = document.getElementById("closeDeleteBudgetModalBtn");
  if (closeDeleteBudgetModalBtn) {
    closeDeleteBudgetModalBtn.addEventListener("click", closeDeleteBudgetModal);
  }

  const cancelDeleteBudgetBtn = document.getElementById("cancelDeleteBudgetBtn");
  if (cancelDeleteBudgetBtn) {
    cancelDeleteBudgetBtn.addEventListener("click", closeDeleteBudgetModal);
  }

  const confirmDeleteBudgetBtn = document.getElementById("confirmDeleteBudgetBtn");
  if (confirmDeleteBudgetBtn) {
    confirmDeleteBudgetBtn.addEventListener("click", deleteBudget);
  }

  // Category Manager Modal Buttons
  const closeManagerModalBtn = document.getElementById("closeManagerModalBtn");
  if (closeManagerModalBtn) {
    closeManagerModalBtn.addEventListener("click", closeCategoryManager);
  }

  const closeManagerDoneBtn = document.getElementById("closeManagerDoneBtn");
  if (closeManagerDoneBtn) {
    closeManagerDoneBtn.addEventListener("click", closeCategoryManager);
  }

  // Add Category to Budget Form
  const addCategoryForm = document.getElementById("addCategoryForm");
  if (addCategoryForm) {
    addCategoryForm.addEventListener("submit", addCategoryToBudget);
  }

  // Remove Category Modal Buttons
  const closeRemoveCategoryModalBtn = document.getElementById("closeRemoveCategoryModalBtn");
  if (closeRemoveCategoryModalBtn) {
    closeRemoveCategoryModalBtn.addEventListener("click", closeRemoveCategoryModal);
  }

  const cancelRemoveCategoryBtn = document.getElementById("cancelRemoveCategoryBtn");
  if (cancelRemoveCategoryBtn) {
    cancelRemoveCategoryBtn.addEventListener("click", closeRemoveCategoryModal);
  }

  const confirmRemoveCategoryBtn = document.getElementById("confirmRemoveCategoryBtn");
  if (confirmRemoveCategoryBtn) {
    confirmRemoveCategoryBtn.addEventListener("click", removeCategoryFromBudget);
  }

  // Close modals when clicking outside backdrop
  window.addEventListener("click", (e) => {
    if (e.target.id === "budgetModal") closeBudgetModal();
    if (e.target.id === "categoryManagerModal") closeCategoryManager();
    if (e.target.id === "deleteBudgetModal") closeDeleteBudgetModal();
    if (e.target.id === "removeCategoryModal") closeRemoveCategoryModal();
  });
}

/**
 * Loads available categories from GET /api/categories.
 */
async function loadCategories() {
  try {
    categories = await apiGet("/api/categories");
    if (!Array.isArray(categories)) categories = [];
  } catch (error) {
    showToast("Failed to load categories for allocation.", "error");
  }
}

/**
 * Loads transactions from GET /api/transactions to calculate actual category spending.
 */
async function loadTransactions() {
  try {
    transactions = await apiGet("/api/transactions");
    if (!Array.isArray(transactions)) transactions = [];
  } catch (error) {
    transactions = [];
  }
}

/**
 * Loads all budgets belonging to the authenticated user from GET /api/budgets.
 * Also loads the category allocations for each budget.
 */
async function loadBudgets() {
  const loadingIndicator = document.getElementById("loadingIndicator");
  const emptyState = document.getElementById("emptyState");
  const budgetsGrid = document.getElementById("budgetsGrid");

  if (loadingIndicator) loadingIndicator.style.display = "block";
  if (emptyState) emptyState.style.display = "none";
  if (budgetsGrid) budgetsGrid.style.display = "none";

  try {
    const fetchedBudgets = await apiGet("/api/budgets");
    budgets = Array.isArray(fetchedBudgets) ? fetchedBudgets : [];

    // Load category mappings for each budget in parallel
    budgetCategoriesMap.clear();
    const allocationPromises = budgets.map(async (b) => {
      const budgetId = b.budget_id ?? b.budgetId;
      try {
        const allocs = await apiGet(`/api/budgets/${budgetId}/categories`);
        budgetCategoriesMap.set(budgetId, Array.isArray(allocs) ? allocs : []);
      } catch (err) {
        budgetCategoriesMap.set(budgetId, []);
      }
    });
    await Promise.all(allocationPromises);

    if (loadingIndicator) loadingIndicator.style.display = "none";

    if (budgets.length === 0) {
      if (emptyState) emptyState.style.display = "block";
      if (budgetsGrid) budgetsGrid.style.display = "none";
    } else {
      if (emptyState) emptyState.style.display = "none";
      if (budgetsGrid) {
        budgetsGrid.style.display = "grid";
        renderBudgets();
      }
    }

    renderSummaryMetrics();

  } catch (error) {
    if (loadingIndicator) loadingIndicator.style.display = "none";
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showToast(error.message || "Failed to load budgets. Please try again.", "error");
  }
}

/**
 * Computes and renders top summary stats.
 */
function renderSummaryMetrics() {
  const activeCountEl = document.getElementById("activeBudgetsCount");
  const totalAllocEl = document.getElementById("totalBudgetsAllocated");
  const totalTrackedEl = document.getElementById("totalTrackedCategories");

  const today = getTodayDateString();
  let activeBudgets = 0;
  let totalAllocCents = 0;
  let totalCategoryCount = 0;

  budgets.forEach((b) => {
    const startDate = b.start_date || b.startDate;
    const endDate = b.end_date || b.endDate;
    if (startDate && endDate && today >= startDate && today <= endDate) {
      activeBudgets++;
    }

    const budgetId = b.budget_id ?? b.budgetId;
    const allocs = budgetCategoriesMap.get(budgetId) || [];
    totalCategoryCount += allocs.length;

    allocs.forEach((item) => {
      const amt = Number(item.allocated_amount ?? item.allocatedAmount);
      if (!isNaN(amt)) {
        totalAllocCents += Math.round(amt * 100);
      }
    });
  });

  if (activeCountEl) activeCountEl.textContent = activeBudgets;
  if (totalAllocEl) totalAllocEl.textContent = formatCurrency(totalAllocCents / 100);
  if (totalTrackedEl) totalTrackedEl.textContent = totalCategoryCount;
}

/**
 * Calculates spending for a budget based on Expense transactions within its date window
 * for categories mapped to the budget.
 *
 * @param {object} budget
 * @param {Array} allocations
 * @returns {number} total spent in floating-point units
 */
function calculateBudgetSpending(budget, allocations) {
  if (!allocations || allocations.length === 0) return 0;

  const categoryIdSet = new Set(
    allocations.map((a) => String(a.category_id ?? a.categoryId))
  );

  const startDate = budget.start_date || budget.startDate;
  const endDate = budget.end_date || budget.endDate;

  let spentCents = 0;

  transactions.forEach((tx) => {
    const type = (tx.transaction_type || tx.transactionType || "").trim().toLowerCase();
    if (type !== "expense") return;

    const txDate = tx.transaction_date || tx.transactionDate;
    if (!txDate || txDate < startDate || txDate > endDate) return;

    const catId = String(tx.category_id ?? tx.categoryId);
    if (categoryIdSet.has(catId)) {
      const amt = Number(tx.amount);
      if (!isNaN(amt)) {
        spentCents += Math.round(amt * 100);
      }
    }
  });

  return spentCents / 100;
}

/**
 * Calculates category-specific spending for a given budget.
 */
function calculateCategorySpending(budget, categoryId) {
  const startDate = budget.start_date || budget.startDate;
  const endDate = budget.end_date || budget.endDate;
  const targetCatId = String(categoryId);

  let spentCents = 0;
  transactions.forEach((tx) => {
    const type = (tx.transaction_type || tx.transactionType || "").trim().toLowerCase();
    if (type !== "expense") return;

    const txDate = tx.transaction_date || tx.transactionDate;
    if (!txDate || txDate < startDate || txDate > endDate) return;

    const catId = String(tx.category_id ?? tx.categoryId);
    if (catId === targetCatId) {
      const amt = Number(tx.amount);
      if (!isNaN(amt)) {
        spentCents += Math.round(amt * 100);
      }
    }
  });

  return spentCents / 100;
}

/**
 * Renders the budgets cards in the grid.
 */
function renderBudgets() {
  const grid = document.getElementById("budgetsGrid");
  if (!grid) return;

  grid.innerHTML = "";
  const today = getTodayDateString();

  // Create Category lookup map
  const catMap = new Map();
  categories.forEach((c) => {
    const id = String(c.category_id ?? c.categoryId);
    catMap.set(id, c.category_name ?? c.categoryName);
  });

  budgets.forEach((budget) => {
    const budgetId = budget.budget_id ?? budget.budgetId;
    const name = budget.budget_name ?? budget.budgetName;
    const startDate = budget.start_date || budget.startDate;
    const endDate = budget.end_date || budget.endDate;

    const allocations = budgetCategoriesMap.get(budgetId) || [];

    // Calculate total allocated using integer cents
    let totalAllocCents = 0;
    allocations.forEach((item) => {
      const amt = Number(item.allocated_amount ?? item.allocatedAmount);
      if (!isNaN(amt)) {
        totalAllocCents += Math.round(amt * 100);
      }
    });
    const totalAllocated = totalAllocCents / 100;

    // Calculate actual spending from Expense transactions
    const totalSpent = calculateBudgetSpending(budget, allocations);
    const remaining = totalAllocated - totalSpent;

    // Status badge determination
    let statusClass = "badge-active";
    let statusText = "Active";
    if (today < startDate) {
      statusClass = "badge-upcoming";
      statusText = "Upcoming";
    } else if (today > endDate) {
      statusClass = "badge-past";
      statusText = "Past";
    }

    // Progress bar calculation
    const percentSpent = totalAllocated > 0 ? (totalSpent / totalAllocated) * 100 : 0;
    const clampedProgress = Math.min(100, Math.round(percentSpent));

    let progressClass = "progress-normal";
    if (percentSpent > 100) {
      progressClass = "progress-danger";
    } else if (percentSpent >= 80) {
      progressClass = "progress-warning";
    }

    // Category preview items
    let categoriesPreviewHtml = "";
    if (allocations.length === 0) {
      categoriesPreviewHtml = `<div class="text-muted" style="font-size: 0.8rem; font-style: italic;">No categories assigned yet</div>`;
    } else {
      const previewSlice = allocations.slice(0, 4);
      const itemsHtml = previewSlice
        .map((a) => {
          const cId = String(a.category_id ?? a.categoryId);
          const cName = catMap.get(cId) || `Category #${cId}`;
          const amt = Number(a.allocated_amount ?? a.allocatedAmount);
          return `
            <div class="budget-cat-item">
              <span>${escapeHtml(cName)}</span>
              <strong>${formatCurrency(amt)}</strong>
            </div>
          `;
        })
        .join("");

      const moreCount = allocations.length - previewSlice.length;
      const moreHtml = moreCount > 0 ? `<div class="text-muted" style="font-size: 0.75rem; text-align: right; margin-top: 0.25rem;">+ ${moreCount} more categories</div>` : "";

      categoriesPreviewHtml = `<div class="budget-cat-list">${itemsHtml}</div>${moreHtml}`;
    }

    const card = document.createElement("div");
    card.className = "budget-card";
    card.innerHTML = `
      <div>
        <div class="budget-card-header">
          <div class="budget-title-group">
            <h3>${escapeHtml(name)}</h3>
            <div class="budget-dates">
              <span>📅 ${formatDate(startDate)} → ${formatDate(endDate)}</span>
            </div>
          </div>
          <span class="table-badge ${statusClass}">${statusText}</span>
        </div>

        <div class="budget-metrics-row">
          <div class="budget-metric">
            <span class="budget-metric-label">Allocated</span>
            <span class="budget-metric-val" style="color: var(--accent-green);">${formatCurrency(totalAllocated)}</span>
          </div>
          <div class="budget-metric">
            <span class="budget-metric-label">Spent</span>
            <span class="budget-metric-val" style="color: ${totalSpent > totalAllocated ? '#f87171' : 'var(--text-primary)'};">${formatCurrency(totalSpent)}</span>
          </div>
          <div class="budget-metric">
            <span class="budget-metric-label">Remaining</span>
            <span class="budget-metric-val" style="color: ${remaining < 0 ? '#f87171' : 'var(--accent-blue)'};">${formatCurrency(remaining)}</span>
          </div>
        </div>

        <div class="budget-progress-container">
          <div class="budget-progress-labels">
            <span>Progress (${Math.round(percentSpent)}%)</span>
            <span>${remaining < 0 ? 'Over Budget' : `${formatCurrency(remaining)} left`}</span>
          </div>
          <div class="budget-progress-bar">
            <div class="budget-progress-fill ${progressClass}" style="width: ${clampedProgress}%;"></div>
          </div>
        </div>

        <div class="budget-categories-preview">
          <div class="budget-categories-title">
            <span>Assigned Categories (${allocations.length})</span>
          </div>
          ${categoriesPreviewHtml}
        </div>
      </div>

      <div class="budget-card-actions">
        <button type="button" class="btn btn-primary btn-sm" onclick="openCategoryManager(${budgetId})">
          🏷️ Manage Categories
        </button>
        <button type="button" class="btn btn-secondary btn-sm" onclick="openEditBudgetModal(${budgetId})">
          Edit
        </button>
        <button type="button" class="btn btn-danger btn-sm" onclick="confirmDeleteBudget(${budgetId})">
          Delete
        </button>
      </div>
    `;

    grid.appendChild(card);
  });
}

/**
 * Opens the Add Budget Modal.
 */
function openAddBudgetModal() {
  editingBudgetId = null;
  showModalError("");

  const modalTitle = document.getElementById("modalTitle");
  if (modalTitle) modalTitle.textContent = "Create Budget";

  const saveBtn = document.getElementById("saveBudgetBtn");
  if (saveBtn) saveBtn.textContent = "Create Budget";

  const form = document.getElementById("budgetForm");
  if (form) form.reset();

  // Default dates to current month window
  const bounds = getCurrentMonthBounds();
  const startInput = document.getElementById("startDate");
  const endInput = document.getElementById("endDate");
  if (startInput) startInput.value = bounds.startDate;
  if (endInput) endInput.value = bounds.endDate;

  const modal = document.getElementById("budgetModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Opens the Edit Budget Modal.
 *
 * @param {number|string} budgetId
 */
function openEditBudgetModal(budgetId) {
  const budget = budgets.find((b) => {
    const id = b.budget_id ?? b.budgetId;
    return String(id) === String(budgetId);
  });

  if (!budget) {
    showToast("Budget not found. Please refresh the page.", "error");
    return;
  }

  editingBudgetId = budgetId;
  showModalError("");

  const modalTitle = document.getElementById("modalTitle");
  if (modalTitle) modalTitle.textContent = "Edit Budget";

  const saveBtn = document.getElementById("saveBudgetBtn");
  if (saveBtn) saveBtn.textContent = "Update Budget";

  const nameInput = document.getElementById("budgetName");
  const startInput = document.getElementById("startDate");
  const endInput = document.getElementById("endDate");

  if (nameInput) nameInput.value = budget.budget_name ?? budget.budgetName;
  if (startInput) startInput.value = budget.start_date || budget.startDate || "";
  if (endInput) endInput.value = budget.end_date || budget.endDate || "";

  const modal = document.getElementById("budgetModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Closes the Add/Edit Budget Modal.
 */
function closeBudgetModal() {
  const modal = document.getElementById("budgetModal");
  if (modal) modal.style.display = "none";
  editingBudgetId = null;
  showModalError("");
}

/**
 * Handles Create or Update Budget submission.
 * Enforces end_date >= start_date.
 * NEVER submits client-supplied user_id, budget_id, or created_at.
 */
async function saveBudget(e) {
  e.preventDefault();
  showModalError("");

  const nameInput = document.getElementById("budgetName");
  const startInput = document.getElementById("startDate");
  const endInput = document.getElementById("endDate");
  const saveBtn = document.getElementById("saveBudgetBtn");

  const name = nameInput ? nameInput.value.trim() : "";
  const startDate = startInput ? startInput.value.trim() : "";
  const endDate = endInput ? endInput.value.trim() : "";

  // Validation
  if (!name) {
    showModalError("Please provide a budget name.");
    if (nameInput) nameInput.focus();
    return;
  }

  if (!startDate) {
    showModalError("Please select a valid start date.");
    if (startInput) startInput.focus();
    return;
  }

  if (!endDate) {
    showModalError("Please select a valid end date.");
    if (endInput) endInput.focus();
    return;
  }

  if (endDate < startDate) {
    showModalError("End date must be greater than or equal to start date.");
    if (endInput) endInput.focus();
    return;
  }

  // Construct secure payload
  const payload = {
    budget_name: name,
    start_date: startDate,
    end_date: endDate
  };

  if (saveBtn) {
    saveBtn.disabled = true;
    saveBtn.textContent = "Saving...";
  }

  try {
    if (editingBudgetId) {
      await apiPut(`/api/budgets/${editingBudgetId}`, payload);
      showToast("Budget updated successfully.", "success");
    } else {
      await apiPost("/api/budgets", payload);
      showToast("Budget created successfully.", "success");
    }

    closeBudgetModal();
    await loadBudgets();

  } catch (error) {
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showModalError(error.message || "Failed to save budget. Please check your inputs.");
  } finally {
    if (saveBtn) {
      saveBtn.disabled = false;
      saveBtn.textContent = editingBudgetId ? "Update Budget" : "Create Budget";
    }
  }
}

/**
 * Prepares and displays the Delete Budget Confirmation modal.
 *
 * @param {number|string} budgetId
 */
function confirmDeleteBudget(budgetId) {
  const budget = budgets.find((b) => {
    const id = b.budget_id ?? b.budgetId;
    return String(id) === String(budgetId);
  });

  if (!budget) {
    showToast("Budget not found.", "error");
    return;
  }

  deletingBudgetId = budgetId;

  const detailsBox = document.getElementById("deleteBudgetDetails");
  if (detailsBox) {
    const name = budget.budget_name ?? budget.budgetName;
    const startDate = budget.start_date || budget.startDate;
    const endDate = budget.end_date || budget.endDate;
    const allocs = budgetCategoriesMap.get(budgetId) || [];

    detailsBox.innerHTML = `
      <div><strong>Budget:</strong> ${escapeHtml(name)}</div>
      <div><strong>Period:</strong> ${formatDate(startDate)} → ${formatDate(endDate)}</div>
      <div><strong>Allocated Categories:</strong> ${allocs.length} assigned</div>
    `;
  }

  const modal = document.getElementById("deleteBudgetModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Closes the Delete Budget modal.
 */
function closeDeleteBudgetModal() {
  const modal = document.getElementById("deleteBudgetModal");
  if (modal) modal.style.display = "none";
  deletingBudgetId = null;
}

/**
 * Executes budget deletion via DELETE /api/budgets/{id}.
 */
async function deleteBudget() {
  if (!deletingBudgetId) return;

  const confirmBtn = document.getElementById("confirmDeleteBudgetBtn");
  if (confirmBtn) {
    confirmBtn.disabled = true;
    confirmBtn.textContent = "Deleting...";
  }

  try {
    await apiDelete(`/api/budgets/${deletingBudgetId}`);
    closeDeleteBudgetModal();
    showToast("Budget deleted successfully.", "success");
    await loadBudgets();

  } catch (error) {
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showToast(error.message || "Failed to delete budget. Please try again.", "error");
  } finally {
    if (confirmBtn) {
      confirmBtn.disabled = false;
      confirmBtn.textContent = "Delete Budget";
    }
  }
}

/**
 * Opens Category Manager Modal for a specific budget.
 *
 * @param {number|string} budgetId
 */
async function openCategoryManager(budgetId) {
  const budget = budgets.find((b) => {
    const id = b.budget_id ?? b.budgetId;
    return String(id) === String(budgetId);
  });

  if (!budget) {
    showToast("Budget not found.", "error");
    return;
  }

  managingBudgetId = budgetId;
  editingAllocationCategoryId = null;
  showManagerError("");

  const titleEl = document.getElementById("managerModalTitle");
  const datesEl = document.getElementById("managerModalDates");

  const name = budget.budget_name ?? budget.budgetName;
  const startDate = budget.start_date || budget.startDate;
  const endDate = budget.end_date || budget.endDate;

  if (titleEl) titleEl.textContent = `Categories – ${name}`;
  if (datesEl) datesEl.textContent = `Period: ${formatDate(startDate)} → ${formatDate(endDate)}`;

  // Reset add category form
  const addForm = document.getElementById("addCategoryForm");
  if (addForm) addForm.reset();

  renderManagerView();

  const modal = document.getElementById("categoryManagerModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Closes the Category Manager Modal.
 */
function closeCategoryManager() {
  const modal = document.getElementById("categoryManagerModal");
  if (modal) modal.style.display = "none";
  managingBudgetId = null;
  editingAllocationCategoryId = null;
  showManagerError("");
}

/**
 * Renders the allocated categories list and available category options in the Category Manager.
 */
function renderManagerView() {
  if (!managingBudgetId) return;

  const budget = budgets.find((b) => {
    const id = b.budget_id ?? b.budgetId;
    return String(id) === String(managingBudgetId);
  });
  if (!budget) return;

  const allocations = budgetCategoriesMap.get(managingBudgetId) || [];
  const listEl = document.getElementById("managerCategoriesList");
  const totalAllocEl = document.getElementById("managerTotalAllocated");
  const selectEl = document.getElementById("addCategorySelect");
  const noticeEl = document.getElementById("addCategoryNotice");
  const submitBtn = document.getElementById("submitAddCategoryBtn");

  // Lookup map for category names
  const catMap = new Map();
  categories.forEach((c) => {
    const id = String(c.category_id ?? c.categoryId);
    catMap.set(id, c);
  });

  // Calculate total allocated in integer cents
  let totalAllocCents = 0;
  allocations.forEach((a) => {
    const amt = Number(a.allocated_amount ?? a.allocatedAmount);
    if (!isNaN(amt)) {
      totalAllocCents += Math.round(amt * 100);
    }
  });

  if (totalAllocEl) {
    totalAllocEl.textContent = formatCurrency(totalAllocCents / 100);
  }

  // 1. Render Current Allocations
  if (!listEl) return;
  listEl.innerHTML = "";

  if (allocations.length === 0) {
    listEl.innerHTML = `
      <div class="placeholder-box" style="padding: 1.5rem; text-align: center;">
        <p class="text-muted" style="margin: 0; font-size: 0.9rem;">
          No categories have been assigned to this budget. Use the form below to allocate spending limits.
        </p>
      </div>
    `;
  } else {
    allocations.forEach((alloc) => {
      const catId = alloc.category_id ?? alloc.categoryId;
      const amount = Number(alloc.allocated_amount ?? alloc.allocatedAmount);
      const catObj = catMap.get(String(catId));
      const catName = catObj ? (catObj.category_name ?? catObj.categoryName) : `Category #${catId}`;
      const isCustom = catObj && (catObj.user_id ?? catObj.userId) !== null && (catObj.user_id ?? catObj.userId) !== undefined;

      const spent = calculateCategorySpending(budget, catId);

      const row = document.createElement("div");
      row.className = "cat-manager-row";

      // Check if this row is currently in inline edit mode
      if (editingAllocationCategoryId === catId) {
        row.innerHTML = `
          <div class="cat-manager-row-info">
            <strong>${escapeHtml(catName)} ${isCustom ? '<span class="table-badge badge-custom" style="font-size: 0.65rem;">Custom</span>' : ''}</strong>
            <span class="text-muted" style="font-size: 0.775rem;">Spent: ${formatCurrency(spent)}</span>
          </div>
          <div class="cat-manager-row-actions">
            <span style="font-size: 0.85rem; color: var(--text-muted);">₹</span>
            <input 
              type="number" 
              id="inlineEditAmountInput" 
              class="form-control cat-manager-input-sm" 
              step="0.01" 
              min="0.01" 
              value="${amount.toFixed(2)}"
            >
            <button type="button" class="btn btn-primary btn-sm" onclick="saveCategoryAllocationEdit(${catId})">Save</button>
            <button type="button" class="btn btn-secondary btn-sm" onclick="cancelCategoryAllocationEdit()">Cancel</button>
          </div>
        `;
      } else {
        row.innerHTML = `
          <div class="cat-manager-row-info">
            <strong>${escapeHtml(catName)} ${isCustom ? '<span class="table-badge badge-custom" style="font-size: 0.65rem;">Custom</span>' : ''}</strong>
            <span class="text-muted" style="font-size: 0.775rem;">
              Allocated: <strong style="color: var(--accent-green);">${formatCurrency(amount)}</strong> | Spent: ${formatCurrency(spent)}
            </span>
          </div>
          <div class="cat-manager-row-actions">
            <button type="button" class="btn btn-secondary btn-sm" onclick="startCategoryAllocationEdit(${catId})">Edit Amount</button>
            <button type="button" class="btn btn-danger btn-sm" onclick="confirmRemoveCategory(${managingBudgetId}, ${catId}, '${escapeHtml(catName)}', ${amount})">Remove</button>
          </div>
        `;
      }

      listEl.appendChild(row);
    });
  }

  // 2. Populate Available Categories in Add dropdown
  // Exclude categories already assigned to this budget
  const assignedCatIds = new Set(
    allocations.map((a) => String(a.category_id ?? a.categoryId))
  );

  const available = categories.filter((c) => {
    const id = String(c.category_id ?? c.categoryId);
    const type = (c.category_type || c.categoryType || "").trim().toLowerCase();
    // Only allow expense categories for budgeting spending limits, and not already assigned
    return !assignedCatIds.has(id) && (type === "expense" || type === "");
  });

  if (selectEl) {
    selectEl.innerHTML = '<option value="">Select Category</option>';
    available.forEach((c) => {
      const id = c.category_id ?? c.categoryId;
      const name = c.category_name ?? c.categoryName;
      const isCustom = (c.user_id ?? c.userId) !== null && (c.user_id ?? c.userId) !== undefined;

      const opt = document.createElement("option");
      opt.value = id;
      opt.textContent = isCustom ? `${name} (Custom)` : name;
      selectEl.appendChild(opt);
    });
  }

  if (available.length === 0) {
    if (noticeEl) {
      noticeEl.textContent = "All available categories are already assigned to this budget.";
      noticeEl.style.display = "block";
    }
    if (submitBtn) submitBtn.disabled = true;
    if (selectEl) selectEl.disabled = true;
  } else {
    if (noticeEl) noticeEl.style.display = "none";
    if (submitBtn) submitBtn.disabled = false;
    if (selectEl) selectEl.disabled = false;
  }
}

/**
 * Handles adding a category to the budget.
 * POST /api/budgets/{budgetId}/categories
 */
async function addCategoryToBudget(e) {
  e.preventDefault();
  showManagerError("");

  if (!managingBudgetId) return;

  const selectEl = document.getElementById("addCategorySelect");
  const amountEl = document.getElementById("addCategoryAmount");
  const submitBtn = document.getElementById("submitAddCategoryBtn");

  const catIdStr = selectEl ? selectEl.value.trim() : "";
  const amountStr = amountEl ? amountEl.value.trim() : "";

  if (!catIdStr) {
    showManagerError("Please select a category to allocate.");
    if (selectEl) selectEl.focus();
    return;
  }

  const amountNum = parseFloat(amountStr);
  if (isNaN(amountNum) || amountNum <= 0) {
    showManagerError("Allocated amount must be strictly greater than 0.");
    if (amountEl) amountEl.focus();
    return;
  }

  const payload = {
    category_id: parseInt(catIdStr, 10),
    allocated_amount: amountNum
  };

  if (submitBtn) {
    submitBtn.disabled = true;
    submitBtn.textContent = "Adding...";
  }

  try {
    await apiPost(`/api/budgets/${managingBudgetId}/categories`, payload);
    showToast("Category allocated to budget.", "success");

    // Refresh allocations
    const updatedAllocs = await apiGet(`/api/budgets/${managingBudgetId}/categories`);
    budgetCategoriesMap.set(managingBudgetId, Array.isArray(updatedAllocs) ? updatedAllocs : []);

    if (amountEl) amountEl.value = "";
    renderManagerView();
    renderBudgets();
    renderSummaryMetrics();

  } catch (error) {
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    if (error.status === 409) {
      showManagerError("This category is already assigned to this budget.");
    } else {
      showManagerError(error.message || "Failed to allocate category. Please try again.");
    }
  } finally {
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.textContent = "+ Add Category Allocation";
    }
  }
}

/**
 * Switches a category row into inline edit mode.
 */
function startCategoryAllocationEdit(categoryId) {
  editingAllocationCategoryId = categoryId;
  showManagerError("");
  renderManagerView();
}

/**
 * Cancels inline edit mode.
 */
function cancelCategoryAllocationEdit() {
  editingAllocationCategoryId = null;
  showManagerError("");
  renderManagerView();
}

/**
 * Saves updated allocated amount for a category.
 * PUT /api/budgets/{budgetId}/categories/{categoryId}
 */
async function saveCategoryAllocationEdit(categoryId) {
  showManagerError("");
  if (!managingBudgetId) return;

  const inputEl = document.getElementById("inlineEditAmountInput");
  const amountStr = inputEl ? inputEl.value.trim() : "";
  const amountNum = parseFloat(amountStr);

  if (isNaN(amountNum) || amountNum <= 0) {
    showManagerError("Allocated amount must be strictly greater than 0.");
    if (inputEl) inputEl.focus();
    return;
  }

  const payload = {
    allocated_amount: amountNum
  };

  try {
    await apiPut(`/api/budgets/${managingBudgetId}/categories/${categoryId}`, payload);
    showToast("Category allocation updated.", "success");

    editingAllocationCategoryId = null;

    // Refresh allocations
    const updatedAllocs = await apiGet(`/api/budgets/${managingBudgetId}/categories`);
    budgetCategoriesMap.set(managingBudgetId, Array.isArray(updatedAllocs) ? updatedAllocs : []);

    renderManagerView();
    renderBudgets();
    renderSummaryMetrics();

  } catch (error) {
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showManagerError(error.message || "Failed to update allocation.");
  }
}

/**
 * Opens Remove Category Confirmation Modal.
 */
function confirmRemoveCategory(budgetId, categoryId, categoryName, allocatedAmount) {
  removingCategoryInfo = {
    budgetId,
    categoryId,
    categoryName,
    allocatedAmount
  };

  const detailsEl = document.getElementById("removeCategoryDetails");
  if (detailsEl) {
    detailsEl.innerHTML = `
      <div><strong>Category:</strong> ${escapeHtml(categoryName)}</div>
      <div><strong>Current Allocation:</strong> ${formatCurrency(allocatedAmount)}</div>
    `;
  }

  const modal = document.getElementById("removeCategoryModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Closes Remove Category Confirmation Modal.
 */
function closeRemoveCategoryModal() {
  const modal = document.getElementById("removeCategoryModal");
  if (modal) modal.style.display = "none";
  removingCategoryInfo = null;
}

/**
 * Executes removal of category allocation from budget.
 * DELETE /api/budgets/{budgetId}/categories/{categoryId}
 */
async function removeCategoryFromBudget() {
  if (!removingCategoryInfo) return;

  const { budgetId, categoryId } = removingCategoryInfo;
  const confirmBtn = document.getElementById("confirmRemoveCategoryBtn");

  if (confirmBtn) {
    confirmBtn.disabled = true;
    confirmBtn.textContent = "Removing...";
  }

  try {
    await apiDelete(`/api/budgets/${budgetId}/categories/${categoryId}`);
    closeRemoveCategoryModal();
    showToast("Category removed from budget.", "success");

    // Refresh allocations
    const updatedAllocs = await apiGet(`/api/budgets/${budgetId}/categories`);
    budgetCategoriesMap.set(budgetId, Array.isArray(updatedAllocs) ? updatedAllocs : []);

    renderManagerView();
    renderBudgets();
    renderSummaryMetrics();

  } catch (error) {
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showToast(error.message || "Failed to remove category from budget.", "error");
  } finally {
    if (confirmBtn) {
      confirmBtn.disabled = false;
      confirmBtn.textContent = "Remove Allocation";
    }
  }
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

// Global exposure for inline HTML event handlers
window.openCategoryManager = openCategoryManager;
window.openEditBudgetModal = openEditBudgetModal;
window.confirmDeleteBudget = confirmDeleteBudget;
window.startCategoryAllocationEdit = startCategoryAllocationEdit;
window.cancelCategoryAllocationEdit = cancelCategoryAllocationEdit;
window.saveCategoryAllocationEdit = saveCategoryAllocationEdit;
window.confirmRemoveCategory = confirmRemoveCategory;

// Initialize on DOMContentLoaded
document.addEventListener("DOMContentLoaded", initBudgetsPage);
