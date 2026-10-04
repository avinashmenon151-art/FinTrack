package model;

/**
 * Enumeration representing allowed transaction types in FinTrack.
 * Corresponds to database constraint: CHECK (transaction_type IN ('Income', 'Expense'))
 */
public enum TransactionType {
    Income,
    Expense
}
