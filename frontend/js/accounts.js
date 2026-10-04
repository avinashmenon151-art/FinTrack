/**
 * FinTrack – Accounts Management Module
 * Connects frontend/accounts.html to the authenticated Java Servlet backend.
 * All ownership is derived from the server session; user_id is never sent.
 */

// State
let accounts = [];
let currentUser = null;
let currentDefaultAccountId = null;
let editingAccountId = null;
let deletingAccountId = null;

// Currency Formatter (Indian Rupee context)
const inrFormatter = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

/**
 * Formats a numeric balance or BigDecimal string to INR currency.
 */
function formatCurrency(amount) {
  const num = Number(amount);
  if (isNaN(num)) return "₹0.00";
  return inrFormatter.format(num);
}

/**
 * Initializes the Accounts page.
 * Enforces session authentication via requireAuth().
 */
async function initAccountsPage() {
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

  // Load accounts from backend
  await loadAccounts();
}

/**
 * Loads the authenticated user's accounts from GET /api/accounts.
 */
async function loadAccounts() {
  const loadingEl = document.getElementById("loadingIndicator");
  const emptyEl = document.getElementById("emptyState");
  const gridEl = document.getElementById("accountsGrid");

  if (loadingEl) loadingEl.style.display = "block";
  if (emptyEl) emptyEl.style.display = "none";
  if (gridEl) gridEl.style.display = "none";

  try {
    // Refresh user profile to ensure up-to-date default_account_id
    const freshUser = await getCurrentUser();
    if (freshUser) {
      currentUser = freshUser;
      currentDefaultAccountId = freshUser.default_account_id ?? freshUser.defaultAccountId ?? null;
    }

    // Call GET /api/accounts with credentials included (no user_id sent)
    accounts = await apiGet("/api/accounts");

    if (loadingEl) loadingEl.style.display = "none";

    if (!accounts || accounts.length === 0) {
      if (emptyEl) emptyEl.style.display = "block";
      if (gridEl) gridEl.style.display = "none";
    } else {
      if (emptyEl) emptyEl.style.display = "none";
      if (gridEl) {
        gridEl.style.display = "grid";
        renderAccounts();
      }
    }
  } catch (error) {
    if (loadingEl) loadingEl.style.display = "none";
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showToast(error.message || "Failed to load accounts. Please try again.", "error");
  }
}

/**
 * Renders the accounts grid cards.
 */
function renderAccounts() {
  const gridEl = document.getElementById("accountsGrid");
  if (!gridEl) return;

  gridEl.innerHTML = "";

  accounts.forEach((account) => {
    const accountId = account.account_id ?? account.accountId;
    const accountName = account.account_name ?? account.accountName ?? "Unnamed Account";
    const accountType = account.account_type ?? account.accountType ?? "Checking";
    const balance = account.balance ?? 0;
    const isDefault = (currentDefaultAccountId != null && Number(currentDefaultAccountId) === Number(accountId));

    const card = document.createElement("div");
    card.className = `account-card ${isDefault ? "is-default" : ""}`;
    card.id = `account-card-${accountId}`;

    card.innerHTML = `
      <div>
        <div class="account-top">
          <div class="account-name-group">
            <h3>${escapeHtml(accountName)}</h3>
            <span class="account-type-badge">${escapeHtml(accountType)}</span>
          </div>
          ${isDefault ? '<span class="account-default-badge">★ Default Account</span>' : ""}
        </div>

        <div class="account-balance-group">
          <div class="account-balance-label">Current Balance</div>
          <div class="account-balance-amount">${formatCurrency(balance)}</div>
        </div>
      </div>

      <div class="account-actions">
        ${!isDefault ? `<button class="btn btn-secondary btn-sm" onclick="handleSetDefault(${accountId})">★ Set as Default</button>` : ""}
        <button class="btn btn-secondary btn-sm" onclick="openEditAccountModal(${accountId})">Edit</button>
        <button class="btn btn-danger btn-sm" onclick="openDeleteConfirmModal(${accountId}, '${escapeJsString(accountName)}')">Delete</button>
      </div>
    `;

    gridEl.appendChild(card);
  });
}

/**
 * Opens the modal for creating a new account.
 */
function openAddAccountModal() {
  editingAccountId = null;
  document.getElementById("modalTitle").textContent = "Add Financial Account";
  document.getElementById("accountForm").reset();
  document.getElementById("accountBalance").value = "0.00";
  hideModalError();

  const modal = document.getElementById("accountModal");
  if (modal) modal.style.display = "flex";
  document.getElementById("accountName").focus();
}

/**
 * Opens the modal for editing an existing account.
 */
function openEditAccountModal(accountId) {
  const account = accounts.find((a) => (a.account_id ?? a.accountId) === accountId);
  if (!account) return;

  editingAccountId = accountId;
  document.getElementById("modalTitle").textContent = "Edit Financial Account";
  hideModalError();

  document.getElementById("accountName").value = account.account_name ?? account.accountName ?? "";
  document.getElementById("accountType").value = account.account_type ?? account.accountType ?? "Savings";
  document.getElementById("accountBalance").value = account.balance ?? 0;

  const modal = document.getElementById("accountModal");
  if (modal) modal.style.display = "flex";
  document.getElementById("accountName").focus();
}

/**
 * Closes the account add/edit modal.
 */
function closeAccountModal() {
  const modal = document.getElementById("accountModal");
  if (modal) modal.style.display = "none";
  editingAccountId = null;
  hideModalError();
}

/**
 * Handles form submission for adding or updating an account.
 */
async function handleAccountFormSubmit(e) {
  e.preventDefault();
  hideModalError();

  const nameInput = document.getElementById("accountName");
  const typeInput = document.getElementById("accountType");
  const balanceInput = document.getElementById("accountBalance");
  const submitBtn = document.getElementById("saveAccountBtn");

  const accountName = nameInput.value.trim();
  const accountType = typeInput.value.trim();
  const balanceVal = balanceInput.value.trim();

  // Validations
  if (!accountName) {
    showModalError("Account name is required.");
    nameInput.focus();
    return;
  }

  if (!accountType) {
    showModalError("Account type is required.");
    typeInput.focus();
    return;
  }

  const balanceNum = parseFloat(balanceVal);
  if (isNaN(balanceNum)) {
    showModalError("Please enter a valid numeric balance.");
    balanceInput.focus();
    return;
  }

  // Request Payload (CRITICAL: user_id is omitted; backend session assigns ownership)
  const payload = {
    account_name: accountName,
    account_type: accountType,
    balance: balanceNum
  };

  try {
    submitBtn.disabled = true;
    submitBtn.textContent = "Saving...";

    if (editingAccountId === null) {
      // Create: POST /api/accounts
      await apiPost("/api/accounts", payload);
      showToast("Account created successfully!", "success");
    } else {
      // Update: PUT /api/accounts/{id}
      await apiPut(`/api/accounts/${editingAccountId}`, payload);
      showToast("Account updated successfully!", "success");
    }

    closeAccountModal();
    await loadAccounts();
  } catch (error) {
    showModalError(error.message || "Failed to save account. Please try again.");
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = "Save Account";
  }
}

/**
 * Sets an account as default via PUT /api/accounts/{id}/default.
 */
async function handleSetDefault(accountId) {
  try {
    const res = await apiPut(`/api/accounts/${accountId}/default`, {});
    currentDefaultAccountId = accountId;
    showToast(res.message || "Default account updated successfully!", "success");
    await loadAccounts();
  } catch (error) {
    showToast(error.message || "Failed to set default account.", "error");
  }
}

/**
 * Opens delete confirmation modal.
 */
function openDeleteConfirmModal(accountId, accountName) {
  deletingAccountId = accountId;
  const msgEl = document.getElementById("deleteConfirmMessage");
  if (msgEl) {
    msgEl.textContent = `Are you sure you want to delete "${accountName}"?`;
  }
  const modal = document.getElementById("deleteModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Closes delete confirmation modal.
 */
function closeDeleteModal() {
  deletingAccountId = null;
  const modal = document.getElementById("deleteModal");
  if (modal) modal.style.display = "none";
}

/**
 * Executes deletion of the selected account via DELETE /api/accounts/{id}.
 */
async function executeDeleteAccount() {
  if (deletingAccountId === null) return;

  const confirmBtn = document.getElementById("confirmDeleteBtn");

  try {
    confirmBtn.disabled = true;
    confirmBtn.textContent = "Deleting...";

    // Call DELETE /api/accounts/{id} (no user_id sent)
    await apiDelete(`/api/accounts/${deletingAccountId}`);

    showToast("Account deleted successfully.", "success");
    closeDeleteModal();
    await loadAccounts();
  } catch (error) {
    closeDeleteModal();
    showToast(error.message || "Failed to delete account. It may have associated records.", "error");
  } finally {
    confirmBtn.disabled = false;
    confirmBtn.textContent = "Delete Account";
  }
}

/**
 * Helpers for modal errors and toast notifications.
 */
function showModalError(msg) {
  const el = document.getElementById("modalError");
  if (el) {
    el.textContent = msg;
    el.style.display = "flex";
  }
}

function hideModalError() {
  const el = document.getElementById("modalError");
  if (el) {
    el.style.display = "none";
    el.textContent = "";
  }
}

function showToast(msg, type = "success") {
  const toast = document.getElementById("pageToast");
  if (!toast) return;

  toast.textContent = msg;
  toast.className = `alert ${type === "error" ? "alert-error" : "alert-success"}`;
  toast.style.display = "flex";

  setTimeout(() => {
    toast.style.display = "none";
  }, 4000);
}

function escapeHtml(str) {
  if (!str) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function escapeJsString(str) {
  if (!str) return "";
  return String(str)
    .replace(/\\/g, "\\\\")
    .replace(/'/g, "\\'")
    .replace(/"/g, '\\"');
}

/**
 * Attaches DOM event listeners.
 */
function setupEventListeners() {
  const addAccountBtn = document.getElementById("addAccountBtn");
  const emptyAddBtn = document.getElementById("emptyAddBtn");
  const accountForm = document.getElementById("accountForm");
  const closeAccountModalBtn = document.getElementById("closeAccountModalBtn");
  const cancelAccountBtn = document.getElementById("cancelAccountBtn");
  const closeDeleteModalBtn = document.getElementById("closeDeleteModalBtn");
  const cancelDeleteBtn = document.getElementById("cancelDeleteBtn");
  const confirmDeleteBtn = document.getElementById("confirmDeleteBtn");
  const logoutBtn = document.getElementById("logoutBtn");

  if (addAccountBtn) addAccountBtn.addEventListener("click", openAddAccountModal);
  if (emptyAddBtn) emptyAddBtn.addEventListener("click", openAddAccountModal);
  if (accountForm) accountForm.addEventListener("submit", handleAccountFormSubmit);
  if (closeAccountModalBtn) closeAccountModalBtn.addEventListener("click", closeAccountModal);
  if (cancelAccountBtn) cancelAccountBtn.addEventListener("click", closeAccountModal);
  if (closeDeleteModalBtn) closeDeleteModalBtn.addEventListener("click", closeDeleteModal);
  if (cancelDeleteBtn) cancelDeleteBtn.addEventListener("click", closeDeleteModal);
  if (confirmDeleteBtn) confirmDeleteBtn.addEventListener("click", executeDeleteAccount);

  if (logoutBtn) {
    logoutBtn.addEventListener("click", async () => {
      logoutBtn.disabled = true;
      logoutBtn.textContent = "Signing out...";
      await logout();
    });
  }

  // Close modals on backdrop click
  window.addEventListener("click", (e) => {
    const accountModal = document.getElementById("accountModal");
    const deleteModal = document.getElementById("deleteModal");
    if (e.target === accountModal) closeAccountModal();
    if (e.target === deleteModal) closeDeleteModal();
  });
}

// Bootstrap on DOM load
document.addEventListener("DOMContentLoaded", initAccountsPage);
