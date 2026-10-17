import Constants, { ExecutionEnvironment } from 'expo-constants';
import { useEffect, useRef } from 'react';
import { Platform } from 'react-native';
import type { AppSnapshot } from '@/data/AppSnapshot';

export const CHANNEL = 'debts';

type Notifications = typeof import('expo-notifications');

/**
 * expo-notifications nạp module native ngay khi import; Expo Go (SDK 53+) không có đủ module đó, và
 * trình nạp module của Metro báo lỗi nghiêm trọng trước khi try/catch kịp bắt. Nên trong Expo Go ta
 * không import nó: app chạy bình thường, chỉ không có thông báo hệ thống (tin vẫn nằm trong hộp thư
 * ở tab Tôi). Dev build / bản phát hành có đầy đủ.
 */
const inExpoGo = Constants.executionEnvironment === ExecutionEnvironment.StoreClient;

let cached: Notifications | null | undefined;
function load(): Notifications | null {
  if (cached !== undefined) return cached;
  if (inExpoGo) return (cached = null);
  try {
    const n = require('expo-notifications') as Notifications;
    n.setNotificationHandler({
      handleNotification: async () => ({
        shouldShowBanner: true,
        shouldShowList: true,
        shouldPlaySound: false,
        shouldSetBadge: false,
      }),
    });
    cached = n;
  } catch {
    cached = null;
  }
  return cached;
}

export async function setupNotifications(): Promise<void> {
  const n = load();
  if (n && Platform.OS === 'android') {
    await n.setNotificationChannelAsync(CHANNEL, {
      name: 'Khoản nợ và hoá đơn',
      description: 'Hoá đơn mới, nhắc trả, xác nhận đã nhận tiền',
      importance: n.AndroidImportance.DEFAULT,
    });
  }
}

/** Xin quyền một lần. Từ chối (hoặc không hỗ trợ) thì tin vẫn nằm trong hộp thư ở tab Tôi. */
export async function ensurePermission(): Promise<boolean> {
  const n = load();
  if (!n) return false;
  const cur = await n.getPermissionsAsync();
  if (cur.granted) return true;
  if (!cur.canAskAgain) return false;
  return (await n.requestPermissionsAsync()).granted;
}

export async function canPost(): Promise<boolean> {
  const n = load();
  return n ? (await n.getPermissionsAsync()).granted : false;
}

/** Bấm thông báo → gọi onTap với dữ liệu đính kèm. Trả hàm huỷ đăng ký. */
export function onNotificationTap(onTap: (data: { billId?: string | null; debtKey?: string | null }) => void): () => void {
  const n = load();
  if (!n) return () => undefined;
  const sub = n.addNotificationResponseReceivedListener((r) => {
    onTap(r.notification.request.content.data as { billId?: string | null; debtKey?: string | null });
  });
  return () => sub.remove();
}

/**
 * Hộp thư là nguồn sự thật: mỗi tin mới tới (sau lần tải đầu tiên) được hiện thành thông báo hệ thống.
 * Chạy khi app đang mở hoặc nằm nền; đẩy tới máy đã tắt hẳn cần FCM + Cloud Functions (xem README).
 */
export function useInboxNotifications(userId: string | null, snapshot: AppSnapshot | null): void {
  const seen = useRef<Set<string> | null>(null);
  useEffect(() => {
    seen.current = null;
  }, [userId]);
  useEffect(() => {
    if (!userId || !snapshot) return;
    const items = snapshot.inboxOf(userId);
    if (seen.current === null) {
      seen.current = new Set(items.map((i) => i.id)); // tin cũ không báo lại
      return;
    }
    const known = seen.current;
    for (const e of items) {
      if (known.has(e.id)) continue;
      known.add(e.id);
      canPost().then((ok) => {
        const n = load();
        if (!ok || !n) return;
        n.scheduleNotificationAsync({
          content: { title: 'ChiaBill', body: e.message, data: { billId: e.billId, debtKey: e.debtKey } },
          trigger: Platform.OS === 'android' ? { channelId: CHANNEL } : null,
        }).catch(() => undefined);
      });
    }
  }, [userId, snapshot]);
}
