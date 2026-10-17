# ChiaBill — chia tiền nhóm bằng VietQR

Mobile app for splitting shared expenses with **zero money drift**, settling debts by scanning a **VietQR code pre-filled with the exact amount**.

**v0.4.0 — React Native (Expo, TypeScript) + Firebase (Auth + Firestore).** This is a port of the Java/Android + Node/Express version (v0.3.0, branch `main`). The business logic is the same; the tests that guard it are the same cases with the same random seeds.

## Stack

| Layer | Technology |
|-------|-----------|
| Mobile | React Native 0.88 · Expo SDK 58 · Expo Router · TypeScript (strict) |
| Backend / DB | Firebase Authentication (phone number + password) · Cloud Firestore · Security Rules |
| QR | `react-native-qrcode-svg` (generate) · `expo-camera` (scan, decode from image) |
| Tests | Jest (`jest-expo`) · Firebase Emulator Suite for integration tests |

There is **no server to run**. Everything the Express backend did is either the client-side repository (`src/data`) or Firestore Security Rules (`firestore.rules`).

## Getting started

### Prerequisites

- Node.js 20+ and npm
- A Firebase project (Spark/free plan is enough) — or just the emulators, see below
- Expo Go **or** a dev build (`npx expo run:android` / `run:ios`) on a device/simulator

### 1. Install

```bash
npm install
```

### 2. Create the Firebase project

1. [Firebase console](https://console.firebase.google.com) → *Add project*.
2. *Build → Authentication → Get started → Sign-in method → Email/Password → Enable.*
   (The app logs in with phone number + password; it maps the phone number to a synthetic email `<phone>@phone.chiabill.app`, so no SMS is sent and no billing is needed.)
3. *Build → Firestore Database → Create database* (production mode).
4. *Project settings → Your apps → Web app* → copy the config into `.env`:

```bash
cp .env.example .env   # then fill in the EXPO_PUBLIC_FIREBASE_* values
```

5. Deploy rules and the index (the inbox query needs the composite index in `firestore.indexes.json`):

```bash
npx firebase login
npx firebase use <your-project-id>        # or edit .firebaserc
npx firebase deploy --only firestore
```

### 3. Run the app

```bash
npx expo start
```

Open it in Expo Go, or build a dev client for full camera / media-library / notification behaviour.

### Run against the emulators instead (no cloud project)

Needs Java 11+ for the Firestore emulator.

```bash
npm run emulators            # Auth :9099, Firestore :8080
# in .env: EXPO_PUBLIC_USE_EMULATOR=true
#   EXPO_PUBLIC_EMULATOR_HOST=10.0.2.2   (Android emulator)  |  localhost (iOS simulator)  |  your LAN IP (real phone)
npx expo start
```

### First run: create two accounts

Register two accounts (two devices, or sign out/in). Account A: *Tạo nhóm* → search B by exact phone number → add. A adds a bill → B sees it in *Khoản nợ* and gets a notification. B pays by scanning the QR (A must first set a receiving account in *Tôi → Khai QR nhận tiền*), taps *Tôi đã chuyển*; A taps *Đã nhận tiền*.

## Tests

```bash
npm test                 # unit tests: domain (53) + UI draft logic
npm run test:int         # integration tests against the Firebase emulators (25): repository + Security Rules
npm run typecheck
```

`npm test` runs the ported `:domain` suite: allocator, split engine (10 000 random bills, never off by 1đ), ledger/debt simplifier (5 000 random nets), VietQR codec (5 000 round-trips), phone and invite codes. The random tests use a re-implementation of `java.util.Random`, so they generate the *same* cases as the JUnit versions did.

`npm run test:int` starts the Auth + Firestore emulators with the real `firestore.rules`, then drives the real repositories as several separate "devices": permissions, the payment state machine, leave/remove/delete rules, invite-code joins, and attempts to bypass the client by writing to Firestore directly.

## Project layout

```
src/
  domain/      Pure TypeScript business rules. NO react-native / firebase imports.
  data/        Firestore repository, auth, snapshot builder (the only code that knows Firebase)
  state/       React context (auth + live snapshot), local notifications
  ui/          Theme, shared components, bill draft logic
  app/         Expo Router screens (file = route)
firestore.rules, firestore.indexes.json, firebase.json   Firebase config
```

See [architecture.md](architecture.md) for the data model and request flow, [AGENTS.md](AGENTS.md) for the rules to follow when changing code, [business-requirements.md](business-requirements.md) for FR/NFR status.

## Known limits

- **Push when the app is closed** needs FCM + a Cloud Function (Blaze plan). Today every new inbox document is turned into a local notification while the app is open or in the background; otherwise it waits in the inbox (tab *Tôi*).
- **Offline writes**: React Native has no persistent Firestore cache, so the app needs a connection to start and to write (same as v0.3.0's "writes need the network"). Realtime listeners keep the UI live while online.
- **Money rules are enforced on the client** (and in the rules where they are not about amounts). Firestore Rules cannot run `SplitEngine`, so a hacked client could, for example, leave a group while owing money. Moving those checks into Cloud Functions is the next hardening step.
- A user's bank account fields are readable by any signed-in user who knows their uid (same exposure as the old `GET /users/find`).
- Demo seeding (`DemoSeeder` in v0.3.0) was on-device only and is not ported.
- Not yet run on a physical device: scanning a generated QR with a real banking app, and the usability sessions (NFR3/NFR4).

## Khai báo dùng AI

Phần mã và tài liệu được viết với sự hỗ trợ của Claude (Anthropic). Mọi quy tắc tiền được kiểm bằng test tự động; kết quả test là bằng chứng, không phải lời của công cụ.
