/**
 * FinTrack – Savings Goals Management Module
 * Connects frontend/savings-goals.html to the authenticated Java Servlet backend.
 * All ownership is derived from the server session; user_id is never sent.
 */

// Application State
let currentUser = null;
let accounts = [];
let savingsGoals = [];

let editingGoalId = null;
let deletingGoalId = null;
let quickUpdatingGoalId = null;

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
 * Formats a date string (YYYY-MM-DD) into readable format (e.g. 31 Dec 2026).
 */
function formatDate(dateStr) {
  if (!dateStr) return "No target date";
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
 * Displays or hides quick-update modal error messages.
 */
function showQuickUpdateError(message) {
  const quickErr = document.getElementById("quickUpdateError");
  if (!quickErr) return;
  if (message) {
    quickErr.textContent = message;
    quickErr.style.display = "block";
  } else {
    quickErr.textContent = "";
    quickErr.style.display = "none";
  }
}

/**
 * Initializes the Savings Goals page.
 * Enforces session authentication via requireAuth().
 */
async function initSavingsGoalsPage() {
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

  // Load supporting accounts for linked account dropdown
  await loadAccounts();

  // Load savings goals
  await loadSavingsGoals();
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

  // Create Goal Buttons
  const addGoalBtn = document.getElementById("addGoalBtn");
  if (addGoalBtn) {
    addGoalBtn.addEventListener("click", openAddGoalModal);
  }

  const emptyCreateGoalBtn = document.getElementById("emptyCreateGoalBtn");
  if (emptyCreateGoalBtn) {
    emptyCreateGoalBtn.addEventListener("click", openAddGoalModal);
  }

  // Goal Modal Close & Cancel
  const closeGoalModalBtn = document.getElementById("closeGoalModalBtn");
  if (closeGoalModalBtn) {
    closeGoalModalBtn.addEventListener("click", closeGoalModal);
  }

  const cancelGoalBtn = document.getElementById("cancelGoalBtn");
  if (cancelGoalBtn) {
    cancelGoalBtn.addEventListener("click", closeGoalModal);
  }

  // Goal Form Submission
  const goalForm = document.getElementById("goalForm");
  if (goalForm) {
    goalForm.addEventListener("submit", saveGoal);
  }

  // Quick Update Modal Buttons
  const closeQuickUpdateModalBtn = document.getElementById("closeQuickUpdateModalBtn");
  if (closeQuickUpdateModalBtn) {
    closeQuickUpdateModalBtn.addEventListener("click", closeQuickUpdateModal);
  }

  const cancelQuickUpdateBtn = document.getElementById("cancelQuickUpdateBtn");
  if (cancelQuickUpdateBtn) {
    cancelQuickUpdateBtn.addEventListener("click", closeQuickUpdateModal);
  }

  const quickUpdateForm = document.getElementById("quickUpdateForm");
  if (quickUpdateForm) {
    quickUpdateForm.addEventListener("submit", saveQuickUpdate);
  }

  // Delete Goal Modal Buttons
  const closeDeleteGoalModalBtn = document.getElementById("closeDeleteGoalModalBtn");
  if (closeDeleteGoalModalBtn) {
    closeDeleteGoalModalBtn.addEventListener("click", closeDeleteGoalModal);
  }

  const cancelDeleteGoalBtn = document.getElementById("cancelDeleteGoalBtn");
  if (cancelDeleteGoalBtn) {
    cancelDeleteGoalBtn.addEventListener("click", closeDeleteGoalModal);
  }

  const confirmDeleteGoalBtn = document.getElementById("confirmDeleteGoalBtn");
  if (confirmDeleteGoalBtn) {
    confirmDeleteGoalBtn.addEventListener("click", deleteGoal);
  }

  // Close modals when clicking outside backdrop
  window.addEventListener("click", (e) => {
    if (e.target.id === "goalModal") closeGoalModal();
    if (e.target.id === "quickUpdateModal") closeQuickUpdateModal();
    if (e.target.id === "deleteGoalModal") closeDeleteGoalModal();
  });
}

/**
 * Loads the authenticated user's accounts from GET /api/accounts.
 */
async function loadAccounts() {
  try {
    accounts = await apiGet("/api/accounts");
    if (!Array.isArray(accounts)) accounts = [];

    // Populate Linked Account Select
    const select = document.getElementById("linkedAccount");
    if (select) {
      select.innerHTML = '<option value="">No linked account</option>';
      accounts.forEach((acc) => {
        const id = acc.account_id ?? acc.accountId;
        const name = acc.account_name ?? acc.accountName;
        const opt = document.createElement("option");
        opt.value = id;
        opt.textContent = name;
        select.appendChild(opt);
      });
    }
  } catch (error) {
    showToast("Failed to load accounts for linkage.", "error");
  }
}

/**
 * Loads savings goals from GET /api/savings-goals.
 */
async function loadSavingsGoals() {
  const loadingIndicator = document.getElementById("loadingIndicator");
  const emptyState = document.getElementById("emptyState");
  const goalsGrid = document.getElementById("goalsGrid");

  if (loadingIndicator) loadingIndicator.style.display = "block";
  if (emptyState) emptyState.style.display = "none";
  if (goalsGrid) goalsGrid.style.display = "none";

  try {
    const result = await apiGet("/api/savings-goals");
    savingsGoals = Array.isArray(result) ? result : [];

    if (loadingIndicator) loadingIndicator.style.display = "none";

    if (savingsGoals.length === 0) {
      if (emptyState) emptyState.style.display = "block";
      if (goalsGrid) goalsGrid.style.display = "none";
    } else {
      if (emptyState) emptyState.style.display = "none";
      if (goalsGrid) {
        goalsGrid.style.display = "grid";
        renderGoals();
      }
    }

    renderSummaryMetrics();

  } catch (error) {
    if (loadingIndicator) loadingIndicator.style.display = "none";
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showToast(error.message || "Failed to load savings goals. Please try again.", "error");
  }
}

/**
 * Computes and renders top summary stats for Active goals.
 */
function renderSummaryMetrics() {
  const activeCountEl = document.getElementById("activeGoalsCount");
  const totalTargetEl = document.getElementById("totalActiveTarget");
  const totalSavedEl = document.getElementById("totalActiveSaved");
  const overallProgEl = document.getElementById("overallProgressPercent");

  let activeCount = 0;
  let totalTargetCents = 0;
  let totalSavedCents = 0;

  savingsGoals.forEach((goal) => {
    const status = (goal.status || "Active").trim();
    if (status.toLowerCase() === "active") {
      activeCount++;
      const target = Number(goal.target_amount ?? goal.targetAmount);
      const saved = Number(goal.saved_amount ?? goal.savedAmount);
      if (!isNaN(target)) totalTargetCents += Math.round(target * 100);
      if (!isNaN(saved)) totalSavedCents += Math.round(saved * 100);
    }
  });

  const overallPercent = totalTargetCents > 0 ? Math.round((totalSavedCents / totalTargetCents) * 100) : 0;

  if (activeCountEl) activeCountEl.textContent = activeCount;
  if (totalTargetEl) totalTargetEl.textContent = formatCurrency(totalTargetCents / 100);
  if (totalSavedEl) totalSavedEl.textContent = formatCurrency(totalSavedCents / 100);
  if (overallProgEl) overallProgEl.textContent = `${overallPercent}%`;
}

/**
 * Renders savings goals cards in the responsive grid.
 */
function renderGoals() {
  const grid = document.getElementById("goalsGrid");
  if (!grid) return;

  grid.innerHTML = "";

  // Lookup map for accounts
  const accountMap = new Map();
  accounts.forEach((a) => {
    const id = String(a.account_id ?? a.accountId);
    accountMap.set(id, a.account_name ?? a.accountName);
  });

  savingsGoals.forEach((goal) => {
    const goalId = goal.goal_id ?? goal.goalId;
    const name = goal.goal_name ?? goal.goalName;
    const targetAmt = Number(goal.target_amount ?? goal.targetAmount);
    const savedAmt = Number(goal.saved_amount ?? goal.savedAmount);
    const targetDate = goal.target_date || goal.targetDate;
    const status = (goal.status || "Active").trim();
    const accountId = goal.account_id ?? goal.accountId;

    // Remaining calculation using integer cents
    const targetCents = Math.round(targetAmt * 100);
    const savedCents = Math.round(savedAmt * 100);
    const remainingCents = Math.max(0, targetCents - savedCents);
    const remaining = remainingCents / 100;

    // Progress percentage calculation
    const progress = targetAmt > 0 ? (savedAmt / targetAmt) * 100 : 0;
    const displayProgress = Math.round(progress);
    const barProgress = Math.min(100, Math.round(progress));

    // Status Badge and Progress Bar Styling
    let badgeClass = "badge-income";
    let progressClass = "progress-goal-active";

    if (status.toLowerCase() === "completed" || progress >= 100) {
      badgeClass = "badge-completed";
      progressClass = "progress-goal-completed";
    } else if (status.toLowerCase() === "cancelled") {
      badgeClass = "badge-cancelled";
      progressClass = "progress-goal-cancelled";
    }

    const linkedAccountName = accountId ? accountMap.get(String(accountId)) || `Account #${accountId}` : null;

    const card = document.createElement("div");
    card.className = "goal-card";
    card.innerHTML = `
      <div>
        <div class="goal-card-header">
          <div class="goal-title-group">
            <h3>${escapeHtml(name)}</h3>
            <div class="goal-account-tag">
              ${linkedAccountName ? `<span>🏦 ${escapeHtml(linkedAccountName)}</span>` : '<span class="text-muted">Unlinked</span>'}
            </div>
          </div>
          <span class="table-badge ${badgeClass}">${escapeHtml(status)}</span>
        </div>

        <div class="goal-amounts-row">
          <div class="goal-amount-block">
            <span class="goal-amount-label">Saved</span>
            <span class="goal-amount-val" style="color: var(--accent-green);">${formatCurrency(savedAmt)}</span>
          </div>
          <div class="goal-amount-block">
            <span class="goal-amount-label">Target</span>
            <span class="goal-amount-val">${formatCurrency(targetAmt)}</span>
          </div>
          <div class="goal-amount-block">
            <span class="goal-amount-label">Remaining</span>
            <span class="goal-amount-val" style="color: ${remaining === 0 ? 'var(--accent-green)' : 'var(--text-secondary)'};">${formatCurrency(remaining)}</span>
          </div>
        </div>

        <div class="goal-progress-container">
          <div class="goal-progress-header">
            <span>Progress: <strong>${displayProgress}%</strong></span>
            <span>${remaining === 0 ? 'Target Reached! 🎉' : `${formatCurrency(remaining)} left`}</span>
          </div>
          <div class="goal-progress-bar">
            <div class="goal-progress-fill ${progressClass}" style="width: ${barProgress}%;"></div>
          </div>
        </div>

        <div class="goal-meta-footer">
          <span>📅 Target Date:</span>
          <strong>${formatDate(targetDate)}</strong>
        </div>
      </div>

      <div class="goal-card-actions">
        <button type="button" class="btn btn-primary btn-sm" onclick="openQuickUpdateModal(${goalId})">
          💰 Update Saved
        </button>
        <button type="button" class="btn btn-secondary btn-sm" onclick="openEditGoalModal(${goalId})">
          Edit
        </button>
        <button type="button" class="btn btn-danger btn-sm" onclick="confirmDeleteGoal(${goalId})">
          Delete
        </button>
      </div>
    `;

    grid.appendChild(card);
  });
}

/**
 * Opens Add Savings Goal Modal.
 */
function openAddGoalModal() {
  editingGoalId = null;
  showModalError("");

  const modalTitle = document.getElementById("modalTitle");
  if (modalTitle) modalTitle.textContent = "Create Savings Goal";

  const saveBtn = document.getElementById("saveGoalBtn");
  if (saveBtn) saveBtn.textContent = "Save Goal";

  const form = document.getElementById("goalForm");
  if (form) form.reset();

  const savedInput = document.getElementById("savedAmount");
  if (savedInput) savedInput.value = "0.00";

  const statusSelect = document.getElementById("goalStatus");
  if (statusSelect) statusSelect.value = "Active";

  const modal = document.getElementById("goalModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Opens Edit Savings Goal Modal with existing values populated.
 *
 * @param {number|string} goalId
 */
function openEditGoalModal(goalId) {
  const goal = savingsGoals.find((g) => {
    const id = g.goal_id ?? g.goalId;
    return String(id) === String(goalId);
  });

  if (!goal) {
    showToast("Savings goal not found.", "error");
    return;
  }

  editingGoalId = goalId;
  showModalError("");

  const modalTitle = document.getElementById("modalTitle");
  if (modalTitle) modalTitle.textContent = "Edit Savings Goal";

  const saveBtn = document.getElementById("saveGoalBtn");
  if (saveBtn) saveBtn.textContent = "Update Goal";

  const nameInput = document.getElementById("goalName");
  const targetInput = document.getElementById("targetAmount");
  const savedInput = document.getElementById("savedAmount");
  const dateInput = document.getElementById("targetDate");
  const accountSelect = document.getElementById("linkedAccount");
  const statusSelect = document.getElementById("goalStatus");

  if (nameInput) nameInput.value = goal.goal_name ?? goal.goalName;
  if (targetInput) targetInput.value = Number(goal.target_amount ?? goal.targetAmount).toFixed(2);
  if (savedInput) savedInput.value = Number(goal.saved_amount ?? goal.savedAmount).toFixed(2);
  if (dateInput) dateInput.value = goal.target_date || goal.targetDate || "";
  if (accountSelect) accountSelect.value = goal.account_id ?? goal.accountId ?? "";
  if (statusSelect) statusSelect.value = goal.status || "Active";

  const modal = document.getElementById("goalModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Closes the Add/Edit Goal Modal.
 */
function closeGoalModal() {
  const modal = document.getElementById("goalModal");
  if (modal) modal.style.display = "none";
  editingGoalId = null;
  showModalError("");
}

/**
 * Saves a new or edited savings goal.
 * NEVER submits client-supplied user_id, goal_id, or created_at.
 */
async function saveGoal(e) {
  e.preventDefault();
  showModalError("");

  const nameInput = document.getElementById("goalName");
  const targetInput = document.getElementById("targetAmount");
  const savedInput = document.getElementById("savedAmount");
  const dateInput = document.getElementById("targetDate");
  const accountSelect = document.getElementById("linkedAccount");
  const statusSelect = document.getElementById("goalStatus");
  const saveBtn = document.getElementById("saveGoalBtn");

  const goalName = nameInput ? nameInput.value.trim() : "";
  const targetStr = targetInput ? targetInput.value.trim() : "";
  const savedStr = savedInput ? savedInput.value.trim() : "";
  const targetDate = dateInput ? dateInput.value.trim() : "";
  const accountIdStr = accountSelect ? accountSelect.value.trim() : "";
  const status = statusSelect ? statusSelect.value.trim() : "Active";

  // Validation
  if (!goalName) {
    showModalError("Please provide a goal name.");
    if (nameInput) nameInput.focus();
    return;
  }

  const targetAmount = parseFloat(targetStr);
  if (isNaN(targetAmount) || targetAmount <= 0) {
    showModalError("Target amount must be strictly greater than 0.");
    if (targetInput) targetInput.focus();
    return;
  }

  const savedAmount = savedStr ? parseFloat(savedStr) : 0;
  if (isNaN(savedAmount) || savedAmount < 0) {
    showModalError("Saved amount cannot be negative.");
    if (savedInput) savedInput.focus();
    return;
  }

  if (status !== "Active" && status !== "Completed" && status !== "Cancelled") {
    showModalError("Please select a valid goal status.");
    return;
  }

  // Construct secure payload without user_id, goal_id, or created_at
  const payload = {
    goal_name: goalName,
    target_amount: targetAmount,
    saved_amount: savedAmount,
    target_date: targetDate || null,
    account_id: accountIdStr ? parseInt(accountIdStr, 10) : null,
    status
  };

  if (saveBtn) {
    saveBtn.disabled = true;
    saveBtn.textContent = "Saving...";
  }

  try {
    if (editingGoalId) {
      await apiPut(`/api/savings-goals/${editingGoalId}`, payload);
      showToast("Savings goal updated successfully.", "success");
    } else {
      await apiPost("/api/savings-goals", payload);
      showToast("Savings goal created successfully.", "success");
    }

    closeGoalModal();
    await loadSavingsGoals();

  } catch (error) {
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showModalError(error.message || "Failed to save savings goal. Please verify your inputs.");
  } finally {
    if (saveBtn) {
      saveBtn.disabled = false;
      saveBtn.textContent = editingGoalId ? "Update Goal" : "Save Goal";
    }
  }
}

/**
 * Opens the Quick Update Saved Amount modal.
 *
 * @param {number|string} goalId
 */
function openQuickUpdateModal(goalId) {
  const goal = savingsGoals.find((g) => {
    const id = g.goal_id ?? g.goalId;
    return String(id) === String(goalId);
  });

  if (!goal) {
    showToast("Savings goal not found.", "error");
    return;
  }

  quickUpdatingGoalId = goalId;
  showQuickUpdateError("");

  const nameEl = document.getElementById("quickGoalName");
  const targetInfoEl = document.getElementById("quickTargetInfo");
  const inputEl = document.getElementById("quickSavedAmount");
  const statusEl = document.getElementById("quickGoalStatus");

  const name = goal.goal_name ?? goal.goalName;
  const targetAmt = Number(goal.target_amount ?? goal.targetAmount);
  const currentSaved = Number(goal.saved_amount ?? goal.savedAmount);

  if (nameEl) nameEl.textContent = name;
  if (targetInfoEl) targetInfoEl.textContent = `Target: ${formatCurrency(targetAmt)} | Currently Saved: ${formatCurrency(currentSaved)}`;
  if (inputEl) inputEl.value = currentSaved.toFixed(2);
  if (statusEl) statusEl.value = goal.status || "Active";

  const modal = document.getElementById("quickUpdateModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Closes the Quick Update modal.
 */
function closeQuickUpdateModal() {
  const modal = document.getElementById("quickUpdateModal");
  if (modal) modal.style.display = "none";
  quickUpdatingGoalId = null;
  showQuickUpdateError("");
}

/**
 * Saves the updated saved amount and status from the quick update modal.
 */
async function saveQuickUpdate(e) {
  e.preventDefault();
  showQuickUpdateError("");

  if (!quickUpdatingGoalId) return;

  const inputEl = document.getElementById("quickSavedAmount");
  const statusEl = document.getElementById("quickGoalStatus");
  const saveBtn = document.getElementById("saveQuickUpdateBtn");

  const amountStr = inputEl ? inputEl.value.trim() : "";
  const statusVal = statusEl ? statusEl.value.trim() : "Active";

  const newSaved = parseFloat(amountStr);
  if (isNaN(newSaved) || newSaved < 0) {
    showQuickUpdateError("Saved amount cannot be negative.");
    if (inputEl) inputEl.focus();
    return;
  }

  const payload = {
    saved_amount: newSaved,
    status: statusVal
  };

  if (saveBtn) {
    saveBtn.disabled = true;
    saveBtn.textContent = "Updating...";
  }

  try {
    await apiPut(`/api/savings-goals/${quickUpdatingGoalId}`, payload);
    closeQuickUpdateModal();
    showToast("Saved amount updated successfully.", "success");
    await loadSavingsGoals();

  } catch (error) {
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showQuickUpdateError(error.message || "Failed to update saved amount.");
  } finally {
    if (saveBtn) {
      saveBtn.disabled = false;
      saveBtn.textContent = "Update Amount";
    }
  }
}

/**
 * Prepares and displays the Delete Goal Confirmation modal.
 *
 * @param {number|string} goalId
 */
function confirmDeleteGoal(goalId) {
  const goal = savingsGoals.find((g) => {
    const id = g.goal_id ?? g.goalId;
    return String(id) === String(goalId);
  });

  if (!goal) {
    showToast("Savings goal not found.", "error");
    return;
  }

  deletingGoalId = goalId;

  const detailsBox = document.getElementById("deleteGoalDetails");
  if (detailsBox) {
    const name = goal.goal_name ?? goal.goalName;
    const targetAmt = Number(goal.target_amount ?? goal.targetAmount);
    const savedAmt = Number(goal.saved_amount ?? goal.savedAmount);
    const status = goal.status || "Active";

    detailsBox.innerHTML = `
      <div><strong>Goal:</strong> ${escapeHtml(name)}</div>
      <div><strong>Target Amount:</strong> ${formatCurrency(targetAmt)}</div>
      <div><strong>Saved Amount:</strong> ${formatCurrency(savedAmt)}</div>
      <div><strong>Status:</strong> ${escapeHtml(status)}</div>
    `;
  }

  const modal = document.getElementById("deleteGoalModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Closes the Delete Goal modal.
 */
function closeDeleteGoalModal() {
  const modal = document.getElementById("deleteGoalModal");
  if (modal) modal.style.display = "none";
  deletingGoalId = null;
}

/**
 * Executes savings goal deletion via DELETE /api/savings-goals/{id}.
 */
async function deleteGoal() {
  if (!deletingGoalId) return;

  const confirmBtn = document.getElementById("confirmDeleteGoalBtn");
  if (confirmBtn) {
    confirmBtn.disabled = true;
    confirmBtn.textContent = "Deleting...";
  }

  try {
    await apiDelete(`/api/savings-goals/${deletingGoalId}`);
    closeDeleteGoalModal();
    showToast("Savings goal deleted successfully.", "success");
    await loadSavingsGoals();

  } catch (error) {
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showToast(error.message || "Failed to delete savings goal. Please try again.", "error");
  } finally {
    if (confirmBtn) {
      confirmBtn.disabled = false;
      confirmBtn.textContent = "Delete Goal";
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
window.openQuickUpdateModal = openQuickUpdateModal;
window.openEditGoalModal = openEditGoalModal;
window.confirmDeleteGoal = confirmDeleteGoal;

// Initialize on DOMContentLoaded
document.addEventListener("DOMContentLoaded", initSavingsGoalsPage);
