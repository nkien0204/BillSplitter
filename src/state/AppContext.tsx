import { type ReactNode, createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { AppSnapshot } from '@/data/AppSnapshot';
import { authRepository, ledgerRepository } from '@/data/firebase';
import type { AuthRepository } from '@/data/AuthRepository';
import type { LedgerRepository } from '@/data/LedgerRepository';

interface AppState {
  /** false cho tới khi Firebase khôi phục xong phiên đăng nhập. */
  authReady: boolean;
  /** Tài khoản đang dùng; null nếu chưa đăng nhập. */
  userId: string | null;
  /** null cho tới khi mọi nguồn dữ liệu trả kết quả đầu tiên. */
  snapshot: AppSnapshot | null;
  syncError: string | null;
  repo: LedgerRepository;
  auth: AuthRepository;
}

const Ctx = createContext<AppState | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [authReady, setAuthReady] = useState(false);
  const [userId, setUserId] = useState<string | null>(null);
  const [snapshot, setSnapshot] = useState<AppSnapshot | null>(null);
  const [syncError, setSyncError] = useState<string | null>(null);

  useEffect(
    () =>
      authRepository.onAuthStateChanged((id) => {
        setUserId(id);
        setSnapshot(null);
        setAuthReady(true);
      }),
    [],
  );

  useEffect(() => {
    if (!userId) return;
    setSyncError(null);
    return ledgerRepository.subscribe(userId, setSnapshot, (e) => setSyncError(e.message));
  }, [userId]);

  const value = useMemo<AppState>(
    () => ({ authReady, userId, snapshot, syncError, repo: ledgerRepository, auth: authRepository }),
    [authReady, userId, snapshot, syncError],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useApp(): AppState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useApp ngoài AppProvider');
  return v;
}

/** Màn đã đăng nhập: userId và snapshot chắc chắn có (null trong lúc đang tải). */
export function useLedger(): { userId: string; snapshot: AppSnapshot; repo: LedgerRepository } | null {
  const { userId, snapshot, repo } = useApp();
  if (!userId || !snapshot) return null;
  return { userId, snapshot, repo };
}

/** Chạy một thao tác ghi, hiện lỗi nghiệp vụ bằng toast. Trả true nếu thành công. */
export function useAction() {
  return useCallback(async (fn: () => Promise<unknown>, onError?: (message: string) => void): Promise<boolean> => {
    try {
      await fn();
      return true;
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      if (onError) onError(message);
      else (await import('@/ui/components')).toast(message);
      return false;
    }
  }, []);
}
