/**
 * FinTrack Authentication Module
 * Manages user registration, login, logout, and session lifecycle guards.
 * Relies exclusively on HTTP session cookies via the backend.
 */

/**
 * Retrieves the currently authenticated user from the backend session.
 * 
 * @returns {Promise<object|null>} - Authenticated user object or null if unauthenticated.
 */
async function getCurrentUser() {
  try {
    const user = await apiGet("/api/auth/me");
    return user;
  } catch (error) {
    // 401 Unauthorized or network issue: session is not authenticated
    return null;
  }
}

/**
 * Authenticates a user with email and password.
 * 
 * @param {string} email 
 * @param {string} password 
 * @returns {Promise<object>} - Authenticated user representation
 */
async function login(email, password) {
  const credentials = {
    email: email ? email.trim() : "",
    password: password || ""
  };
  return await apiPost("/api/auth/login", credentials);
}

/**
 * Registers a new user.
 * 
 * @param {object} userData - { name, email, password, phone_number }
 * @returns {Promise<object>} - Created user representation
 */
async function register(userData) {
  const payload = {
    name: userData.name ? userData.name.trim() : "",
    email: userData.email ? userData.email.trim() : "",
    password: userData.password || "",
    phone_number: userData.phone_number ? userData.phone_number.trim() : ""
  };
  return await apiPost("/api/auth/register", payload);
}

/**
 * Logs out the current user by invalidating the server session and redirecting.
 */
async function logout() {
  try {
    await apiPost("/api/auth/logout", {});
  } catch (ignored) {
    // Even if session is already expired or invalid, proceed to login page
  }
  window.location.href = "index.html";
}

/**
 * Route Guard for protected pages (e.g. dashboard.html).
 * Verifies active session with backend; redirects to index.html if unauthenticated.
 * 
 * @returns {Promise<object|null>} - Authenticated user if verified
 */
async function requireAuth() {
  const user = await getCurrentUser();
  if (!user) {
    window.location.href = "index.html";
    return null;
  }
  return user;
}

/**
 * Route Guard for public pages (e.g. index.html, register.html).
 * If user is already authenticated, redirects them straight to dashboard.html.
 */
async function redirectIfAuthenticated() {
  const user = await getCurrentUser();
  if (user) {
    window.location.href = "dashboard.html";
  }
}
