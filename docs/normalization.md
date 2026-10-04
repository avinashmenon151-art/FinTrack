# FinTrack – Database Normalization Analysis

This document provides a formal, comprehensive normalization study for the **FinTrack – Personal Finance Management System** relational database. It analyzes functional dependencies, candidate keys, attribute closures, normal form compliance (1NF through BCNF), and critically evaluates the trade-offs in the design.

---

## 1. Summary of Relations and Attributes

The 7 authoritative relations in FinTrack:

1. **`USER`** (`user_id`, `name`, `email`, `password_hash`, `phone_number`, `default_account_id`, `created_at`)
2. **`ACCOUNT`** (`account_id`, `user_id`, `account_name`, `account_type`, `balance`, `created_at`)
3. **`CATEGORY`** (`category_id`, `user_id`, `category_name`, `category_type`, `description`)
4. **`TRANSACTION`** (`transaction_id`, `user_id`, `account_id`, `category_id`, `amount`, `transaction_type`, `transaction_date`, `description`, `created_at`)
5. **`BUDGET`** (`budget_id`, `user_id`, `budget_name`, `start_date`, `end_date`, `created_at`)
6. **`BUDGET_CATEGORY`** (`budget_id`, `category_id`, `allocated_amount`)
7. **`SAVINGS_GOAL`** (`goal_id`, `user_id`, `account_id`, `goal_name`, `target_amount`, `saved_amount`, `target_date`, `status`, `created_at`)

---

## 2. Relation-by-Relation Normalization Analysis

### 2.1 Relation: `USER`

#### Functional Dependencies (FDs)
- $FD_1$: `user_id` $\rightarrow$ `{name, email, password_hash, phone_number, default_account_id, created_at}`
- $FD_2$: `email` $\rightarrow$ `{user_id, name, password_hash, phone_number, default_account_id, created_at}`

#### Candidate Keys
- $CK_1 = \{\text{user\_id}\}$ (Primary Key)
- $CK_2 = \{\text{email}\}$ (Alternate Unique Key)

#### Attribute Closure
- $\{\text{user\_id}\}^+ = \{\text{user\_id, name, email, password\_hash, phone\_number, default\_account\_id, created\_at}\}$
- $\{\text{email}\}^+ = \{\text{user\_id, name, email, password\_hash, phone\_number, default\_account\_id, created\_at}\}$

#### Normal Form Evaluation
- **1NF**: All attributes contain atomic scalar values; no multi-valued or composite attributes exist. **(Satisfied)**
- **2NF**: All candidate keys consist of single attributes. Therefore, no partial functional dependencies can exist. **(Satisfied)**
- **3NF**: For every non-trivial dependency $X \rightarrow Y$, $X$ (`user_id` or `email`) is a superkey. There are no transitive dependencies of non-prime attributes on a candidate key. **(Satisfied)**
- **BCNF**: For every non-trivial functional dependency, the determinant is a superkey. **(Satisfied - BCNF)**

---

### 2.2 Relation: `ACCOUNT`

#### Functional Dependencies (FDs)
- $FD_1$: `account_id` $\rightarrow$ `{user_id, account_name, account_type, balance, created_at}`

*(Note: While each account belongs to a user, a user may own multiple accounts with identical names or types. Hence, `{user_id, account_name}` is not guaranteed to be an alternate key unless specifically constrained.)*

#### Candidate Keys
- $CK_1 = \{\text{account\_id}\}$ (Primary Key)

#### Attribute Closure
- $\{\text{account\_id}\}^+ = \{\text{account\_id, user\_id, account\_name, account\_type, balance, created\_at}\}$

#### Normal Form Evaluation
- **1NF**: Satisfied. All columns are atomic.
- **2NF**: Satisfied. Single-attribute candidate key precludes partial key dependencies.
- **3NF**: Satisfied. The only determinant is `account_id`, which is a superkey.
- **BCNF**: Satisfied. Determinant is a superkey. **(Satisfied - BCNF)**

---

### 2.3 Relation: `CATEGORY`

#### Functional Dependencies (FDs)
- $FD_1$: `category_id` $\rightarrow$ `{user_id, category_name, category_type, description}`

*(Note: `user_id` is NULL for default system categories and populated for user-specific categories. System-wide and user-specific names may overlap, making `category_id` the sole unique determinant.)*

#### Candidate Keys
- $CK_1 = \{\text{category\_id}\}$ (Primary Key)

#### Attribute Closure
- $\{\text{category\_id}\}^+ = \{\text{category\_id, user\_id, category\_name, category\_type, description}\}$

#### Normal Form Evaluation
- **1NF**: Satisfied. Scalar, atomic attributes.
- **2NF**: Satisfied. Candidate key is a single attribute.
- **3NF**: Satisfied. The determinant `category_id` is a superkey.
- **BCNF**: Satisfied. Determinant is a superkey. **(Satisfied - BCNF)**

---

### 2.4 Relation: `TRANSACTION` (Critical Analysis)

#### Functional Dependencies (FDs)
- $FD_1$: `transaction_id` $\rightarrow$ `{user_id, account_id, category_id, amount, transaction_type, transaction_date, description, created_at}`
- $FD_2$: `account_id` $\rightarrow$ `user_id` *(Derived from the business rule: each account belongs to exactly one user)*

#### Candidate Keys
- $CK_1 = \{\text{transaction\_id}\}$ (Primary Key)

#### Attribute Closure
- $\{\text{transaction\_id}\}^+ = \{\text{transaction\_id, user\_id, account\_id, category\_id, amount, transaction\_type, transaction\_date, description, created\_at}\}$
- $\{\text{account\_id}\}^+ = \{\text{account\_id, user\_id}\}$

#### Normal Form Evaluation
- **1NF**: Satisfied.
- **2NF**: Satisfied. The candidate key $\{\text{transaction\_id}\}$ has degree 1, so no proper subset exists to induce partial dependency.
- **3NF Evaluation — Violation Identified**:
  - By definition, a relation $R$ is in 3NF if for every non-trivial functional dependency $X \rightarrow Y$:
    1. $X$ is a superkey of $R$, OR
    2. $Y$ is a prime attribute (a member of some candidate key).
  - Examining $FD_2: \text{account\_id} \rightarrow \text{user\_id}$:
    - Is `account_id` a superkey of `TRANSACTION`? **No** (an account holds many transactions).
    - Is `user_id` a prime attribute? **No** (the only candidate key is $\{\text{transaction\_id}\}$, so `user_id` is non-prime).
  - Therefore, a **transitive dependency** exists:
    $$\text{transaction\_id} \xrightarrow{FD_1} \text{account\_id} \xrightarrow{FD_2} \text{user\_id}$$
  - **Result**: `TRANSACTION` is in **2NF**, but **strictly violates 3NF** and therefore violates **BCNF**.

#### Discussion & Justification of Design Decision

1. **Theoretical 3NF Resolution**:
   - In strict 3NF decomposition, `user_id` would be removed from `TRANSACTION`:
     $$\text{TRANSACTION\_3NF}(\underline{\text{transaction\_id}}, \text{account\_id}, \text{category\_id}, \text{amount}, \text{transaction\_type}, \text{transaction\_date}, \text{description}, \text{created\_at})$$
   - To find the owner of a transaction, an application must join `TRANSACTION` with `ACCOUNT`:
     ```sql
     SELECT t.* FROM transactions t
     JOIN accounts a ON t.account_id = a.account_id
     WHERE a.user_id = ?;
     ```

2. **Why FinTrack Retains `user_id` in `TRANSACTION` (Controlled Denormalization)**:
   - **Multi-Tenant Security & Tenant Isolation**: In a personal finance management system, user data must never leak across account boundaries (Business Rule 20). Every authorization check and API query begins with `user_id`. Retaining `user_id` enables direct filtering `WHERE user_id = ?` on transactions without joining.
   - **Performance at Scale**: Querying dashboard summaries, date ranges, and transaction histories is the most frequent operation in the system. Storing `user_id` allows high-performance composite indexes such as `(user_id, transaction_date DESC)` and `(user_id, category_id)`.
   - **Data Integrity Assurance**: To eliminate the risk of update anomalies where a transaction's `user_id` might disagree with its account's `user_id`, PostgreSQL can enforce a composite foreign key:
     ```sql
     -- Enforces that the account actually belongs to the specified user
     FOREIGN KEY (account_id, user_id) REFERENCES accounts(account_id, user_id)
     ```
   - **Conclusion**: Retaining `user_id` is a deliberate, documented engineering trade-off (2NF with controlled redundancy) rather than an oversight.

---

### 2.5 Relation: `BUDGET`

#### Functional Dependencies (FDs)
- $FD_1$: `budget_id` $\rightarrow$ `{user_id, budget_name, start_date, end_date, created_at}`

#### Candidate Keys
- $CK_1 = \{\text{budget\_id}\}$ (Primary Key)

#### Attribute Closure
- $\{\text{budget\_id}\}^+ = \{\text{budget\_id, user\_id, budget\_name, start\_date, end\_date, created\_at}\}$

#### Normal Form Evaluation
- **1NF**: Satisfied.
- **2NF**: Satisfied. Candidate key is a single attribute.
- **3NF**: Satisfied. Determinant is a superkey.
- **BCNF**: Satisfied. Determinant is a superkey. **(Satisfied - BCNF)**

---

### 2.6 Relation: `BUDGET_CATEGORY` (Composite Key Analysis)

#### Functional Dependencies (FDs)
- $FD_1$: `(budget_id, category_id)` $\rightarrow$ `allocated_amount`

#### Candidate Keys
- $CK_1 = \{(\text{budget\_id}, \text{category\_id})\}$ (Composite Primary Key)

#### Attribute Closure
- $\{(\text{budget\_id}, \text{category\_id})\}^+ = \{\text{budget\_id, category\_id, allocated\_amount}\}$
- $\{\text{budget\_id}\}^+ = \{\text{budget\_id}\}$
- $\{\text{category\_id}\}^+ = \{\text{category\_id}\}$

#### Why `BUDGET_CATEGORY` Requires a Composite Key
1. **$M:N$ Relationship Decomposition**: Business Rules 9 & 10 establish that a budget can cover multiple categories, and a category can appear across multiple budgets. In relational algebra, a Many-to-Many relationship cannot be represented inside either parent entity without creating repeating groups (violating 1NF) or creating insertion/deletion anomalies.
2. **Relationship Attribute**: The `allocated_amount` is an attribute of the *association* between a specific budget and a specific category.
   - `budget_id` alone cannot determine `allocated_amount` because a budget allocates different amounts to different categories.
   - `category_id` alone cannot determine `allocated_amount` because the same category (e.g., "Groceries") has different allocations in different budgets (e.g., $500 in October, $650 in November).
3. **Minimality**: Neither `budget_id` nor `category_id` can be dropped from the determinant without losing the uniqueness of the tuple. Thus, the composite key $\{(\text{budget\_id}, \text{category\_id})\}$ is the minimal candidate key.

#### Normal Form Evaluation
- **1NF**: Satisfied.
- **2NF**: Satisfied. The only non-prime attribute is `allocated_amount`. It depends on the full composite key $\{(\text{budget\_id}, \text{category\_id})\}$, not on any proper subset (`budget_id` or `category_id` alone). Hence, there are no partial dependencies.
- **3NF**: Satisfied. The determinant $\{(\text{budget\_id}, \text{category\_id})\}$ is a superkey.
- **BCNF**: Satisfied. The only non-trivial FD has a superkey determinant. **(Satisfied - BCNF)**

---

### 2.7 Relation: `SAVINGS_GOAL`

#### Functional Dependencies (FDs)
- $FD_1$: `goal_id` $\rightarrow$ `{user_id, account_id, goal_name, target_amount, saved_amount, target_date, status, created_at}`

*(Note: `account_id` is an optional foreign key that can be NULL. An account can be linked to multiple savings goals, so `account_id` does not uniquely identify a savings goal.)*

#### Candidate Keys
- $CK_1 = \{\text{goal\_id}\}$ (Primary Key)

#### Attribute Closure
- $\{\text{goal\_id}\}^+ = \{\text{goal\_id, user\_id, account\_id, goal\_name, target\_amount, saved\_amount, target\_date, status, created\_at}\}$

#### Normal Form Evaluation
- **1NF**: Satisfied.
- **2NF**: Satisfied. Single-attribute candidate key.
- **3NF**: Satisfied. The determinant `goal_id` is a superkey. *(Note: Similar to `TRANSACTION`, if an account is linked, `account_id` transitively points to `user_id`. However, because `account_id` is optional and nullable, `user_id` represents direct ownership of the goal independent of any account).*
- **BCNF**: Satisfied. Determinant is a superkey. **(Satisfied - BCNF)**

---

## 3. Analysis of Circular Foreign Key Dependency (`USER` ↔ `ACCOUNT`)

### 3.1 The Circular Dependency Cycle
- **Rule 1**: `USER.default_account_id` references `ACCOUNT.account_id` ($0..1$ default account per user).
- **Rule 2**: `ACCOUNT.user_id` references `USER.user_id` ($1$ owner per account).

```text
[ USER ] ──(default_account_id FK)──> [ ACCOUNT ]
   ▲                                        │
   └────────────────(user_id FK)────────────┘
```

### 3.2 The Insertion & Deletion Challenge
1. **Insertion Deadlock**: To insert a new `USER` who must have a default account, the `ACCOUNT` must already exist. But to insert that `ACCOUNT`, the `USER` must already exist.
2. **Deletion Cascade Risk**: If both foreign keys were mandatory (`NOT NULL`) with cascade rules, deleting either record could lead to circular cascading locks or constraint violations.

### 3.3 Evaluation of Solutions

| Approach | Mechanics | Advantages | Disadvantages | FinTrack Decision |
| :--- | :--- | :--- | :--- | :--- |
| **Option A: Nullable FK in `USER` (Selected)** | `USER.default_account_id` is `NULLable` with `ON DELETE SET NULL`. | Simple, idiomatic PostgreSQL; breaks the insertion cycle naturally during registration. | Requires application logic or trigger to ensure the referenced account belongs to that user. | **Adopted** |
| **Option B: DEFERRABLE Constraints** | `DEFERRABLE INITIALLY DEFERRED` foreign key constraints checked at transaction commit. | Allows mutually dependent non-null inserts within a single SQL transaction. | More complex transaction handling in JDBC; confusing error traces. | Rejected |
| **Option C: Boolean Flag in `ACCOUNT`** | Move default status to `ACCOUNT.is_default BOOLEAN` with a partial unique index. | Completely eliminates circular FK. | Changes the authoritative relational schema specified for the project. | Analyzed below |

### 3.4 Adopted Solution Protocol
1. `USER.default_account_id` is defined as `INT NULL REFERENCES accounts(account_id) ON DELETE SET NULL`.
2. Upon user registration: Insert `USER` with `default_account_id = NULL`.
3. Upon primary account creation: Insert `ACCOUNT` referencing `user_id`.
4. Update `USER` setting `default_account_id = newly_created_account_id`.

---

## 4. Normalization Summary Table

| Relation | Highest Normal Form | Key Determinant(s) | Normalization Status & Remarks |
| :--- | :--- | :--- | :--- |
| **`USER`** | **BCNF** | `user_id`, `email` | Fully normalized; 2 candidate keys. |
| **`ACCOUNT`** | **BCNF** | `account_id` | Fully normalized. |
| **`CATEGORY`** | **BCNF** | `category_id` | Fully normalized; supports NULL `user_id`. |
| **`TRANSACTION`** | **2NF** (Violates 3NF) | `transaction_id` | **Controlled Denormalization**: Transitive dependency `transaction_id → account_id → user_id` retained for security isolation & index performance. |
| **`BUDGET`** | **BCNF** | `budget_id` | Fully normalized. |
| **`BUDGET_CATEGORY`** | **BCNF** | `(budget_id, category_id)` | Fully normalized composite key decomposing $M:N$ relationship. |
| **`SAVINGS_GOAL`** | **BCNF** | `goal_id` | Fully normalized; optional `account_id`. |
