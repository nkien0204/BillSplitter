# System Architecture — ChiaBill (v0.4.0, React Native + Firebase)

## 1. Tech stack

| Concern | Choice |
|---------|--------|
| App | React Native 0.88, Expo SDK 58, Expo Router, TypeScript strict |
| Auth | Firebase Auth, email/password; email = `<normalised phone>@phone.chiabill.app` |
| Database | Cloud Firestore, realtime listeners, Security Rules |
| QR | `react-native-qrcode-svg` (encode), `expo-camera` (scan + decode from image) |
| Notifications | `expo-notifications`, local notifications triggered by new inbox documents |
| Tests | Jest + `jest-expo`; Firebase Emulator Suite for integration |

## 2. Layers

```
src/app (Expo Router screens)
   │ useLedger() / useAction()
src/state/AppContext ── auth state + live AppSnapshot
   │
src/data ── LedgerRepository (interface) ◄── FirebaseLedgerRepository ──► Firestore
   │            AuthRepository ◄── FirebaseAuthRepository ──► Firebase Auth
   │            watchSnapshot: Firestore listeners → AppSnapshot
src/domain ── pure TypeScript, imported by both data and UI
```

**Request flow.** UI → `repo.xxx()` (a Promise) → validate with domain rules → `writeBatch` / `runTransaction` to Firestore → Firestore listeners fire (immediately for local writes, then on server ack) → `watchSnapshot` rebuilds one immutable `AppSnapshot` including the `Ledger` → React context → screens re-render. Errors are `LedgerError` (user-facing Vietnamese message) or the Firebase error.

## 3. Source layout

```
src/domain/
  model/    types.ts (DebtStatus, Category, Debt, ShareLine, Transfer), Money, BillSpec, Bill, SplitResult
  split/    SplitEngine (split, check, equal)
  ledger/   Ledger, DebtSimplifier, PaymentStateMachine
  qr/       Tlv, Crc16, VietQrCodec, TransferContent, PaymentTarget, BankDirectory, QrError
  user/     PhoneNumber, InviteCode
src/data/
  records.ts        Firestore document shapes
  docs.ts           DocumentSnapshot → record (defaults for missing fields)
  mappers.ts        record → domain Bill / PaymentTarget
  AppSnapshot.ts    users, groups, bills, statuses, inbox + Ledger; the only thing UI reads
  watchSnapshot.ts  composes the listeners
  LedgerRepository.ts / FirebaseLedgerRepository.ts
  AuthRepository.ts / FirebaseAuthRepository.ts
  firebase.ts       app/auth/firestore singletons (+ emulator switch)
src/state/          AppContext, notifications
src/ui/             theme, components, bill/billDraft
src/app/            login, (tabs)/{index,debts,profile}, create-group, group/[id], group/settle,
                    bill/[id], bill/edit, debt/[key], qr-setup
```

### 3.1 Screens → functional requirements

| Screen | FR |
|--------|----|
| `login` | FR1 |
| `(tabs)/index` (groups, join by code) | FR2, FR3 |
| `create-group`, `group/[id]` | FR2, FR4, FR8, FR14 |
| `bill/edit`, `bill/[id]` | FR5, FR6, FR7 |
| `group/settle` | FR9, FR10, FR11 |
| `(tabs)/debts`, `debt/[key]` | FR11, FR12, FR16, FR18, FR19 |
| `(tabs)/profile`, `qr-setup` | FR13, FR17 |

## 4. Firestore data model

| Collection / doc | Fields | Notes |
|------------------|--------|-------|
| `users/{uid}` | name, phone (normalised, null for guests), colorIndex, guest, bankBin, accountNo, accountName, qrUpdatedAt, createdAt, createdBy (guests) | Guests have id `guest-<autoId>`. `get` for signed-in users, **no `list`**. |
| `phones/{phone}` | uid | Exact lookup + uniqueness. Created in the same batch as `users/{uid}`; rules check `getAfter(users/uid).phone == phone`. |
| `groups/{id}` | name, inviteCode, createdBy, createdAt, **memberOrder[]**, **activeMemberIds[]** | `memberOrder` append-only, index = position (replaces the Room `members.position`). `activeMemberIds` replaces `members.active`. Readable only by active members. |
| `inviteCodes/{code}` | groupId | Code → group without listing groups. |
| `joinProofs/{uid}` | code, groupId, at | Write-only. A joiner is not yet a member, so the rules verify knowledge of the code with `getAfter(joinProofs/uid).code == group.inviteCode`. |
| `bills/{id}` | groupId, title, payerId, subtotal, participants[], category, createdBy, createdAt (= spend date), updatedAt | `subtotal` is split equally among `participants`. Per-person shares are **not** stored. |
| `debtStatuses/{billId:debtorId}` | billId, groupId, debtorId, status, updatedAt | Missing = `PENDING`. `groupId`/`debtorId` are new so rules and queries can use them. |
| `inbox/{id}` | recipientId, fromId, message, billId, debtKey, createdAt | Index `recipientId ASC, createdAt DESC` (`firestore.indexes.json`). |

Mapping from v0.3.0 (Room): `UserEntity`→`users`, `GroupEntity`+`MemberEntity`→`groups`, `BillEntity`+`BillItemEntity`→`bills`, `DebtStatusEntity`→`debtStatuses`, `InboxEntity`→`inbox`. `participantsCsv`/`sharesCsv`/`consumersCsv` became real arrays and maps.

## 5. Security Rules (summary)

- Only active members read a group, its bills and statuses.
- Bills: created by an active member (`createdBy == uid`); updated by creator or payer, never moved to another group; deleted by creator, payer or group creator.
- `debtStatuses`: the **payment state machine** is in the rules — only the debtor may write `MARKED_PAID` (from PENDING/DISPUTED); only the payer may write `CONFIRMED` (not from CONFIRMED) or `DISPUTED` (only from MARKED_PAID).
- Groups: members may add one member; an outsider may add only themselves with a valid join proof; the creator may remove one member; anyone may leave (creator hands over ownership).
- `inbox`: only the recipient reads or deletes; anyone may create with `fromId == uid`.
- Not enforceable in rules (needs `SplitEngine`): "don't leave/remove/delete while debts are open", and amounts in bills. Those stay in `FirebaseLedgerRepository`.

## 6. Money and splitting (`src/domain`)

Integer VND, one method: **equal split**. `SplitEngine.split(spec, memberOrder)`:

1. take the participants in `memberOrder` order;
2. `equal(total, n)`: everyone gets `floor(total / n)`; the `total mod n` leftover đồng go one each to the first participants (so shares differ by at most 1đ and always add up to the total);
3. the payer always gets a line (amount 0 if they did not take part).

There is no VAT, service fee or rounding step (removed to keep the app simple; v0.3.0 had itemised/custom/percent splits too). Splits run over **`memberOrder`** so old bills never change when people leave or join. Bills saved by older builds with another mode are read as an equal split of their `subtotal`.

## 7. Ledger and settlement

`Ledger(groupMembers, bills, statuses)` derives debts (payer = creditor), balances and the greedy settlement plan. `PaymentStateMachine` is unchanged; its transitions are also encoded in the rules and applied inside a Firestore transaction so two taps cannot skip a state.

## 8. VietQR

`VietQrCodec.encode/parse` (NAPAS GUID `A000000727`, CRC-16/CCITT-FALSE) is a line-for-line port; vectors and 5 000 round-trips are tested. The QR image is drawn by `react-native-qrcode-svg`; saving/sharing writes the PNG to the cache and uses `expo-media-library` / `expo-sharing`.

## 9. Notifications

The inbox collection is the source of truth. `useInboxNotifications` compares each snapshot's inbox to the ids already seen (the first snapshot is the baseline) and posts a local notification for every new item; tapping it routes to `debt/[key]` or `bill/[id]`. Delivery while the app is fully closed needs FCM + Cloud Functions (not implemented).

## 10. Conventions

- Vietnamese UI text and comments; identifiers in English.
- Domain files import only from `src/domain`. Data files import domain and Firebase. Screens import state, ui and domain.
- Every write goes through `LedgerRepository`; screens call it through `useAction()` so errors surface as toasts.

## 11. Out of scope / known gaps

Offline writes, push when closed, server-enforced money rules, deep-link invites, photo avatars. See README → *Known limits*.
