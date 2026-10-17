import AsyncStorage from '@react-native-async-storage/async-storage';
import { getApp, getApps, initializeApp } from 'firebase/app';
import { connectAuthEmulator, getAuth, getReactNativePersistence, initializeAuth } from 'firebase/auth';
import { connectFirestoreEmulator, initializeFirestore } from 'firebase/firestore';
import { FirebaseAuthRepository } from './FirebaseAuthRepository';
import { FirebaseLedgerRepository } from './FirebaseLedgerRepository';

/**
 * Cấu hình Firebase lấy từ biến môi trường EXPO_PUBLIC_* (xem .env.example).
 * Các giá trị này không phải bí mật: bảo mật nằm ở firestore.rules.
 */
const config = {
  apiKey: process.env.EXPO_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.EXPO_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.EXPO_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: process.env.EXPO_PUBLIC_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.EXPO_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.EXPO_PUBLIC_FIREBASE_APP_ID,
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
