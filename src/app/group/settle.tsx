import { Stack, useLocalSearchParams } from 'expo-router';
import { Money } from '@/domain';
import { useLedger } from '@/state/AppContext';
import { Label, Loading, Muted, type RowData, Rows, Screen } from '@/ui/components';
import { colors } from '@/ui/theme';
import { Text } from 'react-native';

/** Đề xuất tất toán: gộp mọi khoản chưa trả trong nhóm thành ít lần chuyển nhất (tham lam). */
export default function Settle() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const app = useLedger();
  if (!app) return <Loading />;
  const { snapshot: s, userId: me } = app;
  const g = s.group(groupId);
  if (!g) return <Muted>Nhóm không còn.</Muted>;
  const l = s.ledger();
  const plan = l.settlement(groupId);
  const open = l.openDebtCount(groupId);

  const nets: RowData[] = [];
  for (const [id, v] of l.netBalances(groupId)) {
    // Người đã rời nhóm mà không còn khoản nào thì không cần hiện
    if (v === 0 && !s.membersOf(groupId).includes(id)) continue;
    nets.push({
      key: id,
      avatarName: s.name(id),
      colorIndex: s.colorIndex(id),
      title: s.name(id) + (id === me ? ' (bạn)' : ''),
      subtitle: v > 0 ? 'Được nhận lại' : v < 0 ? 'Cần trả thêm' : 'Sòng phẳng',
      amount: Money.formatSigned(v),
      amountColor: v > 0 ? colors.brand : v < 0 ? colors.owe : colors.muted,
    });
  }
  const transfers: RowData[] = plan.map((t, i) => ({
    key: String(i),
    avatarName: s.name(t.fromId),
    colorIndex: s.colorIndex(t.fromId),
    title: `${s.name(t.fromId)} → ${s.name(t.toId)}`,
    amount: Money.format(t.amount),
  }));

  return (
    <Screen>
      <Stack.Screen options={{ title: 'Tất toán', headerBackTitle: g.name }} />
      <Text style={{ fontSize: 18, fontWeight: '700' }}>
        {open === 0 ? 'Không còn khoản nào' : `${open} khoản → ${plan.length} lần chuyển`}
      </Text>
      <Label>Số dư từng người</Label>
      <Rows rows={nets} />
      <Label>Đề xuất chuyển khoản</Label>
      <Rows rows={transfers} empty="Không cần chuyển khoản nào." />
    </Screen>
  );
}
