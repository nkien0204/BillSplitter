import AsyncStorage from '@react-native-async-storage/async-storage';
import { getApp, getApps, initializeApp } from 'firebase/app';
import { connectAuthEmulator, getAuth, getReactNativePersistence, initializeAuth } from 'firebase/auth';
import { connectFirestoreEmulator, initializeFirestore } from 'firebase/firestore';
import { FirebaseAuthRepository } from './FirebaseAuthRepository';
import { FirebaseLedgerRepository } from './FirebaseLedgerRepository';

/**
 * Cấu hình Firebase. Giá trị trong .env (EXPO_PUBLIC_*) được ưu tiên; không có thì dùng mặc định bên dưới,
 * để bản APK build ở máy nào cũng nối vào cùng một Firebase project.
 *
 * Đây là cấu hình "web app" của Firebase, vốn công khai (nằm trong mọi app đã phát hành). Bảo mật nằm ở
 * firestore.rules và Firebase Auth, không phải ở việc giấu các giá trị này.
 * Phải viết process.env.EXPO_PUBLIC_X đầy đủ từng biến: Metro chỉ thay thế được dạng tĩnh này.
 */
const config = {
  apiKey: process.env.EXPO_PUBLIC_FIREBASE_API_KEY ?? 'AIzaSyBWXfBHpxrgxX4Oy_kZw2fk7MMXSkwMBDc',
  authDomain: process.env.EXPO_PUBLIC_FIREBASE_AUTH_DOMAIN ?? 'bill-splitter-eb11a.firebaseapp.com',
  projectId: process.env.EXPO_PUBLIC_FIREBASE_PROJECT_ID ?? 'bill-splitter-eb11a',
  storageBucket: process.env.EXPO_PUBLIC_FIREBASE_STORAGE_BUCKET ?? 'bill-splitter-eb11a.firebasestorage.app',
  messagingSenderId: process.env.EXPO_PUBLIC_FIREBASE_MESSAGING_SENDER_ID ?? '262554336633',
  appId: process.env.EXPO_PUBLIC_FIREBASE_APP_ID ?? '1:262554336633:web:1a5e9415f6109c5d96c3a8',
};

export const useEmulator = process.env.EXPO_PUBLIC_USE_EMULATOR === 'true';

function build() {
  const fresh = getApps().length === 0;
  const app = fresh ? initializeApp(config) : getApp();
  // initializeAuth chỉ gọi được một lần; lần sau (fast refresh) lấy lại thể hiện cũ.
  const auth = fresh ? initializeAuth(app, { persistence: getReactNativePersistence(AsyncStorage) }) : getAuth(app);
  // React Native không có WebChannel ổn định trên mọi mạng: tự chuyển sang long polling khi cần.
  const db = initializeFirestore(app, { experimentalAutoDetectLongPolling: true });
  if (fresh && useEmulator) {
    const host = process.env.EXPO_PUBLIC_EMULATOR_HOST ?? 'localhost';
    connectAuthEmulator(auth, `http://${host}:9099`, { disableWarnings: true });
    connectFirestoreEmulator(db, host, 8080);
  }
  return { app, auth, db };
}

const { auth, db } = build();

export const ledgerRepository = new FirebaseLedgerRepository(db);
export const authRepository = new FirebaseAuthRepository(auth, db);
export { auth, db };
