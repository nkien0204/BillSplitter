# Business Requirements Document

## Project: ChiaBill — Shared Expense / Bill Splitter App

### 1. Overview

A mobile app for friends, roommates and small groups. One person pays a shared bill; the app splits it, tracks who owes whom, and lets each debtor pay back by scanning a **VietQR code of the payer, prefilled with the exact amount**. Both sides confirm each payment.

**Target platform:** Android, **Java native** (course ET4710 requires Java; iOS is out of scope)
**Target users:** Roommates, shared households, small friend groups
**Current release:** v0.3.0 — offline demo. All demo accounts share one on-device database, so accounts interact on a single phone without a server.

> **Change from the first draft (commit 3e60823):** the stack moved from React Native + Node/Express to Java native Android, because the course requires Java. The server-side requirements are kept, marked **Phase 2**, and the Android app already hides storage behind a `LedgerRepository` interface so a server can be added without touching the screens.

Status legend: **Done** = implemented in v0.3.0 · **Partial** = implemented with the stated limit · **Phase 2** = needs a backend.

---

### 2. Functional Requirements (FR)

#### 2.1 User & Group Management

| ID | Requirement | Status | Where |
|----|-------------|--------|-------|
| FR1 | The system shall allow a user to create an account and log in. | **Partial.** Account = display name + Vietnamese mobile number (normalised, unique). Login = pick an account (offline demo). Email/password + JWT is Phase 2. | `LoginActivity`, `PhoneNumber` |
| FR2 | The system shall allow a user to create a group and invite members. | **Done.** Add members by exact phone-number match; each group gets an invite code. | `CreateGroupActivity`, `GroupDetailActivity` |
| FR3 | The system shall allow a user to join an existing group via an invite code or link. | **Partial.** 6-character invite code (`DAL-AT7`), shareable as text. Deep link is Phase 2. | `InviteCode`, `GroupsFragment` |
| FR4 | The system shall allow a user to view and manage members of a group (add or remove members). | **Done.** Group creator removes a member; blocked while that member has unconfirmed debts in the group. | `LocalLedgerRepository.removeMember` |

#### 2.2 Expense Management

| ID | Requirement | Status | Where |
|----|-------------|--------|-------|
| FR5 | The system shall allow a user to add an expense with amount, description, category, date, payer, and participants. | **Done.** Categories: Ăn uống, Đi lại, Lưu trú, Mua sắm, Giải trí, Khác. Date picker (no future dates). VAT 0/8/10 % and service fee 0/5/10 % are extra. | `BillEditActivity`, `Category` |
| FR6 | The system shall allow a user to choose a split method: equal split, custom amounts, or percentage split. | **Done**, plus itemised split (each item shared by the people who had it). Percentages must total exactly 100 %. | `SplitEngine` |
| FR7 | The system shall allow a user to edit or delete an expense they created. | **Done.** Creator or payer only; bills with a confirmed payment cannot be deleted. | `LocalLedgerRepository.saveBill/deleteBill` |
| FR8 | The system shall allow a user to view a history of all expenses in a group, filterable by date, member, or category. | **Done.** Filters: category, member (paid or has a share), period (7 days, 30 days, this month). | `GroupDetailActivity` |

#### 2.3 Balance & Settlement

| ID | Requirement | Status | Where |
|----|-------------|--------|-------|
| FR9 | The system shall automatically calculate each member's balance. | **Done.** | `Ledger` |
| FR10 | The system shall display a summary screen showing net balances per member. | **Done.** | `SettleActivity` |
| FR11 | The system shall allow a user to record a "settle up" payment, marking a debt as paid. | **Done.** Debtor marks paid → payer confirms or disputes (state machine). Settle screen also proposes the fewest transfers. | `PaymentStateMachine`, `DebtSimplifier` |
| FR12 | The system shall update all affected balances immediately after any expense or settlement change. | **Done.** Room LiveData → one immutable `AppSnapshot`. Editing an amount resets unconfirmed debts to "not paid" and notifies the debtor. | `AppSnapshot` |

#### 2.4 Other

| ID | Requirement | Status | Where |
|----|-------------|--------|-------|
| FR13 | The system shall allow a user to edit their profile (name, avatar). | **Partial.** Name editable; avatar is the initial on a fixed colour. Photo upload not planned. | `ProfileFragment` |
| FR14 | The system shall allow a user to leave a group. | **Done.** Blocked while the user still owes or is owed in that group. Old bills keep the user's share. | `LocalLedgerRepository.leaveGroup` |
| FR15 | The system shall allow the app to function offline, caching data locally and syncing once the device reconnects. | **Partial.** Fully offline (Room). Sync is Phase 2. | — |

#### 2.5 Added by ChiaBill

| ID | Requirement | Status |
|----|-------------|--------|
| FR16 | Each debt shall show a VietQR (NAPAS, EMVCo) of the payer's bank account with amount and transfer note prefilled, generated on the device. | **Done** |
| FR17 | A user shall register their receiving account by picking a QR screenshot, scanning with the camera, pasting the QR string, or typing bank + account number. Invalid QR (bad CRC, not VietQR) is rejected with a reason. | **Done** |
| FR18 | Debtors, payers and added members shall receive in-app and system notifications; tapping one opens the related debt. | **Done** |
| FR19 | A debtor shall be able to save the QR image or share it to a guest who does not have the app. | **Done** |

---

### 3. Non-Functional Requirements (NFR)

| ID | Category | Requirement | Status |
|----|----------|-------------|--------|
| NFR1 | Performance | API responses within ~2 s under normal load. | Phase 2 (no API yet) |
| NFR2 | Performance | Balance recalculation near-instant on device after any change. | **Done.** Pure-Java split of 10 000 random bills runs in well under a second in unit tests. |
| NFR3 | Usability | Adding an expense shall take no more than 3–4 inputs/taps. | **To measure.** Equal split with defaults: amount → save (payer, participants, category, date pre-filled). |
| NFR4 | Usability | SUS ≥ 70 in evaluation. | **To measure** with classmates |
| NFR5 | Reliability | Offline-first: add and view expenses without network. | **Done** |
| NFR6 | Security | Passwords hashed (e.g. bcrypt). | Phase 2 |
| NFR7 | Security | JWT for protected API routes. | Phase 2 |
| NFR8 | Security | HTTPS for all client–server traffic. | Phase 2. Today the app makes **no** network calls; QR is generated on the device. |
| NFR9 | Scalability | Multiple groups per user without slowdown. | **Done** on device; server side Phase 2 |
| NFR10 | Maintainability | Layered architecture with clear separation. | **Done** on Android: `:domain` (pure Java, no Android) → `data` (Room + repository) → `ui`. Backend layering (routes → controllers → services → models) applies in Phase 2. |
| NFR11 | Portability | ~~iOS and Android from one React Native codebase.~~ **Android 8.0+ (API 26+), Java native.** | **Changed** (course requires Java) |
| NFR12 | Data Consistency | Balances always equal the sum of underlying records, no drift. | **Done.** Money is integer VND (`long`), never floating point. Largest-remainder allocation; property tests on 10 000 random bills. |
| NFR13 | Availability | Backend stable during demo. | Phase 2 |

---

### 4. Traceability Note

FR5–FR12 are the core evaluation criteria: correctness of balance calculations and usability of the expense and settlement flows. Correctness is covered by the `:domain` unit and property tests (`gradlew :domain:test`, 52 tests); usability by task-based sessions with classmates (NFR3, NFR4).
