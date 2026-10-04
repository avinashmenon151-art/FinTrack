package model;

/**
 * Enumeration representing allowed savings goal lifecycle states in FinTrack.
 * Corresponds to database constraint: CHECK (status IN ('Active', 'Completed', 'Cancelled'))
 */
public enum SavingsGoalStatus {
    Active,
    Completed,
    Cancelled
}
