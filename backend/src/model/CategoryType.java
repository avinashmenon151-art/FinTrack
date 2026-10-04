package model;

/**
 * Enumeration representing allowed category classification types in FinTrack.
 * Corresponds to database constraint: CHECK (category_type IN ('Income', 'Expense'))
 */
public enum CategoryType {
    Income,
    Expense
}
