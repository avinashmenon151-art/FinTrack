# FinTrack – Personal Finance Management System

A robust, full-stack personal finance management application designed to help users track expenses, manage budgets, monitor income streams, and achieve personal savings goals.

---

## Project Overview

**FinTrack** is designed as a modular, three-tier enterprise-style personal finance system. It provides both an intuitive web-based interface and a standalone Java command-line interface (CLI) to give users flexible control over their financial health. The platform emphasizes clean architecture, relational integrity, and standard design patterns suited for an academic full-stack Java engineering project.

---

## Technology Stack

| Layer | Technology / Tools |
| :--- | :--- |
| **Backend** | Java Servlets, Java Database Connectivity (JDBC), Apache Tomcat |
| **Database** | PostgreSQL |
| **Frontend** | HTML5, CSS3, JavaScript (ES6+) |
| **CLI Application** | Java (Core CLI runtime) |
| **Version Control** | Git & GitHub |

---

## Planned Modules

1. **User Authentication & Authorization**
   - User registration, secure login, session management, and access filters.
2. **Account Management**
   - Management of multiple financial accounts (Savings, Checking, Credit, Cash).
3. **Transaction Tracking**
   - Income and expense recording, categorization, date filtering, and transaction logs.
4. **Category Management**
   - Custom and default income/expense categorization.
5. **Budget Planning**
   - Monthly and periodic budget allocation per category with progress tracking.
6. **Savings Goals**
   - Target savings tracking with target dates, milestones, and status updates.
7. **Reporting & Analytics**
   - Financial summaries, expense distribution, and exportable reports.
8. **Command-Line Interface (CLI)**
   - Standalone console utility for offline transaction logging, batch imports, and report generation.

---

## Project Structure

```text
FinTrack/
├── backend/            # Java Servlets, DAOs, Models, and Tomcat web app configuration
├── database/           # PostgreSQL schema definitions, migration scripts, and seed data
├── frontend/           # Web client interfaces (HTML, CSS, JavaScript, static assets)
├── cli-app/            # Standalone Java CLI application and reporting tools
├── docs/               # System documentation, ER diagrams, schema specifications, and API docs
├── .gitignore          # Version control ignore rules
└── README.md           # Project documentation and setup guide
```

---

## Current Project Status

- [x] **Phase 1: Project Initialization & Directory Structure** (Current)
  - Core directory hierarchy established.
  - Baseline documentation and architecture guidelines configured.
- [ ] **Phase 2: Database Modeling & Schema Implementation**
  - Entity-Relationship diagram design.
  - Relational schema normalization (3NF).
  - PostgreSQL DDL table definitions and seed scripts.
- [ ] **Phase 3: Backend Architecture & JDBC Setup**
  - Database connection pool / utility implementation.
  - Model definitions and DAO layer implementation.
  - Controller servlets and session filters.
- [ ] **Phase 4: Frontend Development**
  - Responsive UI templates and styling.
  - Client-side validation and API integration.
- [ ] **Phase 5: CLI Utility Development**
  - Command-line interactive menu and batch processing features.
- [ ] **Phase 6: Testing, Refinement & Deployment**
  - Integration testing and deployment on Apache Tomcat.
