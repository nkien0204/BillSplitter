# System Architecture — Shared Expense / Bill Splitter App

This document describes the system design and technology stack for the Bill Splitter app. It is written to give an AI coding agent (or a new developer) enough context to generate, modify, or review code consistently with the intended architecture.

---

## 1. Tech stack

| Layer | Technology |
|---|---|
| Mobile frontend | React Native (JavaScript/TypeScript) |
| Backend | Node.js + Express |
| Database | SQLite (server-side, accessed via an ORM — Sequelize or Prisma) |
| Local/offline storage | SQLite on-device (via `react-native-sqlite-storage` or `WatermelonDB`) |
| Authentication | JWT (JSON Web Tokens) |
| API style | REST, JSON payloads over HTTPS |

---

## 2. High-level architecture

```
┌─────────────────────┐        HTTPS / REST (JSON)        ┌─────────────────────┐        SQL (via ORM)        ┌─────────────────┐
│     Mobile App       │ ───────────────────────────────► │    Backend Server    │ ───────────────────────────► │   Database        │
│  React Native        │ ◄─────────────────────────────── │   Node.js + Express   │ ◄───────────────────────────  │   SQLite          │
│  + local SQLite cache│                                   │   (JWT auth)          │                              │                  │
└─────────────────────┘                                   └─────────────────────┘                              └─────────────────┘
```

- The mobile app is the only client. It never talks to the database directly — all data access goes through the backend REST API.
- The mobile app keeps a local SQLite cache so core screens (expense list, balances) work offline. Writes made offline are queued and synced when connectivity returns.
- The backend is stateless (aside from the database); JWTs carry the authenticated user's identity on every request.

---

## 3. Mobile app (React Native)

### 3.1 Responsibilities
- Render UI screens
- Cache data locally in SQLite for offline access
- Queue writes made while offline, sync them when back online
- Call the backend REST API for all reads/writes when online

### 3.2 Suggested folder structure

```
/src
  /screens
    LoginScreen.tsx
    RegisterScreen.tsx
    GroupListScreen.tsx
    GroupDetailScreen.tsx
    AddExpenseScreen.tsx
    BalanceSummaryScreen.tsx
    SettleUpScreen.tsx
    ProfileScreen.tsx
  /components
    ExpenseListItem.tsx
    BalanceRow.tsx
    SplitSelector.tsx
  /navigation
    AppNavigator.tsx
  /api
    client.ts          // axios/fetch wrapper, attaches JWT header
    authApi.ts
    groupApi.ts
    expenseApi.ts
  /db
    localDb.ts          // SQLite setup (on-device)
    syncManager.ts       // handles offline queue + sync on reconnect
  /store
    authStore.ts
    groupStore.ts        // state management (e.g. Zustand, Redux, or Context)
  /types
    models.ts             // shared TypeScript interfaces (User, Group, Expense, etc.)
```

### 3.3 Core screens → functional requirement mapping

| Screen | Related FRs |
|---|---|
| LoginScreen / RegisterScreen | FR1 |
| GroupListScreen / GroupDetailScreen | FR2, FR3, FR4, FR14 |
| AddExpenseScreen | FR5, FR6, FR7 |
| BalanceSummaryScreen | FR9, FR10 |
| SettleUpScreen | FR11, FR12 |
| ProfileScreen | FR13 |

### 3.4 Offline behavior (FR15)
- All reads render from local SQLite first (cache-first).
- Writes (new expense, settlement) are written to local SQLite immediately and marked `pending_sync = true`.
- A `syncManager` listens for connectivity changes and pushes pending records to the backend in order, then marks them synced.
- Conflict handling: last-write-wins is acceptable for this project's scope; no merge logic required.

---

## 4. Backend (Node.js + Express)

### 4.1 Responsibilities
- Expose REST API endpoints
- Authenticate requests via JWT
- Contain all business logic (especially balance/split calculations)
- Read/write the SQLite database via an ORM

### 4.2 Suggested folder structure

```
/src
  /routes
    auth.routes.js
    group.routes.js
    expense.routes.js
    settlement.routes.js
  /controllers
    auth.controller.js
    group.controller.js
    expense.controller.js
    settlement.controller.js
  /services
    auth.service.js
    balance.service.js     // core split/balance calculation logic
    expense.service.js
  /models                  // ORM models (Sequelize/Prisma)
    user.model.js
    group.model.js
    groupMember.model.js
    expense.model.js
    expenseShare.model.js
    settlement.model.js
  /middleware
    authMiddleware.js       // verifies JWT
    errorHandler.js
  /config
    db.js
    env.js
  app.js
  server.js
```

### 4.3 Architecture pattern
Layered architecture, request flows as:

```
Route → Controller → Service → Model (ORM) → Database
```

- **Routes**: define endpoint + HTTP method, attach middleware
- **Controllers**: parse request, call service, format response
- **Services**: business logic (e.g. `balance.service.js` computes who-owes-whom)
- **Models**: ORM schema definitions and queries only — no business logic here

### 4.4 Authentication flow
1. User logs in with email/password → backend verifies password hash → issues a JWT.
2. Mobile app stores the JWT securely (e.g. Keychain/Keystore via a secure storage library) and attaches it as `Authorization: Bearer <token>` on every subsequent request.
3. `authMiddleware` verifies the JWT on protected routes and attaches `req.user`.

---

## 5. Database schema (SQLite)

### 5.1 Tables

**users**
| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK | |
| name | TEXT | |
| email | TEXT | unique |
| password_hash | TEXT | bcrypt hash |
| avatar_url | TEXT | nullable |
| created_at | DATETIME | |

**groups**
| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK | |
| name | TEXT | e.g. "Apartment 4B" |
| invite_code | TEXT | unique |
| created_by | INTEGER | FK → users.id |
| created_at | DATETIME | |

**group_members**
| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK | |
| group_id | INTEGER | FK → groups.id |
| user_id | INTEGER | FK → users.id |
| joined_at | DATETIME | |

**expenses**
| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK | |
| group_id | INTEGER | FK → groups.id |
| description | TEXT | |
| amount | DECIMAL | total expense amount |
| category | TEXT | nullable |
| paid_by | INTEGER | FK → users.id |
| split_type | TEXT | 'equal' \| 'custom' \| 'percentage' |
| date | DATETIME | |
| created_at | DATETIME | |

**expense_shares**
| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK | |
| expense_id | INTEGER | FK → expenses.id |
| user_id | INTEGER | FK → users.id |
| amount_owed | DECIMAL | this user's share of the expense |

**settlements**
| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK | |
| group_id | INTEGER | FK → groups.id |
| paid_by | INTEGER | FK → users.id (debtor) |
| paid_to | INTEGER | FK → users.id (creditor) |
| amount | DECIMAL | |
| method | TEXT | e.g. 'cash', 'bank transfer' — free text, nullable |
| settled_at | DATETIME | |

### 5.2 Balance calculation logic
A user's net balance in a group = (sum of `expense_shares.amount_owed` where they are the payer, across all group expenses) − (sum of `expense_shares.amount_owed` where they are a participant) + (net of `settlements` involving them). This calculation lives in `balance.service.js` and should be covered by unit tests, since correctness here is a core evaluation criterion (see NFR12).

---

## 6. REST API endpoints

| Method | Endpoint | Description | Related FR |
|---|---|---|---|
| POST | /api/auth/register | Register a new user | FR1 |
| POST | /api/auth/login | Log in, returns JWT | FR1 |
| GET | /api/users/me | Get current user profile | FR13 |
| PUT | /api/users/me | Update current user profile | FR13 |
| POST | /api/groups | Create a group | FR2 |
| POST | /api/groups/join | Join a group via invite code | FR3 |
| GET | /api/groups | List groups for current user | FR2 |
| GET | /api/groups/:id | Get group details + members | FR4 |
| DELETE | /api/groups/:id/members/:userId | Remove a member | FR4 |
| POST | /api/groups/:id/leave | Leave a group | FR14 |
| POST | /api/groups/:id/expenses | Add an expense | FR5, FR6 |
| PUT | /api/expenses/:id | Edit an expense | FR7 |
| DELETE | /api/expenses/:id | Delete an expense | FR7 |
| GET | /api/groups/:id/expenses | List/filter expenses in a group | FR8 |
| GET | /api/groups/:id/balances | Get computed balances for the group | FR9, FR10 |
| POST | /api/groups/:id/settlements | Record a settle-up payment | FR11, FR12 |

All endpoints except `/api/auth/register` and `/api/auth/login` require a valid JWT in the `Authorization` header.

---

## 7. Conventions

- **Naming**: camelCase for JS variables/functions, snake_case for database columns.
- **Money handling**: store amounts as DECIMAL in the database and as numbers rounded to 2 decimal places in transit; never rely on raw floating-point equality when checking balances.
- **Error responses**: consistent JSON shape `{ "error": { "code": string, "message": string } }`.
- **Dates**: store and transmit in ISO 8601 (UTC); format for display on the client.
- **IDs**: integers, auto-incrementing, generated by the database.

---

## 8. Out of scope (for this project)

- Real-time push notifications
- Multi-currency support
- Payment gateway integration (settling is recorded manually, not processed)
- Horizontal scaling / production-grade deployment concerns
