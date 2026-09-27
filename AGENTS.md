# AI Agent Context & Development Guide — Bill Splitter App

Welcome, AI Agent. This document serves as your primary entry point to understand the project, its goals, and the expected implementation path. It synthesizes the business requirements and technical architecture into an actionable framework.

## 📌 Project Overview
The **Bill Splitter App** is a mobile application designed for roommates and shared households to log expenses, automatically calculate debts (who owes whom), and record settlements.

**Core Value Proposition:** Eliminate the manual effort of calculating shared expenses and ensure data consistency in debt tracking.

## 🗺️ Key Reference Documents
Before making significant architectural decisions, always refer to:
- `architecture.md`: Detailed tech stack, folder structures, database schema, and API definitions.
- `business-requirements.md`: Functional (FR) and Non-Functional Requirements (NFR).

---

## 🛠️ Tech Stack Summary
- **Frontend:** React Native (TypeScript)
- **Backend:** Node.js + Express
- **Database:** SQLite (Server-side via ORM; Client-side via `react-native-sqlite-storage` or `WatermelonDB` for offline cache)
- **Auth:** JWT (JSON Web Tokens)
- **API:** REST / JSON

---

## 🚀 Implementation Roadmap
The project should be built in the following phases to ensure a stable foundation:

### Phase 1: Backend Foundation
- [ ] Setup Node.js/Express environment.
- [ ] Implement SQLite schema via ORM (Users, Groups, Expenses, etc.).
- [ ] Implement Authentication (Register/Login with bcrypt and JWT).
- [ ] Create basic CRUD endpoints for Groups and Users.

### Phase 2: Core Business Logic (The "Heart" of the App)
- [ ] Implement `balance.service.js` to handle complex split calculations (Equal, Custom, Percentage).
- [ ] Implement the balance calculation algorithm: `Net Balance = (Paid as Payer) - (Owed as Participant) + (Settlements)`.
- [ ] **Critical:** Write comprehensive unit tests for `balance.service.js` to ensure zero drift (NFR12).

### Phase 3: Backend API Completion
- [ ] Implement Expense management endpoints (Add, Edit, Delete).
- [ ] Implement Settlement recording endpoints.
- [ ] Implement Group member management and invite code logic.

### Phase 4: Mobile App Skeleton
- [ ] Setup React Native project structure.
- [ ] Implement API Client (Axios/Fetch wrapper with JWT injection).
- [ ] Setup Navigation (AppNavigator) and basic Routing.
- [ ] Implement Auth screens (Login, Register).

### Phase 5: Mobile Feature Implementation
- [ ] Implement Group management screens.
- [ ] Implement Expense logging flow (with split method selectors).
- [ ] Implement Balance Summary and Settle Up screens.

### Phase 6: Offline-First Capability (FR15)
- [ ] Setup local SQLite database on the device.
- [ ] Implement cache-first read strategy.
- [ ] Implement `syncManager` to queue writes and sync when online.

---

## ⚠️ Critical Guidelines & Constraints

### 1. Money Handling
- **Precision:** Never use raw floating-point numbers for money equality checks.
- **Storage:** Store as `DECIMAL` in DB.
- **Transit:** Round to 2 decimal places.

### 2. Architecture Pattern
Strictly follow the layered approach on the backend:
`Route` $\rightarrow$ `Controller` $\rightarrow$ `Service` $\rightarrow$ `Model` $\rightarrow$ `Database`
- **Business logic must live in Services**, not Controllers or Models.

### 3. Naming Conventions
- **JavaScript/TypeScript:** `camelCase` for variables and functions.
- **Database:** `snake_case` for columns and tables.

### 4. Offline Logic
- Use a `pending_sync` flag for local records.
- Last-write-wins is acceptable for conflict resolution.

---

## ✅ Definition of Done (for Tasks)
- [ ] Code follows the layered architecture.
- [ ] TypeScript types are defined for all new models.
- [ ] Backend services are covered by unit tests (especially calculation logic).
- [ ] Error responses follow the standard format: `{ "error": { "code": string, "message": string } }`.
- [ ] Feature maps back to a specific Functional Requirement (FR) in `business-requirements.md`.
