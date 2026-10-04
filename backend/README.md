# FinTrack – Backend Architecture

## Overview

The backend of **FinTrack** is built using standard Java EE / Jakarta EE Servlet specifications with JDBC for persistence and is designed to run on the Apache Tomcat web server. It implements the Model-View-Controller (MVC) and Data Access Object (DAO) design patterns.

---

## Directory Organization

```text
backend/
├── src/
│   ├── controller/    # Servlet classes handling incoming HTTP requests and routing responses
│   ├── dao/           # Data Access Object interfaces and implementations for database operations
│   ├── model/         # Plain Old Java Objects (POJOs) representing domain entities
│   ├── util/          # Utility classes (DBConnection, PasswordHasher, DateFormatter, etc.)
│   └── filter/        # Servlet filters (AuthenticationFilter, LoggingFilter, CORSFilter)
├── WEB-INF/           # Web application deployment descriptor (web.xml) and configuration
└── README.md          # Backend documentation
```

---

## Architecture Principles

- **Controller Layer (`controller/`)**: Intercepts HTTP requests (GET, POST, PUT, DELETE), parses parameters/JSON payloads, invokes the DAO/Service layer, and forwards or serializes the response.
- **Data Access Layer (`dao/`)**: Encapsulates raw JDBC SQL queries, prepared statements, and result set mapping. No business or routing logic should reside here.
- **Model Layer (`model/`)**: Represents core financial entities (`User`, `Account`, `Transaction`, `Category`, `Budget`, `SavingsGoal`).
- **Utilities (`util/`)**: Reusable helper classes including central JDBC connection management.
- **Filters (`filter/`)**: Pre- and post-processing for requests, enforcing authentication, session validation, and header settings.
