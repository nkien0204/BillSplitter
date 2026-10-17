import { Tabs } from 'expo-router';
import { type ColorValue, Text } from 'react-native';
import { useApp } from '@/state/AppContext';
import { colors } from '@/ui/theme';

const icon = (glyph: string) => ({ color }: { color: ColorValue }) => <Text style={{ color, fontSize: 20 }}>{glyph}</Text>;

export default function TabsLayout() {
  const { snapshot, userId } = useApp();
  // Huy hiệu: số khoản mình còn phải trả (chưa trả hoặc bị báo chưa nhận được)
  const open =
    snapshot && userId
      ? snapshot.ledger().debts().filter((d) => d.debtorId === userId && (d.status === 'PENDING' || d.status === 'DISPUTED')).length
      : 0;
  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: colors.brand },
        headerTintColor: '#fff',
        tabBarActiveTintColor: colors.brand,
      }}
    >
      <Tabs.Screen name="index" options={{ title: 'Nhóm', tabBarIcon: icon('👥') }} />
      <Tabs.Screen
        name="debts"
        options={{ title: 'Khoản nợ', tabBarIcon: icon('💸'), tabBarBadge: open > 0 ? open : undefined }}
      />
      <Tabs.Screen name="profile" options={{ title: 'Tôi', tabBarIcon: icon('🙂') }} />
    </Tabs>
  );
}
