# AI Agent Context & Development Guide — ChiaBill (v0.4.0)

This project is a **React Native (Expo, TypeScript) app on Firebase**. It was ported from a Java/Android + Node/Express app (v0.3.0, branch `main`). Business logic is unchanged; only the technology is.

## 📌 Project overview

**ChiaBill** splits shared expenses with money that is **deterministic and drift-free**, and settles debts through **VietQR**. Accounts are phone number + password (Firebase Auth), data lives in Firestore and is pushed to the UI in real time.

## 🗺️ Read first, in order

1. `AGENTS.md` (this file)
2. `architecture.md` — data model, Firestore collections, request flow
3. `business-requirements.md` — FR/NFR and status

## 🛠️ Stack

- **Mobile:** React Native 0.88, Expo SDK 58, Expo Router (routes in `src/app/`), TypeScript strict
- **Backend:** Firebase Auth (email/password, email = `<phone>@phone.chiabill.app`) + Cloud Firestore + `firestore.rules`. No server code.
- **Expo APIs change every SDK.** Check `node_modules/<pkg>/build/*.d.ts` or https://docs.expo.dev/versions/v58.0.0/ before using one. Add native packages with `npx expo install`, never `npm i`.
- **Metro reads tsconfig `paths`.** Do not map packages to `.d.ts` files there (see `src/types/firebase-auth-rn.d.ts` for how the RN-only `getReactNativePersistence` type is declared instead).

## Layers

```
src/app (screens)  →  src/state (AppContext)  →  src/data (repositories)  →  Firestore
                                   ↘                       ↘
                                    └────────────  src/domain (pure TS)  ←──┘
```

- `src/domain` — **pure TypeScript. No `react-native`, `firebase` or `expo` imports.** All business rules: split engine, allocator, ledger, debt simplifier, payment state machine, VietQR codec, phone/invite code.
- `src/data` — the only place that touches Firebase. `LedgerRepository` is the single door for the UI; `FirebaseLedgerRepository` implements it. Business errors are `LedgerError` with a Vietnamese user-facing message.
- `src/app` / `src/ui` — screens and components. **No money maths and no permission checks in components.**

## ⚠️ Critical rules

### 1. Money (the Golden Rule)

- Money is an **integer number of VND** (`number`, always `Number.isSafeInteger`). Never fractions, never `toFixed`.
- Allocate with `Allocator` (largest remainder). It uses `BigInt` internally because `total × weight` can pass 2^53.
- Per-person shares are **recomputed** from the bill by `SplitEngine`; they are **never stored**.

### 2. Logic placement

- Business logic ∈ `src/domain` or the repository. Permissions (can this user delete this bill?) are checked in the repository **and** mirrored in `firestore.rules`. The UI may hide buttons but must not be the only guard.
- Anything that changes the calculation of a split must have a test in `src/domain/__tests__`.

### 3. Group membership order

- `groups/{id}.memberOrder` = **everyone who was ever in the group**, append-only (array index = position). `activeMemberIds` = current members. Splitting always uses `memberOrder` (`AppSnapshot.allMembersOf`); UI and permissions use `activeMemberIds` (`membersOf`). Mixing them changes how old bills divide. Re-adding a returning member uses `arrayUnion`, which keeps the old position.

### 4. Firestore gotchas (learned the hard way — tests cover them)

- Rules cannot prove access for a query unless the query constrains the field the rule reads: queries on `bills`/`debtStatuses` **must** filter by `groupId`.
- Child listeners for a group must only start once the group document is confirmed by the server (`!metadata.hasPendingWrites`), and the group listener needs `includeMetadataChanges: true`. See `watchSnapshot.ts`.
- Reading a document that does not exist must not error in rules (`resource == null ||`), otherwise "not found" turns into `permission-denied`.
- No `undefined` in documents; use `null`.
- No `sequelize.sync()`-style shortcuts exist anymore: schema is implicit, so changing a document shape means updating `records.ts`, `docs.ts`, `firestore.rules` and the integration tests together.

### 5. Phone lookup

Exact match only (`phones/{phone}` document `get`). No listing, no fuzzy search; `users` and `phones` deny `list` in the rules.

## Commands

```bash
npm install
npx expo start                 # run the app
npm test                       # unit tests (domain + ui logic)
npm run test:int               # integration tests on Firebase emulators (needs Java 11+)
npm run typecheck
npm run emulators              # start Auth + Firestore emulators for manual testing
npx firebase deploy --only firestore   # rules + indexes
```

## ✅ Definition of Done

- [ ] `npm test` and `npm run typecheck` pass.
- [ ] If you touched `src/data` or `firestore.rules`: `npm run test:int` passes (add a case that tries the forbidden thing directly against Firestore).
- [ ] `npx expo export --platform android` bundles (catches Metro/Firebase resolution problems).
- [ ] The feature maps to an FR in `business-requirements.md` and its status is updated.
- [ ] `src/domain` still has no framework imports.

## 🚀 Roadmap

Done: domain port with identical tests · Firestore data model + rules · repository with all v0.3.0 business rules · Auth · every screen · local notifications from the inbox.

To do:
- [ ] Run on a physical device; scan a generated QR with a real banking app and record evidence
- [ ] Usability sessions (time to add an expense — NFR3, SUS — NFR4)
- [ ] Cloud Functions: FCM push, and server-side enforcement of the money rules (leave/delete with open debts)
- [ ] Offline support (persistent cache / outbox) — React Native has no IndexedDB, so this needs a custom cache
- [ ] Deep link for invite codes (`chiabill://join/CODE`; the `scheme` is already set)
