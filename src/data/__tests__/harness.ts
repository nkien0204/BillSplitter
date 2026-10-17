import { type FirebaseApp, deleteApp, initializeApp } from 'firebase/app';
import { connectAuthEmulator, getAuth } from 'firebase/auth';
import { type Firestore, connectFirestoreEmulator, getFirestore, setLogLevel, terminate } from 'firebase/firestore';
import type { AppSnapshot } from '../AppSnapshot';
import type { AuthUser } from '../AuthRepository';
import { FirebaseAuthRepository } from '../FirebaseAuthRepository';
import { FirebaseLedgerRepository } from '../FirebaseLedgerRepository';
import type { Unsubscribe } from '../LedgerRepository';

// Các lần ghi bị rules từ chối là chủ đích của test; đừng in cảnh báo.
setLogLevel('silent');
const HOST = process.env.EMULATOR_HOST ?? '127.0.0.1';
let counter = 0;

/** Một người dùng = một app Firebase riêng, giống một điện thoại riêng. */
export class Device {
  readonly app: FirebaseApp;
  readonly db: Firestore;
  readonly auth: FirebaseAuthRepository;
  readonly ledger: FirebaseLedgerRepository;
  user!: AuthUser;
  snap: AppSnapshot | null = null;
  private unsub: Unsubscribe | null = null;
  private waiters: { test: (s: AppSnapshot) => boolean; resolve: (s: AppSnapshot) => void }[] = [];

  constructor() {
    this.app = initializeApp({ projectId: 'demo-chiabill', apiKey: 'fake-key' }, 'device-' + counter++);
    const a = getAuth(this.app);
    connectAuthEmulator(a, `http://${HOST}:9099`, { disableWarnings: true });
    this.db = getFirestore(this.app);
    connectFirestoreEmulator(this.db, HOST, 8080);
    this.auth = new FirebaseAuthRepository(a, this.db);
    this.ledger = new FirebaseLedgerRepository(this.db);
  }

  get id(): string {
    return this.user.id;
  }

  async signUp(name: string, phone = randomPhone(), password = 'matkhau123'): Promise<this> {
    this.user = await this.auth.register(name, phone, password);
    this.watch();
    return this;
  }

  watch(): void {
    this.unsub?.();
    this.unsub = this.ledger.subscribe(
      this.user.id,
      (s) => {
        this.snap = s;
        this.waiters = this.waiters.filter((w) => {
          if (!w.test(s)) return true;
          w.resolve(s);
          return false;
        });
      },
      (e) => {
        console.error('LISTENER ERROR', e);
      },
    );
  }

  /** Chờ tới khi snapshot thoả điều kiện (snapshot đến từ listener thật, gồm cả ghi chưa lên server). */
  until(test: (s: AppSnapshot) => boolean, label = 'điều kiện'): Promise<AppSnapshot> {
    if (this.snap && test(this.snap)) return Promise.resolve(this.snap);
    return new Promise((resolve, reject) => {
      const t = setTimeout(() => reject(new Error(`Hết giờ chờ ${label}`)), 15000);
      this.waiters.push({
        test,
        resolve: (s) => {
          clearTimeout(t);
          resolve(s);
        },
      });
    });
  }

  async close(): Promise<void> {
    this.unsub?.();
    await terminate(this.db);
    await deleteApp(this.app);
  }
}

export function randomPhone(): string {
  const tail = String(Math.floor(Math.random() * 1e8)).padStart(8, '0');
  return '09' + tail;
}

/** Bắt lỗi và trả message, để assert thông điệp tiếng Việt. */
export async function errorOf(p: Promise<unknown>): Promise<{ name: string; message: string; code?: string } | null> {
  try {
    await p;
    return null;
  } catch (e) {
    const err = e as { name?: string; message?: string; code?: string };
    return { name: err.name ?? 'Error', message: err.message ?? '', code: err.code };
  }
}
