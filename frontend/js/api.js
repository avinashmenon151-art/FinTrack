/**
 * FinTrack API Client
 * Centralized HTTP client managing requests to the Java Servlet backend.
 * Uses standard fetch with credentials: "include" for session cookie management.
 */

const API_BASE_URL = window.FINTRACK_API_BASE_URL || "http://localhost:8080/fintrack";

/**
 * Custom error class representing an API error.
 */
class ApiError extends Error {
  constructor(status, message, data = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.data = data;
  }
}

/**
 * Core API request method.
 *
 * @param {string} endpoint - Path such as "/api/auth/login"
 * @param {object} options - Fetch options (method, headers, body, etc.)
 * @returns {Promise<any>} - Resolves with parsed JSON response
 */
async function apiRequest(endpoint, options = {}) {
  // Ensure endpoint starts with '/'
  const cleanEndpoint = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
  const url = `${API_BASE_URL}${cleanEndpoint}`;

  const headers = {
    Accept: "application/json",
    ...(options.headers || {})
  };

  const config = {
    ...options,
    credentials: "include", // CRITICAL: Always include HTTP session cookies
    headers
  };

  // If request has a body and isn't already a string/FormData, JSON serialize it
  if (config.body && typeof config.body === "object" && !(config.body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
    config.body = JSON.stringify(config.body);
  }

  let response;
  try {
    response = await fetch(url, config);
  } catch (networkError) {
    // Network connectivity, CORS refusal, or server offline
    throw new ApiError(0, "Unable to connect to the FinTrack server. Please verify the backend is running.", null);
  }

  // Parse JSON response body if present
  let responseData = null;
  const contentType = response.headers.get("content-type");
  if (contentType && contentType.includes("application/json")) {
    try {
      responseData = await response.json();
    } catch (e) {
      responseData = null;
    }
  } else {
    try {
      const text = await response.text();
      if (text) {
        responseData = { message: text };
      }
    } catch (e) {
      responseData = null;
    }
  }

  // Check HTTP status code
  if (!response.ok) {
    let errorMessage = "An unexpected error occurred.";
    if (responseData) {
      if (typeof responseData.error === "string") {
        errorMessage = responseData.error;
      } else if (typeof responseData.message === "string") {
        errorMessage = responseData.message;
      }
    } else {
      switch (response.status) {
        case 400:
          errorMessage = "Bad request. Please verify your input.";
          break;
        case 401:
          errorMessage = "Invalid email or password.";
          break;
        case 403:
          errorMessage = "Access denied.";
          break;
        case 404:
          errorMessage = "Resource not found.";
          break;
        case 409:
          errorMessage = "An account with this email already exists.";
          break;
        case 500:
          errorMessage = "Something went wrong on the server. Please try again.";
          break;
        default:
          errorMessage = `HTTP error ${response.status}`;
          break;
      }
    }

    throw new ApiError(response.status, errorMessage, responseData);
  }

  return responseData;
}

/**
 * Convenience GET method.
 */
function apiGet(endpoint) {
  return apiRequest(endpoint, { method: "GET" });
}

/**
 * Convenience POST method.
 */
function apiPost(endpoint, data) {
  return apiRequest(endpoint, {
    method: "POST",
    body: data
  });
}

/**
 * Convenience PUT method.
 */
function apiPut(endpoint, data) {
  return apiRequest(endpoint, {
    method: "PUT",
    body: data
  });
}

/**
 * Convenience DELETE method.
 */
function apiDelete(endpoint) {
  return apiRequest(endpoint, { method: "DELETE" });
}
