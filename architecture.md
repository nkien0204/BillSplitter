# System Architecture — ChiaBill (Bill Splitter)

This document describes how the app is built, so a new developer or an AI coding agent can change it consistently. Requirements and their status are in `business-requirements.md`.

---

## 1. Tech stack

| Layer | Technology |
|---|---|
| Language | Java 17 (course requirement: Java native) |
| Platform | Android, minSdk 26 (8.0), targetSdk/compileSdk 35 |
| Build | Gradle 8.9 wrapper, Android Gradle Plugin 8.7.3 |
| UI | Activities + Fragments, XML layouts, ViewBinding, Material Components 1.12 |
| State | Room LiveData → one immutable `AppSnapshot`; ViewModel keeps the bill draft across rotation |
| Local storage | Room 2.6.1 (SQLite), annotation processor |
| QR | ZXing core 3.5.3 (encode/decode) + zxing-android-embedded 4.3.0 (camera scan) |
| Tests | JUnit 5 on `:domain`; AndroidX instrumented tests on Room |
| Backend | None in v0.3.0. Phase 2 plan in §8 |

---

## 2. High-level architecture

```
┌──────────────────────────── Android app (single APK) ───────────────────────────┐
│                                                                                  │
│  ui/  Activities, Fragments ──reads──►  AppSnapshot (immutable, LiveData)        │
│        │                                     ▲                                   │
│        │ calls                                │ rebuilt on every table change     │
│        ▼                                     │                                   │
│  data/repo  LedgerRepository (interface) ◄── LocalLedgerRepository ── Room DB     │
│        │                                                                         │
│        ▼ uses                                                                    │
│  :domain  (pure Java, no Android imports)                                        │
│     split/ SplitEngine, Allocator   ledger/ Ledger, DebtSimplifier,              │
│     qr/ VietQrCodec, Crc16, Tlv      PaymentStateMachine   user/ PhoneNumber,    │
│                                                             InviteCode           │
└──────────────────────────────────────────────────────────────────────────────────┘
```

- **All business rules live in `:domain`** and in the repository. Screens never compute money themselves; they call `SplitEngine` / `Ledger` through the snapshot.
- **Screens only depend on `LedgerRepository`.** Swapping `LocalLedgerRepository` for a remote one (Phase 2) does not touch the UI.
- **Offline multi-account demo:** every demo account lives in the same Room database. Switching account in the app shows that user's view, so "Minh adds a bill → Đạt sees the debt" works on one phone. A notification addressed to another account switches to that account when tapped.

---

## 3. Modules and packages

```
settings.gradle        :domain, :app
domain/src/main/java/vn/nhom03/chiabill/domain/
  model/    Money, Bill, BillSpec, BillItem, Category, SplitMode, SplitResult, ShareLine, Debt, DebtStatus, Transfer
  split/    Allocator (largest remainder), SplitEngine
  ledger/   Ledger, DebtSimplifier, PaymentStateMachine
  qr/       VietQrCodec, Tlv, Crc16, PaymentTarget, BankDirectory, TransferContent, QrError, QrParseResult
  user/     PhoneNumber, InviteCode
domain/src/test/java/…  AllocatorTest, SplitEngineTest, LedgerTest, VietQrCodecTest, PhoneNumberTest, InviteCodeTest

app/src/main/java/vn/nhom03/chiabill/
  ChiaBillApp            wires DB, repository, session, notifications
  data/db/               Room entities + AppDao + AppDatabase
  data/repo/             LedgerRepository, LocalLedgerRepository, AppSnapshot, Mappers, BillInput, DemoSeeder
  ui/                    LoginActivity, MainActivity (tabs), CreateGroupActivity, GroupDetailActivity,
                         BillEditActivity, BillDetailActivity, DebtDetailActivity, SettleActivity, QrSetupActivity
  ui/tabs/               GroupsFragment, DebtsFragment, ProfileFragment
  ui/bill/               BillDraft, BillEditViewModel
  util/                  SessionManager, NotificationHelper, QrImages, QrExport, Nav, Ui
app/src/androidTest/…    DemoSeederTest (Room in-memory)
```

### 3.1 Screens → functional requirements

| Screen | FRs |
|---|---|
| LoginActivity (pick / create account) | FR1 |
| GroupsFragment (list, create, join by code) | FR2, FR3 |
| GroupDetailActivity (members, invite code, history + filters, leave) | FR4, FR8, FR14 |
| BillEditActivity (4 split modes, category, date, live preview) | FR5, FR6, FR7 |
| BillDetailActivity | FR7, FR9 |
| DebtDetailActivity (VietQR, mark paid / confirm / dispute, share QR) | FR11, FR16, FR19 |
| SettleActivity (net balances, fewest transfers) | FR10, FR11 |
| ProfileFragment (rename, receiving QR, inbox) | FR13, FR17, FR18 |

---

## 4. Data model (Room, database version 3)

Upgrades use `fallbackToDestructiveMigration()`: installing a new schema wipes local data and the demo data is re-seeded. Acceptable for a demo; Phase 2 must add real migrations.

| Table | Key columns | Notes |
|---|---|---|
| `users` | `id` PK, `name`, `phone` (unique), `guest`, `bankBin`, `accountNo`, `accountName`, `qrUpdatedAt`, `colorIndex` | Guest = person without the app (no phone, gets QR by share) |
| `bill_groups` | `id` PK, `name`, `createdBy`, `inviteCode` (unique), `createdAt` | `createdBy` = manager; handed to the next member if they leave |
| `members` | (`groupId`, `userId`) PK, `position`, `active` | Leaving/removal sets `active = false`; the row stays so old bills split exactly as before |
| `bills` | `id` PK, `groupId`, `title`, `payerId`, `mode`, `subtotal`, `participantsCsv`, `sharesCsv`, `vatPercent`, `servicePercent`, `rounding`, `category`, `createdBy`, `createdAt` (= expense date), `updatedAt` | `mode` ∈ EQUAL, ITEMIZED, CUSTOM, PERCENT |
| `bill_items` | `billId`, `position`, `name`, `price`, `consumersCsv` | ITEMIZED only |
| `debt_status` | `debtKey` = `billId:debtorId` PK, `billId`, `status`, `updatedAt` | Missing row = PENDING. Deterministic key → writes are idempotent |
| `inbox` | `id`, `recipientId`, `message`, `billId`, `debtKey`, `createdAt` | In-app notifications |

**Per-person shares are never stored.** They are recomputed by `SplitEngine` from the bill, which is deterministic. Only payment status is stored.

`sharesCsv` format: `userId:value,…` — VND for CUSTOM, basis points (1 % = 100) for PERCENT.

---

## 5. Money and splitting (`:domain`)

- Money is `long` VND everywhere. No floating point, no DECIMAL.
- `Allocator.allocate(total, weights)` — largest-remainder method; the parts always sum to `total`; ties go to the lower index (deterministic).
- `SplitEngine.split(spec, memberOrder)`:
  1. **Base share** by mode: EQUAL (subtotal split over participants), ITEMIZED (each item split over its consumers), CUSTOM (typed amounts; subtotal = their sum), PERCENT (subtotal allocated by basis-point weights).
  2. **VAT and service fee** computed on the subtotal (round half up) and allocated in proportion to base shares.
  3. **Rounding:** debtors rounded to 1 đ or 1 000 đ; the payer absorbs the difference. If that would make the payer negative, debtors are rounded down instead.
  - Invariants (property-tested on 10 000 random bills): sum of shares = total; no negative share; same input → same output.
- `SplitEngine.check(spec, memberOrder)` returns a user-facing error or `null` (e.g. percentages not totalling 100 %). Both the form and the repository call it.
- `memberOrder` = **all** members who were ever in the group (active or not), by position. This is what keeps old bills stable when someone leaves.

## 6. Ledger and settlement

- `Ledger` builds one `Debt` per (bill, debtor ≠ payer, amount > 0) and computes `owedTo`, `owedBy`, `netBalances`.
- Balance = sum of open debts (not CONFIRMED) — equivalent to the "paid − owed + settlements" formula in the first draft, but settlements are per debt so there is no separate settlements table to drift.
- `DebtSimplifier` greedily matches largest creditor with largest debtor: at most n − 1 transfers.
- `PaymentStateMachine`: `PENDING → MARKED_PAID` (debtor) `→ CONFIRMED | DISPUTED` (payer); `DISPUTED → MARKED_PAID` (debtor) or `→ CONFIRMED` (payer); the payer can also confirm straight from `PENDING` (cash). Every transition is checked in the repository against the actor's role, not trusted from the UI.
- Editing a bill resets non-confirmed debts whose amount or payer changed back to PENDING and notifies the debtor.

## 7. VietQR

- Encoded on device as EMVCo TLV: `00` format, `01` = 12 (dynamic, with amount), `38` NAPAS (`A000000727`, bank BIN, account, `QRIBFTTA`), `53` = 704, `54` amount, `58` = VN, `62.08` transfer note (`CB <bill> <debtor>`, ASCII), `63` CRC-16/CCITT-FALSE.
- Parsing a user's own QR rejects: not EMV, truncated, bad CRC, not NAPAS VietQR, unsupported service.
- No bank account data leaves the device; there is no network call.

---

## 8. Phase 2: backend (from the first draft, kept as the plan)

Add a server only if multi-device sync is required. Plan:

- **Server:** Node.js + Express + SQLite via ORM, JWT auth (bcrypt passwords), layered `routes → controllers → services → models`, error shape `{ "error": { "code", "message" } }`. `balance.service.js` must port the same rules as `SplitEngine` (integer VND, largest remainder) and reuse its test vectors.
- **Android:** `RemoteLedgerRepository implements LedgerRepository` using Retrofit + OkHttp over HTTPS; Room stays as the offline cache with a `pendingSync` flag, last-write-wins. `ChiaBillApp` switches implementation; screens unchanged.
- **Cheaper alternative:** Firebase Auth (phone OTP) + Firestore with Security Rules, as `FirebaseLedgerRepository`.

| Method | Endpoint | FR |
|---|---|---|
| POST | /api/auth/register, /api/auth/login | FR1 |
| GET / PUT | /api/users/me | FR13 |
| GET | /api/users/by-phone/:phone (exact match only) | FR2 |
| POST / GET | /api/groups | FR2 |
| POST | /api/groups/join (invite code) | FR3 |
| GET | /api/groups/:id | FR4 |
| DELETE | /api/groups/:id/members/:userId | FR4 |
| POST | /api/groups/:id/leave | FR14 |
| POST / GET | /api/groups/:id/bills (filters: category, member, from, to) | FR5, FR6, FR8 |
| PUT / DELETE | /api/bills/:id | FR7 |
| GET | /api/groups/:id/balances | FR9, FR10 |
| POST | /api/debts/:billId/:debtorId/actions (MARK_PAID, CONFIRM, DISPUTE) | FR11, FR12 |

---

## 9. Conventions

- Java: camelCase; one public class per file; Vietnamese user-facing strings and comments are fine.
- Money: `long` VND only. Never `double`.
- Dates: epoch milliseconds (`long`); formatted on display.
- IDs: String. Demo data uses readable ids (`dat`, `g1`, `b-lau`); new rows use UUIDs.
- Room writes run on the repository's single background executor; callbacks are posted to the main thread.
- Any change to split or balance rules needs a domain test.

## 10. Out of scope

Real bank payment processing (settling is confirmed by people, not by the bank), multi-currency, iOS, production deployment.
