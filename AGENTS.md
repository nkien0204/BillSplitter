# AI Agent Context & Development Guide — ChiaBill

Entry point for AI agents and new developers. Read this, then the two reference documents before changing anything significant.

## Project overview

**ChiaBill** is an Android app (Java native) for splitting group bills. One person pays; the app splits the bill, tracks debts, and gives each debtor a VietQR of the payer with the amount filled in. Both sides confirm each payment.

Core value: nobody does the maths by hand, nobody types an account number, and balances never drift by a single đồng.

## Reference documents

- `business-requirements.md` — FR/NFR with implementation status per requirement.
- `architecture.md` — stack, modules, database, split rules, VietQR, Phase 2 backend plan.
- `README.md` (Vietnamese) — build, demo script, demo accounts.
- `docs/project-brief.md` (Vietnamese) — full design: MVP scope, threat model, team split.

## Stack summary

- Java 17, Android minSdk 26 / compileSdk 35, AGP 8.7.3, Gradle 8.9 wrapper
- Room 2.6.1, Material 1.12, ViewBinding, ZXing
- `:domain` pure Java module (JUnit 5), `:app` Android module
- No backend in the current release; see `architecture.md` §8

## Roadmap

### Phase 1 — offline app (done in v0.3.0)
- [x] Domain: integer-VND split engine (equal, itemised, custom amounts, percentage), VAT/fee, rounding
- [x] Domain: ledger, debt simplifier, payment state machine
- [x] Domain: VietQR encode/parse with CRC, phone numbers, invite codes
- [x] 52 unit/property tests on `:domain`
- [x] Room schema v3, `LedgerRepository` + local implementation, demo data
- [x] Accounts by phone number, groups, add member by phone, invite code, remove member, leave group
- [x] Bills: category, date, 4 split modes, edit/delete with permission checks
- [x] History filters (category, member, period)
- [x] Debt screen with VietQR, save/share QR, notifications, settle-up plan
- [x] Profile: rename, receiving QR (image, camera, paste, manual)

### Phase 1 — still to do
- [ ] Run `connectedDebugAndroidTest` on a device and keep the log
- [ ] Scan a generated QR with a real banking app and record evidence
- [ ] Usability sessions: time to add an expense (NFR3), SUS score (NFR4)

### Phase 2 — multi-device (optional)
- [ ] Choose: Node/Express + JWT (per `architecture.md` §8) or Firebase
- [ ] `RemoteLedgerRepository` (or `FirebaseLedgerRepository`) behind the existing interface
- [ ] Offline queue with `pendingSync`, last-write-wins
- [ ] Real Room migrations instead of destructive fallback

## Critical rules

1. **Money is `long` VND.** Never `double`/`float`, never DECIMAL-with-2-places. All allocation goes through `Allocator` (largest remainder) so parts always sum to the total.
2. **Business logic lives in `:domain` or the repository**, never in an Activity. `:domain` must not import anything from Android.
3. **Shares are recomputed, not stored.** Only `debt_status` is stored, keyed `billId:debtorId`.
4. **Split order uses all members ever in the group** (`AppSnapshot.allMembersOf`), UI and permissions use active members (`membersOf`). Mixing these up changes old bills.
5. **Permission checks happen in `LocalLedgerRepository`**, not only in the UI (edit/delete bill, payment actions, remove member).
6. **Phone lookup is exact match only.** No listing or fuzzy search of users.
7. Changing a Room entity → bump `AppDatabase` version.

## Definition of done

- [ ] `gradlew :domain:test` passes; new split/balance rules have a test
- [ ] `gradlew :app:assembleDebug` builds
- [ ] Feature maps to an FR in `business-requirements.md` and its status column is updated
- [ ] README demo steps updated if the flow changed
