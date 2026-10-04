# FinTrack – Command-Line Interface (CLI)

## Overview

The **FinTrack CLI** is a standalone Java application providing an alternative console-based interface for managing personal finances. It allows direct interaction with the PostgreSQL database via JDBC for rapid transaction entry, administrative utilities, offline batch tasks, and report generation.

---

## Directory Organization

```text
cli-app/
├── src/            # Java source files for CLI menu, commands, and console views
├── output/         # Destination folder for generated financial statements, exports, and text summaries
└── README.md       # CLI application documentation and usage instructions
```

---

## Capabilities (Planned)

- **Interactive Console Menu**: Text-driven navigation for accounts, transactions, and budgets.
- **Quick Logging**: Rapid entry mode for expenses and income.
- **Summary Reports**: Text-based budget alerts, expense breakdowns, and monthly recaps.
- **Data Export**: Export statements and summaries directly to the `output/` directory in CSV/TXT formats.
