package util;

import model.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Lightweight JSON utility for FinTrack Servlets.
 *
 * Provides:
 *   1. Clean serialization of FinTrack domain models, collections, and maps to JSON.
 *   2. Simple key-value JSON parsing for request bodies without external third-party dependencies.
 *   3. Helper methods for sending standardized JSON and error HTTP responses.
 */
public class JsonUtil {

    private JsonUtil() {
    }

    /**
     * Reads the entire HTTP request body as a string.
     */
    public static String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Parses a flat/simple JSON object string into a Map of String key to String/Object values.
     * Handles string values, numbers, booleans, and nulls.
     */
    public static Map<String, Object> parseObject(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }

        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }

        if (trimmed.isEmpty()) {
            return map;
        }

        // Tokenize by comma, being careful about quoted strings
        List<String> pairs = splitTopLevelCommas(trimmed);
        for (String pair : pairs) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx != -1) {
                String key = cleanKey(pair.substring(0, colonIdx));
                String rawVal = pair.substring(colonIdx + 1).trim();
                map.put(key, parseRawValue(rawVal));
            }
        }
        return map;
    }

    private static List<String> splitTopLevelCommas(String input) {
        List<String> list = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int depth = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\"' && (i == 0 || input.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (c == '{' || c == '[') {
                    depth++;
                } else if (c == '}' || c == ']') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    list.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }
        if (current.length() > 0) {
            list.add(current.toString().trim());
        }
        return list;
    }

    private static String cleanKey(String rawKey) {
        String k = rawKey.trim();
        if (k.startsWith("\"") && k.endsWith("\"")) {
            k = k.substring(1, k.length() - 1);
        }
        return k;
    }

    private static Object parseRawValue(String rawVal) {
        if (rawVal == null || rawVal.isEmpty() || rawVal.equalsIgnoreCase("null")) {
            return null;
        }
        if (rawVal.startsWith("\"") && rawVal.endsWith("\"")) {
            return unescapeString(rawVal.substring(1, rawVal.length() - 1));
        }
        if (rawVal.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (rawVal.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        // Attempt numeric
        try {
            if (rawVal.contains(".")) {
                return new BigDecimal(rawVal);
            } else {
                return Long.parseLong(rawVal);
            }
        } catch (NumberFormatException e) {
            return rawVal;
        }
    }

    private static String unescapeString(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    // =========================================================================
    // FIELD EXTRACTORS
    // =========================================================================

    public static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : null;
    }

    public static Integer getInteger(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(val.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        try {
            return new BigDecimal(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalDate getLocalDate(Map<String, Object> map, String key) {
        String s = getString(map, key);
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // SERIALIZATION
    // =========================================================================

    /**
     * Serializes any supported FinTrack object or collection to JSON string.
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return escapeJsonString((String) obj);
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Enum) {
            return escapeJsonString(((Enum<?>) obj).name());
        }
        if (obj instanceof LocalDate || obj instanceof OffsetDateTime) {
            return escapeJsonString(obj.toString());
        }
        if (obj instanceof Collection<?>) {
            StringBuilder sb = new StringBuilder("[");
            Iterator<?> it = ((Collection<?>) obj).iterator();
            while (it.hasNext()) {
                sb.append(toJson(it.next()));
                if (it.hasNext()) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Map<?, ?>) {
            StringBuilder sb = new StringBuilder("{");
            Iterator<? extends Map.Entry<?, ?>> it = ((Map<?, ?>) obj).entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> entry = it.next();
                sb.append(escapeJsonString(String.valueOf(entry.getKey())))
                  .append(":")
                  .append(toJson(entry.getValue()));
                if (it.hasNext()) sb.append(",");
            }
            sb.append("}");
            return sb.toString();
        }

        // Domain models
        if (obj instanceof User) {
            User u = (User) obj;
            return "{" +
                    "\"user_id\":" + toJson(u.getUserId()) + "," +
                    "\"userId\":" + toJson(u.getUserId()) + "," +
                    "\"name\":" + toJson(u.getName()) + "," +
                    "\"email\":" + toJson(u.getEmail()) + "," +
                    "\"phone_number\":" + toJson(u.getPhoneNumber()) + "," +
                    "\"phoneNumber\":" + toJson(u.getPhoneNumber()) + "," +
                    "\"default_account_id\":" + toJson(u.getDefaultAccountId()) + "," +
                    "\"defaultAccountId\":" + toJson(u.getDefaultAccountId()) + "," +
                    "\"created_at\":" + toJson(u.getCreatedAt()) + "," +
                    "\"createdAt\":" + toJson(u.getCreatedAt()) +
                    "}";
        }
        if (obj instanceof Account) {
            Account a = (Account) obj;
            return "{" +
                    "\"account_id\":" + toJson(a.getAccountId()) + "," +
                    "\"accountId\":" + toJson(a.getAccountId()) + "," +
                    "\"user_id\":" + toJson(a.getUserId()) + "," +
                    "\"userId\":" + toJson(a.getUserId()) + "," +
                    "\"account_name\":" + toJson(a.getAccountName()) + "," +
                    "\"accountName\":" + toJson(a.getAccountName()) + "," +
                    "\"account_type\":" + toJson(a.getAccountType()) + "," +
                    "\"accountType\":" + toJson(a.getAccountType()) + "," +
                    "\"balance\":" + toJson(a.getBalance()) + "," +
                    "\"created_at\":" + toJson(a.getCreatedAt()) + "," +
                    "\"createdAt\":" + toJson(a.getCreatedAt()) +
                    "}";
        }
        if (obj instanceof Category) {
            Category c = (Category) obj;
            return "{" +
                    "\"category_id\":" + toJson(c.getCategoryId()) + "," +
                    "\"categoryId\":" + toJson(c.getCategoryId()) + "," +
                    "\"user_id\":" + toJson(c.getUserId()) + "," +
                    "\"userId\":" + toJson(c.getUserId()) + "," +
                    "\"category_name\":" + toJson(c.getCategoryName()) + "," +
                    "\"categoryName\":" + toJson(c.getCategoryName()) + "," +
                    "\"category_type\":" + toJson(c.getCategoryType()) + "," +
                    "\"categoryType\":" + toJson(c.getCategoryType()) + "," +
                    "\"description\":" + toJson(c.getDescription()) +
                    "}";
        }
        if (obj instanceof Transaction) {
            Transaction t = (Transaction) obj;
            return "{" +
                    "\"transaction_id\":" + toJson(t.getTransactionId()) + "," +
                    "\"transactionId\":" + toJson(t.getTransactionId()) + "," +
                    "\"user_id\":" + toJson(t.getUserId()) + "," +
                    "\"userId\":" + toJson(t.getUserId()) + "," +
                    "\"account_id\":" + toJson(t.getAccountId()) + "," +
                    "\"accountId\":" + toJson(t.getAccountId()) + "," +
                    "\"category_id\":" + toJson(t.getCategoryId()) + "," +
                    "\"categoryId\":" + toJson(t.getCategoryId()) + "," +
                    "\"amount\":" + toJson(t.getAmount()) + "," +
                    "\"transaction_type\":" + toJson(t.getTransactionType()) + "," +
                    "\"transactionType\":" + toJson(t.getTransactionType()) + "," +
                    "\"transaction_date\":" + toJson(t.getTransactionDate()) + "," +
                    "\"transactionDate\":" + toJson(t.getTransactionDate()) + "," +
                    "\"description\":" + toJson(t.getDescription()) + "," +
                    "\"created_at\":" + toJson(t.getCreatedAt()) + "," +
                    "\"createdAt\":" + toJson(t.getCreatedAt()) +
                    "}";
        }
        if (obj instanceof Budget) {
            Budget b = (Budget) obj;
            return "{" +
                    "\"budgetId\":" + toJson(b.getBudgetId()) + "," +
                    "\"userId\":" + toJson(b.getUserId()) + "," +
                    "\"budgetName\":" + toJson(b.getBudgetName()) + "," +
                    "\"startDate\":" + toJson(b.getStartDate()) + "," +
                    "\"endDate\":" + toJson(b.getEndDate()) + "," +
                    "\"createdAt\":" + toJson(b.getCreatedAt()) +
                    "}";
        }
        if (obj instanceof BudgetCategory) {
            BudgetCategory bc = (BudgetCategory) obj;
            return "{" +
                    "\"budgetId\":" + toJson(bc.getBudgetId()) + "," +
                    "\"categoryId\":" + toJson(bc.getCategoryId()) + "," +
                    "\"allocatedAmount\":" + toJson(bc.getAllocatedAmount()) +
                    "}";
        }
        if (obj instanceof SavingsGoal) {
            SavingsGoal sg = (SavingsGoal) obj;
            return "{" +
                    "\"goalId\":" + toJson(sg.getGoalId()) + "," +
                    "\"userId\":" + toJson(sg.getUserId()) + "," +
                    "\"accountId\":" + toJson(sg.getAccountId()) + "," +
                    "\"goalName\":" + toJson(sg.getGoalName()) + "," +
                    "\"targetAmount\":" + toJson(sg.getTargetAmount()) + "," +
                    "\"savedAmount\":" + toJson(sg.getSavedAmount()) + "," +
                    "\"targetDate\":" + toJson(sg.getTargetDate()) + "," +
                    "\"status\":" + toJson(sg.getStatus()) + "," +
                    "\"createdAt\":" + toJson(sg.getCreatedAt()) +
                    "}";
        }

        return escapeJsonString(obj.toString());
    }

    private static String escapeJsonString(String str) {
        if (str == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    // =========================================================================
    // HTTP RESPONSE HELPERS
    // =========================================================================

    public static void sendJsonResponse(HttpServletResponse resp, int statusCode, Object data) throws IOException {
        resp.setStatus(statusCode);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(toJson(data));
    }

    public static void sendErrorResponse(HttpServletResponse resp, int statusCode, String message) throws IOException {
        Map<String, String> err = new HashMap<>();
        err.put("error", message);
        sendJsonResponse(resp, statusCode, err);
    }
}
