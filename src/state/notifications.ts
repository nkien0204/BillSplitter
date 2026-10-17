import * as Notifications from 'expo-notifications';
import { useEffect, useRef } from 'react';
import { Platform } from 'react-native';
import type { AppSnapshot } from '@/data/AppSnapshot';

export const CHANNEL = 'debts';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: false,
    shouldSetBadge: false,
  }),
});

export async function setupNotifications(): Promise<void> {
  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(CHANNEL, {
      name: 'Khoản nợ và hoá đơn',
      description: 'Hoá đơn mới, nhắc trả, xác nhận đã nhận tiền',
      importance: Notifications.AndroidImportance.DEFAULT,
    });
  }
}

/** Xin quyền một lần. Từ chối thì tin vẫn nằm trong hộp thư ở tab Tôi. */
export async function ensurePermission(): Promise<boolean> {
  const cur = await Notifications.getPermissionsAsync();
  if (cur.granted) return true;
  if (!cur.canAskAgain) return false;
  return (await Notifications.requestPermissionsAsync()).granted;
}

export async function canPost(): Promise<boolean> {
  return (await Notifications.getPermissionsAsync()).granted;
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
        if (!ok) return;
        Notifications.scheduleNotificationAsync({
          content: {
            title: 'ChiaBill',
            body: e.message,
            data: { billId: e.billId, debtKey: e.debtKey },
          },
          trigger: Platform.OS === 'android' ? { channelId: CHANNEL } : null,
        }).catch(() => undefined);
      });
    }
  }, [userId, snapshot]);
}
