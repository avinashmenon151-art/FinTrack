/**
 * FinTrack – Categories Management Module
 * Connects frontend/categories.html to the authenticated Java Servlet backend.
 * Enforces:
 *   - System categories (user_id IS NULL) are read-only with no Edit/Delete controls.
 *   - Custom categories (user_id = authenticatedUserId) can be added, edited, and deleted.
 *   - user_id is never sent by client; ownership is derived strictly from backend session.
 */

// State
let allCategories = [];
let currentFilter = "ALL"; // 'ALL' | 'INCOME' | 'EXPENSE'
let editingCategoryId = null;
let deletingCategoryId = null;
let currentUser = null;

/**
 * Initializes the Categories page on DOM load.
 * Enforces session authentication via requireAuth().
 */
async function initCategoriesPage() {
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

  // Load categories from backend
  await loadCategories();
}

/**
 * Loads available categories from GET /api/categories.
 * Returns system categories + authenticated user's custom categories.
 */
async function loadCategories() {
  const loadingEl = document.getElementById("loadingIndicator");
  const mainContentEl = document.getElementById("categoriesContent");
  const generalEmptyEl = document.getElementById("generalEmptyState");

  if (loadingEl) loadingEl.style.display = "block";
  if (mainContentEl) mainContentEl.style.display = "none";
  if (generalEmptyEl) generalEmptyEl.style.display = "none";

  try {
    // Call GET /api/categories with credentials included (no user_id sent)
    allCategories = await apiGet("/api/categories");

    if (loadingEl) loadingEl.style.display = "none";

    if (!allCategories || allCategories.length === 0) {
      if (generalEmptyEl) generalEmptyEl.style.display = "block";
      if (mainContentEl) mainContentEl.style.display = "none";
    } else {
      if (generalEmptyEl) generalEmptyEl.style.display = "none";
      if (mainContentEl) {
        mainContentEl.style.display = "block";
        renderCategories();
      }
    }
  } catch (error) {
    if (loadingEl) loadingEl.style.display = "none";
    if (error.status === 401) {
      window.location.href = "index.html";
      return;
    }
    showToast(error.message || "Failed to load categories. Please try again.", "error");
  }
}

/**
 * Renders categories divided into System Categories and My Custom Categories.
 */
function renderCategories() {
  // Apply current type filter ('ALL', 'INCOME', 'EXPENSE')
  const filtered = allCategories.filter((c) => {
    if (currentFilter === "ALL") return true;
    const type = (c.category_type ?? c.categoryType ?? "").toUpperCase();
    return type === currentFilter;
  });

  // Separate into System Categories (user_id IS NULL) and Custom Categories
  const systemCategories = filtered.filter((c) => (c.user_id === null || c.userId === null));
  const customCategories = filtered.filter((c) => (c.user_id !== null && c.userId !== null));

  renderSystemCategories(systemCategories);
  renderCustomCategories(customCategories);
}

/**
 * Renders read-only System Categories cards.
 * System categories NEVER display Edit or Delete buttons.
 */
function renderSystemCategories(categories) {
  const gridEl = document.getElementById("systemCategoriesGrid");
  const countEl = document.getElementById("systemCountBadge");
  const emptyNoticeEl = document.getElementById("systemEmptyNotice");

  if (countEl) countEl.textContent = `${categories.length}`;
  if (!gridEl) return;

  gridEl.innerHTML = "";

  if (categories.length === 0) {
    if (emptyNoticeEl) emptyNoticeEl.style.display = "block";
    gridEl.style.display = "none";
    return;
  }

  if (emptyNoticeEl) emptyNoticeEl.style.display = "none";
  gridEl.style.display = "grid";

  categories.forEach((cat) => {
    const catName = cat.category_name ?? cat.categoryName ?? "Unnamed";
    const catType = cat.category_type ?? cat.categoryType ?? "Expense";
    const desc = cat.description || "Standard shared system category.";
    const isIncome = catType.toUpperCase() === "INCOME";

    const card = document.createElement("div");
    card.className = "category-card";

    card.innerHTML = `
      <div>
        <div class="category-header">
          <div class="category-title-group">
            <h4>${escapeHtml(catName)}</h4>
          </div>
          <div class="category-badges">
            <span class="badge ${isIncome ? "badge-income" : "badge-expense"}">${escapeHtml(catType)}</span>
            <span class="badge badge-system">System Category</span>
          </div>
        </div>

        <p class="category-desc">${escapeHtml(desc)}</p>
      </div>

      <div class="category-readonly-notice">
        🔒 Standard system category (read-only)
      </div>
    `;

    gridEl.appendChild(card);
  });
}

/**
 * Renders user's Custom Categories cards.
 * Custom categories display Edit and Delete controls.
 */
function renderCustomCategories(categories) {
  const gridEl = document.getElementById("customCategoriesGrid");
  const countEl = document.getElementById("customCountBadge");
  const emptyBoxEl = document.getElementById("customEmptyState");

  if (countEl) countEl.textContent = `${categories.length}`;
  if (!gridEl) return;

  gridEl.innerHTML = "";

  if (categories.length === 0) {
    if (emptyBoxEl) emptyBoxEl.style.display = "block";
    gridEl.style.display = "none";
    return;
  }

  if (emptyBoxEl) emptyBoxEl.style.display = "none";
  gridEl.style.display = "grid";

  categories.forEach((cat) => {
    const catId = cat.category_id ?? cat.categoryId;
    const catName = cat.category_name ?? cat.categoryName ?? "Unnamed";
    const catType = cat.category_type ?? cat.categoryType ?? "Expense";
    const desc = cat.description || "No description provided.";
    const isIncome = catType.toUpperCase() === "INCOME";

    const card = document.createElement("div");
    card.className = "category-card";
    card.id = `category-card-${catId}`;

    card.innerHTML = `
      <div>
        <div class="category-header">
          <div class="category-title-group">
            <h4>${escapeHtml(catName)}</h4>
          </div>
          <div class="category-badges">
            <span class="badge ${isIncome ? "badge-income" : "badge-expense"}">${escapeHtml(catType)}</span>
            <span class="badge badge-custom">Custom Category</span>
          </div>
        </div>

        <p class="category-desc">${escapeHtml(desc)}</p>
      </div>

      <div class="account-actions">
        <button class="btn btn-secondary btn-sm" onclick="openEditCategoryModal(${catId})">Edit</button>
        <button class="btn btn-danger btn-sm" onclick="openDeleteConfirmModal(${catId}, '${escapeJsString(catName)}')">Delete</button>
      </div>
    `;

    gridEl.appendChild(card);
  });
}

/**
 * Updates active category type filter ('ALL', 'INCOME', 'EXPENSE').
 */
function setCategoryFilter(filterType) {
  currentFilter = filterType.toUpperCase();

  // Update active pill UI
  document.querySelectorAll(".btn-filter").forEach((btn) => {
    if (btn.dataset.filter === currentFilter) {
      btn.classList.add("active");
    } else {
      btn.classList.remove("active");
    }
  });

  renderCategories();
}

/**
 * Opens modal for adding a new custom category.
 */
function openAddCategoryModal() {
  editingCategoryId = null;
  document.getElementById("modalTitle").textContent = "Add Custom Category";
  document.getElementById("categoryForm").reset();
  document.getElementById("categoryType").value = "Expense";
  hideModalError();

  const modal = document.getElementById("categoryModal");
  if (modal) modal.style.display = "flex";
  document.getElementById("categoryName").focus();
}

/**
 * Opens modal for editing an existing custom category.
 */
function openEditCategoryModal(categoryId) {
  const cat = allCategories.find((c) => (c.category_id ?? c.categoryId) === categoryId);
  if (!cat) return;

  // System category protection check: system categories must NEVER be edited
  if (cat.user_id === null || cat.userId === null) {
    showToast("System categories cannot be edited.", "error");
    return;
  }

  editingCategoryId = categoryId;
  document.getElementById("modalTitle").textContent = "Edit Custom Category";
  hideModalError();

  document.getElementById("categoryName").value = cat.category_name ?? cat.categoryName ?? "";
  document.getElementById("categoryType").value = cat.category_type ?? cat.categoryType ?? "Expense";
  document.getElementById("categoryDescription").value = cat.description ?? "";

  const modal = document.getElementById("categoryModal");
  if (modal) modal.style.display = "flex";
  document.getElementById("categoryName").focus();
}

/**
 * Closes the category add/edit modal.
 */
function closeCategoryModal() {
  const modal = document.getElementById("categoryModal");
  if (modal) modal.style.display = "none";
  editingCategoryId = null;
  hideModalError();
}

/**
 * Handles form submission for adding or updating a custom category.
 */
async function handleCategoryFormSubmit(e) {
  e.preventDefault();
  hideModalError();

  const nameInput = document.getElementById("categoryName");
  const typeInput = document.getElementById("categoryType");
  const descInput = document.getElementById("categoryDescription");
  const submitBtn = document.getElementById("saveCategoryBtn");

  const categoryName = nameInput.value.trim();
  const categoryType = typeInput.value.trim();
  const description = descInput.value.trim();

  // Form validations
  if (!categoryName) {
    showModalError("Category name is required.");
    nameInput.focus();
    return;
  }

  if (!categoryType || (categoryType !== "Income" && categoryType !== "Expense")) {
    showModalError("Please select a valid category type (Income or Expense).");
    typeInput.focus();
    return;
  }

  // Request Payload (CRITICAL: user_id is omitted; backend session assigns ownership)
  const payload = {
    category_name: categoryName,
    category_type: categoryType,
    description: description || null
  };

  try {
    submitBtn.disabled = true;
    submitBtn.textContent = "Saving...";

    if (editingCategoryId === null) {
      // Create: POST /api/categories
      await apiPost("/api/categories", payload);
      showToast("Custom category created successfully!", "success");
    } else {
      // Update: PUT /api/categories/{id}
      await apiPut(`/api/categories/${editingCategoryId}`, payload);
      showToast("Custom category updated successfully!", "success");
    }

    closeCategoryModal();
    await loadCategories();
  } catch (error) {
    showModalError(error.message || "Failed to save category. Please try again.");
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = "Save Category";
  }
}

/**
 * Opens delete confirmation modal for custom category.
 */
function openDeleteConfirmModal(categoryId, categoryName) {
  const cat = allCategories.find((c) => (c.category_id ?? c.categoryId) === categoryId);
  if (cat && (cat.user_id === null || cat.userId === null)) {
    showToast("System categories cannot be deleted.", "error");
    return;
  }

  deletingCategoryId = categoryId;
  const msgEl = document.getElementById("deleteConfirmMessage");
  if (msgEl) {
    msgEl.textContent = `Are you sure you want to delete the custom category "${categoryName}"?`;
  }
  const modal = document.getElementById("deleteModal");
  if (modal) modal.style.display = "flex";
}

/**
 * Closes delete confirmation modal.
 */
function closeDeleteModal() {
  deletingCategoryId = null;
  const modal = document.getElementById("deleteModal");
  if (modal) modal.style.display = "none";
}

/**
 * Executes deletion of the custom category via DELETE /api/categories/{id}.
 */
async function executeDeleteCategory() {
  if (deletingCategoryId === null) return;

  const confirmBtn = document.getElementById("confirmDeleteBtn");

  try {
    confirmBtn.disabled = true;
    confirmBtn.textContent = "Deleting...";

    // Call DELETE /api/categories/{id} (no user_id sent)
    await apiDelete(`/api/categories/${deletingCategoryId}`);

    showToast("Custom category deleted successfully.", "success");
    closeDeleteModal();
    await loadCategories();
  } catch (error) {
    closeDeleteModal();
    // Handle foreign key constraint gracefully
    const msg = error.message && error.message.toLowerCase().includes("foreign")
      ? "This category cannot be deleted because it is currently being used by transactions or budgets."
      : (error.message || "Failed to delete category.");
    showToast(msg, "error");
  } finally {
    confirmBtn.disabled = false;
    confirmBtn.textContent = "Delete Category";
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
  const addCategoryBtn = document.getElementById("addCategoryBtn");
  const emptyAddBtn = document.getElementById("emptyAddBtn");
  const categoryForm = document.getElementById("categoryForm");
  const closeCategoryModalBtn = document.getElementById("closeCategoryModalBtn");
  const cancelCategoryBtn = document.getElementById("cancelCategoryBtn");
  const closeDeleteModalBtn = document.getElementById("closeDeleteModalBtn");
  const cancelDeleteBtn = document.getElementById("cancelDeleteBtn");
  const confirmDeleteBtn = document.getElementById("confirmDeleteBtn");
  const logoutBtn = document.getElementById("logoutBtn");

  if (addCategoryBtn) addCategoryBtn.addEventListener("click", openAddCategoryModal);
  if (emptyAddBtn) emptyAddBtn.addEventListener("click", openAddCategoryModal);
  if (categoryForm) categoryForm.addEventListener("submit", handleCategoryFormSubmit);
  if (closeCategoryModalBtn) closeCategoryModalBtn.addEventListener("click", closeCategoryModal);
  if (cancelCategoryBtn) cancelCategoryBtn.addEventListener("click", closeCategoryModal);
  if (closeDeleteModalBtn) closeDeleteModalBtn.addEventListener("click", closeDeleteModal);
  if (cancelDeleteBtn) cancelDeleteBtn.addEventListener("click", closeDeleteModal);
  if (confirmDeleteBtn) confirmDeleteBtn.addEventListener("click", executeDeleteCategory);

  // Filter button clicks
  document.querySelectorAll(".btn-filter").forEach((btn) => {
    btn.addEventListener("click", () => {
      setCategoryFilter(btn.dataset.filter);
    });
  });

  if (logoutBtn) {
    logoutBtn.addEventListener("click", async () => {
      logoutBtn.disabled = true;
      logoutBtn.textContent = "Signing out...";
      await logout();
    });
  }

  // Close modals on backdrop click
  window.addEventListener("click", (e) => {
    const categoryModal = document.getElementById("categoryModal");
    const deleteModal = document.getElementById("deleteModal");
    if (e.target === categoryModal) closeCategoryModal();
    if (e.target === deleteModal) closeDeleteModal();
  });
}

// Bootstrap on DOM load
document.addEventListener("DOMContentLoaded", initCategoriesPage);
