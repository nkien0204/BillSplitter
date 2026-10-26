# ChiaBill — chia tiền nhóm bằng VietQR

Mobile app for splitting shared expenses with **zero money drift**, settling debts by scanning a **VietQR code pre-filled with the exact amount**.

**v0.4.0 — React Native (Expo, TypeScript) + Firebase (Auth + Firestore).** Ported from the Java/Android + Node/Express version (v0.3.0, branches `feature/backend-design` / `android-java-firebase`). Since the port, splitting has been reduced to a single method, the **equal split**, so the money logic stays small and easy to explain (see [Money rules](#money-rules)).

## Features

- Sign up / log in with **phone number + password**. Find people by exact phone number only.
- Groups: invite by phone number or 6-character invite code, guests (people without an account), leave / remove / delete group once everything is settled.
- Bills: total amount split **equally** among the chosen participants, payer, category, spend date; history filterable by category, member and period.
- Debts: one debt per participant towards the payer. The debtor pays by scanning the payer's VietQR (amount and transfer note pre-filled), taps *Tôi đã chuyển*; the payer confirms or disputes.
- Settle screen: net balance per member and the fewest transfers to settle the group.
- Receiving account: scan your bank's QR, pick a screenshot of it, paste the QR string, or type the account in; only the bank BIN, account number and holder name are kept.
- Inbox + local notifications for new bills, edits, payments and reminders.

## Stack

| Layer | Technology |
|-------|-----------|
| Mobile | React Native 0.88 · Expo SDK 58 · Expo Router · TypeScript (strict) |
| Backend / DB | Firebase Authentication (phone number + password) · Cloud Firestore · Security Rules |
| QR | `react-native-qrcode-svg` (generate) · `expo-camera` (scan, decode from image) |
| Tests | Jest (`jest-expo`) · Firebase Emulator Suite for integration tests |

There is **no server to run**. Everything the Express backend did is either the client-side repository (`src/data`) or Firestore Security Rules (`firestore.rules`). No images are stored in Firebase: QR codes are decoded and generated on the device.

## Money rules

- Money is an **integer number of VND**, never a fraction.
- A bill stores only its inputs: `subtotal`, `payerId`, `participants`. Per-person shares are **recomputed** by `SplitEngine` on every device, never stored.
- **Equal split:** everyone gets `floor(total / n)`; the `total mod n` leftover đồng go one each to the first participants in group order. Shares differ by at most 1đ and always add up to the total. Example: 100.000đ for 3 people → 33.334 / 33.333 / 33.333.
- Group order is `groups/{id}.memberOrder`: everyone who was ever in the group, append-only. That keeps old bills unchanged when people leave or rejoin.
- No VAT, service fee or rounding step. (v0.3.0 also had itemised, custom-amount and percentage splits; they were removed.)

Details: [architecture.md](architecture.md) §6–7.

## Getting started

### Prerequisites

- Node.js 20+ and npm
- Expo Go **or** a dev build (`npm run android` / `npm run ios`) on a device or simulator
- Optional: your own Firebase project (Spark/free plan is enough), or just the emulators (see below)

### 1. Install

```bash
npm install
```

### 2. Firebase

Out of the box the app connects to the team's Firebase project `bill-splitter-eb11a`: its web config is the default in `src/data/firebase.ts`, so any build (including an APK built on another machine) talks to the same database. These values are public by design; security comes from `firestore.rules` and Firebase Auth.

To use **your own project** instead:

1. [Firebase console](https://console.firebase.google.com) → *Add project*.
2. *Build → Authentication → Get started → Sign-in method → Email/Password → Enable.*
   (The app maps the phone number to a synthetic email `<phone>@phone.chiabill.app`, so no SMS is sent and no billing is needed.)
3. *Build → Firestore Database → Create database* (production mode).
4. *Project settings → Your apps → Web app* → copy the config into `.env` (values in `.env` override the defaults):

```bash
cp .env.example .env   # then fill in the EXPO_PUBLIC_FIREBASE_* values
```

5. Deploy rules and the index (the inbox query needs the composite index in `firestore.indexes.json`):

```bash
npx firebase login
npx firebase deploy --only firestore --project <your-project-id>
```

Pass `--project` explicitly: the `default` alias in `.firebaserc` may not point at the project you mean.

### 3. Run the app

```bash
npx expo start
```

Open it in Expo Go, or build a dev client (`npm run android`) for full camera / media-library / notification behaviour.

### Run against the emulators instead (no cloud project)

Needs Java 11+ (Java 21 recommended; newer `firebase-tools` will require it).

```bash
npm run emulators            # Auth :9099, Firestore :8080
# in .env: EXPO_PUBLIC_USE_EMULATOR=true
#   EXPO_PUBLIC_EMULATOR_HOST=10.0.2.2   (Android emulator)  |  localhost (iOS simulator)  |  your LAN IP (real phone)
npx expo start
```

### First run: create two accounts

Register two accounts (two devices, or sign out/in). Account A: *Tạo nhóm* → search B by exact phone number → add. A adds a bill → B sees it in *Khoản nợ* and gets a notification. B pays by scanning the QR (A must first set a receiving account in *Tôi → Khai QR nhận tiền*), taps *Tôi đã chuyển*; A taps *Đã nhận tiền*.

## Build an Android APK

```bash
npm run build:apk          # cloud build with EAS (profile "preview"), needs an Expo account
npm run build:apk:local    # local release build → build/chiabill.apk (needs Android SDK + JDK 17)
npm run install:apk        # install build/chiabill.apk on the USB-connected phone (adb)
npm run release:usb        # build and run the release variant directly on the USB-connected phone
```

Everyone in a group should use the same app version: older builds still write bills with VAT / other split modes, which the current version reads as a plain equal split.

## Tests

```bash
npm test                 # unit tests (41): domain (39) + UI draft logic (2)
npm run test:int         # integration tests against the Firebase emulators (25): repository + Security Rules
npm run typecheck
```

`npm test` covers the split engine (equal split edge cases plus 10 000 random bills: never off by 1đ, shares differ by at most 1đ, deterministic), ledger and debt simplifier (5 000 random nets), payment state machine, VietQR codec (round-trips), phone numbers and invite codes. Random tests use a re-implementation of `java.util.Random` with fixed seeds, so every run sees the same cases.

`npm run test:int` starts the Auth + Firestore emulators with the real `firestore.rules`, then drives the real repositories as several separate "devices": permissions, the payment state machine, leave/remove/delete rules, invite-code joins, and attempts to bypass the client by writing to Firestore directly.

Before merging, also check that the Android bundle builds: `npx expo export --platform android`.

## Project layout

```
src/
  domain/      Pure TypeScript business rules (split engine, ledger, VietQR, phone, invite code). NO react-native / firebase imports.
  data/        Firestore repository, auth, snapshot builder (the only code that knows Firebase)
  state/       React context (auth + live snapshot), local notifications
  ui/          Theme, shared components, bill draft logic
  app/         Expo Router screens (file = route)
  util/        Formatting, QR image export
scripts/       APK build / install helpers
firestore.rules, firestore.indexes.json, firebase.json   Firebase config
```

See [architecture.md](architecture.md) for the data model and request flow, [AGENTS.md](AGENTS.md) for the rules to follow when changing code, [business-requirements.md](business-requirements.md) for FR/NFR status.

## Known limits

- **Push when the app is closed** needs FCM + a Cloud Function (Blaze plan). Today every new inbox document is turned into a local notification while the app is open or in the background; otherwise it waits in the inbox (tab *Tôi*).
- **Offline writes**: React Native has no persistent Firestore cache, so the app needs a connection to start and to write. Realtime listeners keep the UI live while online.
- **Money rules are enforced on the client** (and in the rules where they are not about amounts). Firestore Rules cannot run `SplitEngine`, so a hacked client could, for example, leave a group while owing money. Moving those checks into Cloud Functions is the next hardening step.
- **Changing the split algorithm changes old bills**, because shares are recomputed rather than stored.
- A user's bank account fields are readable by any signed-in user who knows their uid.
- Avatars are initials on a fixed colour; photo upload is not planned.
- Not yet run with a real banking app scanning a generated QR, and no usability sessions yet (NFR3/NFR4).

## Khai báo dùng AI

Phần mã và tài liệu được viết với sự hỗ trợ của Claude (Anthropic). Mọi quy tắc tiền được kiểm bằng test tự động; kết quả test là bằng chứng, không phải lời của công cụ.
