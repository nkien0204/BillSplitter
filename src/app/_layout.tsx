import { Stack, router } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AppProvider, useApp } from '@/state/AppContext';
import { onNotificationTap, setupNotifications, useInboxNotifications } from '@/state/notifications';
import { Loading } from '@/ui/components';
import { colors } from '@/ui/theme';

function Gate() {
  const { authReady, userId, snapshot } = useApp();
  useInboxNotifications(userId, snapshot);

  useEffect(() => {
    setupNotifications().catch(() => undefined);
    // Bấm thông báo: mở đúng khoản nợ / hoá đơn.
    return onNotificationTap((d) => {
      if (d.debtKey) router.push({ pathname: '/debt/[key]', params: { key: d.debtKey } });
      else if (d.billId) router.push({ pathname: '/bill/[id]', params: { id: d.billId } });
    });
  }, []);

  if (!authReady) return <Loading />;
  return (
    <Stack
      screenOptions={{
        headerStyle: { backgroundColor: colors.brand },
        headerTintColor: '#fff',
        headerTitleStyle: { fontWeight: '700' },
        contentStyle: { backgroundColor: colors.paper },
      }}
    >
      <Stack.Protected guard={!userId}>
        <Stack.Screen name="login" options={{ headerShown: false }} />
      </Stack.Protected>
      <Stack.Protected guard={!!userId}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="create-group" options={{ title: 'Nhóm mới' }} />
        <Stack.Screen name="group/[id]" options={{ title: 'Nhóm' }} />
        <Stack.Screen name="group/settle" options={{ title: 'Tất toán' }} />
        <Stack.Screen name="bill/[id]" options={{ title: 'Hoá đơn' }} />
        <Stack.Screen name="bill/edit" options={{ title: 'Hoá đơn' }} />
        <Stack.Screen name="debt/[key]" options={{ title: 'Khoản nợ' }} />
        <Stack.Screen name="qr-setup" options={{ title: 'QR nhận tiền' }} />
      </Stack.Protected>
    </Stack>
  );
}

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <AppProvider>
        <StatusBar style="light" />
        <Gate />
      </AppProvider>
    </SafeAreaProvider>
  );
}
