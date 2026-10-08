package cli;

import java.io.Console;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Scanner;

/**
 * Standard console input/output utility for FinTrack CLI.
 * Provides safe prompting, type conversions, error recovery,
 * secure password input, and clean formatting.
 */
public class ConsoleUtil {

    private static final Scanner SCANNER = new Scanner(System.in);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    static {
        CURRENCY_FORMAT.setMinimumFractionDigits(2);
        CURRENCY_FORMAT.setMaximumFractionDigits(2);
    }

    private ConsoleUtil() {
    }

    // =========================================================================
    // VISUAL SECTIONS & HEADERS
    // =========================================================================

    public static void printHeader(String title) {
        System.out.println();
        System.out.println("================================================================================");
        System.out.println("  " + title.toUpperCase());
        System.out.println("================================================================================");
    }

    public static void printSubHeader(String title) {
        System.out.println();
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("  " + title);
        System.out.println("--------------------------------------------------------------------------------");
    }

    public static void printDivider() {
        System.out.println("--------------------------------------------------------------------------------");
    }

    public static void printSuccess(String message) {
        System.out.println("[SUCCESS] " + message);
    }

    public static void printError(String message) {
        System.out.println("[ERROR] " + message);
    }

    public static void printWarning(String message) {
        System.out.println("[WARNING] " + message);
    }

    public static void printInfo(String message) {
        System.out.println("[INFO] " + message);
    }

    public static void pause() {
        System.out.print("\nPress [Enter] to continue...");
        try {
            SCANNER.nextLine();
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // INPUT PROMPTS
    // =========================================================================

    public static String promptString(String label) {
        while (true) {
            System.out.print(label + ": ");
            String input = SCANNER.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            printError("Input cannot be empty. Please try again.");
        }
    }

    public static String promptOptionalString(String label, String defaultValue) {
        String prompt = (defaultValue != null && !defaultValue.isEmpty())
                ? label + " [" + defaultValue + "]: "
                : label + " (optional): ";
        System.out.print(prompt);
        String input = SCANNER.nextLine().trim();
        if (input.isEmpty()) {
            return defaultValue;
        }
        return input;
    }

    public static int promptInt(String label, int min, int max) {
        while (true) {
            System.out.print(label + ": ");
            String input = SCANNER.nextLine().trim();
            try {
                int val = Integer.parseInt(input);
                if (val >= min && val <= max) {
                    return val;
                }
                printError("Please enter a number between " + min + " and " + max + ".");
            } catch (NumberFormatException e) {
                printError("Invalid number format. Please enter an integer.");
            }
        }
    }

    public static Integer promptOptionalInt(String label, Integer defaultValue) {
        String prompt = (defaultValue != null)
                ? label + " [" + defaultValue + "]: "
                : label + " (optional, press Enter to skip): ";
        while (true) {
            System.out.print(prompt);
            String input = SCANNER.nextLine().trim();
            if (input.isEmpty()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                printError("Invalid number. Please enter an integer or press Enter to skip.");
            }
        }
    }

    public static BigDecimal promptBigDecimal(String label, boolean allowZero, boolean positiveOnly) {
        while (true) {
            System.out.print(label + ": ");
            String input = SCANNER.nextLine().trim();
            try {
                // Strip currency symbol if user typed it
                input = input.replace("₹", "").replace(",", "").trim();
                BigDecimal val = new BigDecimal(input).setScale(2, RoundingMode.HALF_UP);

                if (positiveOnly) {
                    if (allowZero && val.compareTo(BigDecimal.ZERO) < 0) {
                        printError("Amount cannot be negative.");
                        continue;
                    }
                    if (!allowZero && val.compareTo(BigDecimal.ZERO) <= 0) {
                        printError("Amount must be strictly greater than zero.");
                        continue;
                    }
                }
                return val;
            } catch (NumberFormatException e) {
                printError("Invalid amount format. Example: 1500.00");
            }
        }
    }

    public static BigDecimal promptOptionalBigDecimal(String label, BigDecimal defaultValue) {
        String prompt = (defaultValue != null)
                ? label + " [" + formatCurrency(defaultValue) + "]: "
                : label + " (optional, press Enter to keep): ";
        while (true) {
            System.out.print(prompt);
            String input = SCANNER.nextLine().trim();
            if (input.isEmpty()) {
                return defaultValue;
            }
            try {
                input = input.replace("₹", "").replace(",", "").trim();
                BigDecimal val = new BigDecimal(input).setScale(2, RoundingMode.HALF_UP);
                if (val.compareTo(BigDecimal.ZERO) <= 0) {
                    printError("Amount must be greater than zero.");
                    continue;
                }
                return val;
            } catch (NumberFormatException e) {
                printError("Invalid amount. Please enter a valid number or press Enter.");
            }
        }
    }

    public static LocalDate promptLocalDate(String label, boolean optional, LocalDate defaultVal) {
        String prompt = (defaultVal != null)
                ? label + " (YYYY-MM-DD) [" + defaultVal.format(DATE_FORMATTER) + "]: "
                : (optional ? label + " (YYYY-MM-DD, optional): " : label + " (YYYY-MM-DD): ");

        while (true) {
            System.out.print(prompt);
            String input = SCANNER.nextLine().trim();
            if (input.isEmpty()) {
                if (defaultVal != null) return defaultVal;
                if (optional) return null;
                printError("Date is required. Format: YYYY-MM-DD");
                continue;
            }
            try {
                return LocalDate.parse(input, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                printError("Invalid date format. Expected YYYY-MM-DD (e.g. 2026-10-08).");
            }
        }
    }

    /**
     * Reads password securely using Console.readPassword() when available.
     * Falls back to Scanner.nextLine() when running in environments without Console.
     * Never echoes or prints passwords.
     */
    public static String promptPassword(String label) {
        System.out.print(label + ": ");
        Console console = System.console();
        if (console != null) {
            char[] chars = console.readPassword();
            if (chars == null) {
                return "";
            }
            return new String(chars);
        } else {
            // Fallback for IDEs / pipes
            if (SCANNER.hasNextLine()) {
                return SCANNER.nextLine().trim();
            }
            return "";
        }
    }

    public static boolean promptConfirm(String prompt) {
        System.out.print(prompt + " (y/N): ");
        String input = SCANNER.nextLine().trim().toLowerCase();
        return "y".equals(input) || "yes".equals(input);
    }

    // =========================================================================
    // FORMATTING HELPERS
    // =========================================================================

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) return "₹ 0.00";
        return "₹ " + CURRENCY_FORMAT.format(amount);
    }

    public static String formatDate(LocalDate date) {
        if (date == null) return "—";
        return date.format(DATE_FORMATTER);
    }

    public static String padRight(String s, int n) {
        if (s == null) s = "";
        return String.format("%-" + n + "s", s);
    }

    public static String padLeft(String s, int n) {
        if (s == null) s = "";
        return String.format("%" + n + "s", s);
    }
}
