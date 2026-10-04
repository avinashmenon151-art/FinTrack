# FinTrack – Database Normalization

## Overview
This document outlines the normalization steps (1NF, 2NF, 3NF, BCNF) applied to the FinTrack relational schema to eliminate redundancy, avoid update anomalies, and maintain referential integrity.

> **Status:** Pending Phase 2 (Database Modeling & Schema Implementation).

---

## Normalization Goals

1. **First Normal Form (1NF)**: Atomic values, unique column names, and identifiable primary keys.
2. **Second Normal Form (2NF)**: Full functional dependency on candidate keys (no partial dependencies).
3. **Third Normal Form (3NF)**: No transitive functional dependencies on non-prime attributes.
