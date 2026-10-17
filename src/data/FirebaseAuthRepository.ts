import {
  type Auth,
  createUserWithEmailAndPassword,
  deleteUser,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
} from 'firebase/auth';
import { type Firestore, doc, getDoc, writeBatch } from 'firebase/firestore';
import { PhoneNumber } from '@/domain';
import {
  AuthError,
  type AuthErrorCode,
  type AuthRepository,
  type AuthUser,
  type Unsubscribe,
} from './AuthRepository';
import { colorIndexFor } from './mappers';

export const MIN_PASSWORD_LENGTH = 6;

/** Firebase Auth chỉ có email/mật khẩu cho đăng nhập thường; số điện thoại đã chuẩn hoá làm phần tên. */
export function emailFor(normalizedPhone: string): string {
  return `${normalizedPhone}@phone.chiabill.app`;
}

function mapFirebaseError(e: unknown): AuthError {
  if (e instanceof AuthError) return e;
  const code = (e as { code?: string })?.code ?? '';
  const table: Record<string, AuthErrorCode> = {
    'auth/email-already-in-use': 'USER_ALREADY_EXISTS',
    'auth/invalid-credential': 'INVALID_CREDENTIALS',
    'auth/wrong-password': 'INVALID_CREDENTIALS',
    'auth/user-not-found': 'INVALID_CREDENTIALS',
    'auth/invalid-email': 'INVALID_CREDENTIALS',
    'auth/weak-password': 'WEAK_PASSWORD',
    'auth/too-many-requests': 'TOO_MANY_REQUESTS',
    'auth/network-request-failed': 'NETWORK_ERROR',
    'unavailable': 'NETWORK_ERROR',
    'permission-denied': 'USER_ALREADY_EXISTS', // phones/{số} đã có chủ
  };
  return new AuthError(table[code] ?? 'UNKNOWN_ERROR');
}

export class FirebaseAuthRepository implements AuthRepository {
  constructor(
    private readonly auth: Auth,
    private readonly db: Firestore,
    private readonly now: () => number = Date.now,
  ) {}

  onAuthStateChanged(cb: (userId: string | null) => void): Unsubscribe {
    return onAuthStateChanged(this.auth, (u) => cb(u ? u.uid : null));
  }

  async register(name: string, phone: string, password: string): Promise<AuthUser> {
    const n = (name ?? '').trim();
    if (n === '') throw new AuthError('BAD_REQUEST');
    const p = PhoneNumber.normalize(phone);
    if (p == null) throw new AuthError('INVALID_PHONE');
    if ((password ?? '').length < MIN_PASSWORD_LENGTH) throw new AuthError('WEAK_PASSWORD');

    let cred;
    try {
      cred = await createUserWithEmailAndPassword(this.auth, emailFor(p), password);
    } catch (e) {
      throw mapFirebaseError(e);
    }
    const uid = cred.user.uid;
    try {
      const b = writeBatch(this.db);
      b.set(doc(this.db, 'users', uid), {
        name: n,
        phone: p,
        guest: false,
        colorIndex: colorIndexFor(uid),
        bankBin: null,
        accountNo: null,
        accountName: null,
        qrUpdatedAt: 0,
        createdAt: this.now(),
      });
      b.set(doc(this.db, 'phones', p), { uid });
      await b.commit();
    } catch (e) {
      // Không để lại tài khoản đăng nhập được mà không có hồ sơ.
      await deleteUser(cred.user).catch(() => undefined);
      throw mapFirebaseError(e);
    }
    return { id: uid, name: n, phone: p };
  }

  async login(phone: string, password: string): Promise<AuthUser> {
    const p = PhoneNumber.normalize(phone);
    if (p == null) throw new AuthError('INVALID_CREDENTIALS');
    let uid: string;
    try {
      uid = (await signInWithEmailAndPassword(this.auth, emailFor(p), password)).user.uid;
    } catch (e) {
      throw mapFirebaseError(e);
    }
    const snap = await getDoc(doc(this.db, 'users', uid));
    return { id: uid, name: snap.exists() ? String(snap.data().name ?? '') : '', phone: p };
  }

  async logout(): Promise<void> {
    await signOut(this.auth);
  }
}
