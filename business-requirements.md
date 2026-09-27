# Business Requirements Document

## Project: Shared Expense / Bill Splitter App

### 1. Overview

This document defines the functional and non-functional requirements for a mobile application that allows roommates or shared households to log shared expenses, automatically calculate balances, and settle debts among group members.

**Target platform:** Mobile (React Native — iOS and Android)
**Target users:** Roommates, shared households, small friend groups

---

### 2. Functional Requirements (FR)

#### 2.1 User & Group Management

| ID | Requirement |
|----|-------------|
| FR1 | The system shall allow a user to register and log in using email and password. |
| FR2 | The system shall allow a user to create a group (e.g., "Apartment 4B") and invite members. |
| FR3 | The system shall allow a user to join an existing group via an invite code or link. |
| FR4 | The system shall allow a user to view and manage members of a group (add or remove members). |

#### 2.2 Expense Management

| ID | Requirement |
|----|-------------|
| FR5 | The system shall allow a user to add an expense with amount, description, category, date, payer, and participants. |
| FR6 | The system shall allow a user to choose a split method: equal split, custom amounts, or percentage split. |
| FR7 | The system shall allow a user to edit or delete an expense they created. |
| FR8 | The system shall allow a user to view a history of all expenses in a group, filterable by date, member, or category. |

#### 2.3 Balance & Settlement

| ID | Requirement |
|----|-------------|
| FR9 | The system shall automatically calculate each member's balance (who owes, who is owed, and how much). |
| FR10 | The system shall display a summary screen showing net balances per member. |
| FR11 | The system shall allow a user to record a "settle up" payment, marking a debt as paid. |
| FR12 | The system shall update all affected balances immediately after any expense or settlement change. |

#### 2.4 Other

| ID | Requirement |
|----|-------------|
| FR13 | The system shall allow a user to edit their profile (name, avatar). |
| FR14 | The system shall allow a user to leave a group. |
| FR15 | The system shall allow the app to function offline, caching data locally and syncing once the device reconnects. |

---

### 3. Non-Functional Requirements (NFR)

| ID | Category | Requirement |
|----|----------|-------------|
| NFR1 | Performance | API responses shall complete within approximately 2 seconds under normal load. |
| NFR2 | Performance | Balance recalculation shall occur near-instantly on-device after any change. |
| NFR3 | Usability | Adding an expense shall require no more than 3-4 user inputs/taps. |
| NFR4 | Usability | The app shall achieve a target System Usability Scale (SUS) score of 70 or higher during evaluation. |
| NFR5 | Reliability | The app shall be offline-first: users can add and view expenses without a network connection. |
| NFR6 | Security | User passwords shall be stored using a secure hashing algorithm (e.g., bcrypt). |
| NFR7 | Security | The system shall use JWT-based authentication for all protected API routes. |
| NFR8 | Security | All client-server communication shall occur over HTTPS. |
| NFR9 | Scalability | The backend shall support multiple groups per user without noticeable performance degradation. |
| NFR10 | Maintainability | The backend API shall follow a layered architecture (routes, controllers, services, models) for clear separation of concerns. |
| NFR11 | Portability | The mobile application shall run on both iOS and Android from a single React Native codebase. |
| NFR12 | Data Consistency | Calculated balances shall always match the sum of underlying expense and settlement records, with no drift. |
| NFR13 | Availability | The backend shall remain available and stable throughout the demo/testing period. |

---

### 4. Traceability Note

Functional requirements FR5–FR12 map directly to the core evaluation criteria for this project: correctness of balance calculations (testable via unit tests on the backend service layer) and usability of the expense-logging and settlement flows (testable via task-based usability sessions with real users, e.g. classmates or roommates).
