# Test Security Rules

Kiểm `../firestore.rules` và `../storage.rules` trên Firebase Emulator (chạy trong máy, không đụng dự án thật).

```bash
npm install -g firebase-tools   # một lần
cd rules-test
npm install
npm test
```

Cần Java 11 trở lên (Emulator chạy bằng Java). Lần đầu `firebase` tự tải Emulator về.
