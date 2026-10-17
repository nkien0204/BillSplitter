import { Stack, router, useLocalSearchParams } from 'expo-router';
import { useEffect } from 'react';
import { Text, View } from 'react-native';
import { Money, SplitEngine, categoryLabel, debtKeyOf } from '@/domain';
import { useAction, useLedger } from '@/state/AppContext';
import { Avatar, Button, Card, Label, Loading, Muted, type RowData, Rows, Screen, confirm } from '@/ui/components';
import { PILL_NEUTRAL, colors, statusColors, statusLabel } from '@/ui/theme';
import { day } from '@/util/format';

export default function BillDetail() {
  const { id: billId } = useLocalSearchParams<{ id: string }>();
  const app = useLedger();
  const run = useAction();
  const gone = !!app && (!app.snapshot.ledger().bill(billId) || !app.snapshot.billRecord(billId));
  useEffect(() => {
    if (gone && router.canGoBack()) router.back();
  }, [gone]);
  if (!app || gone) return <Loading />;
  const { snapshot: s, userId: me, repo } = app;
  const bill = s.ledger().bill(billId)!;
  const be = s.billRecord(billId)!;
  const g = s.group(bill.groupId);
  const spec = bill.spec;
  const r = s.ledger().splitOf(billId)!;

  const calc: string[] = [`Tạm tính ${Money.format(r.subtotal)}`];
  calc[0] +=
    spec.mode === 'ITEMIZED' ? ` · chia theo ${spec.items.length} món`
    : spec.mode === 'CUSTOM' ? ' · mỗi người một số tiền'
    : spec.mode === 'PERCENT' ? ' · chia theo phần trăm'
    : ` · chia đều ${spec.participants.length} người`;
  if (r.vat > 0) calc.push(`VAT ${spec.vatPercent}%: ${Money.format(r.vat)}`);
  if (r.service > 0) calc.push(`Phí phục vụ ${spec.servicePercent}%: ${Money.format(r.service)}`);
  calc.push(spec.rounding > 1 ? 'Người nợ làm tròn tới 1.000đ, phần lẻ dồn cho người trả' : 'Làm tròn tới 1đ');

  const rows: RowData[] = r.lines.map((l) => {
    const id = l.memberId;
    let sub = '';
    const pct = spec.shares.get(id);
    if (spec.mode === 'PERCENT' && pct != null) sub += SplitEngine.formatPercent(pct) + ' · ';
    sub += 'gốc ' + Money.dots(l.base);
    if (l.fee !== 0) sub += ' · phí ' + Money.dots(l.fee);
    if (l.roundingAdj !== 0) sub += ' · làm tròn ' + (l.roundingAdj > 0 ? '+' : '') + Money.dots(l.roundingAdj);
    const row: RowData = {
      key: id,
      avatarName: s.name(id),
      colorIndex: s.colorIndex(id),
      title: s.name(id) + (id === me ? ' (bạn)' : ''),
      subtitle: sub,
      amount: Money.format(l.amount),
    };
    if (id === bill.payerId) return { ...row, pill: 'Người trả', pillColors: PILL_NEUTRAL };
    const d = s.ledger().debt(debtKeyOf(billId, id));
    if (!d) return row;
    return {
      ...row,
      pill: statusLabel(d.status),
      pillColors: statusColors(d.status),
      onPress: () => router.push({ pathname: '/debt/[key]', params: { key: d.key } }),
    };
  });

  const owner = me === be.createdBy || me === be.payerId;
  return (
    <Screen>
      <Stack.Screen options={{ title: bill.title }} />
      <Muted>{`${g?.name ?? ''} · ${day(bill.createdAt)} · ${categoryLabel(bill.category)}`}</Muted>
      <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <Avatar name={s.name(bill.payerId)} colorIndex={s.colorIndex(bill.payerId)} />
        <View style={{ flex: 1 }}>
          <Muted>{(bill.payerId === me ? 'Bạn' : s.name(bill.payerId)) + ' đã trả'}</Muted>
          <Text style={{ fontSize: 24, fontWeight: '800' }}>{Money.format(r.total)}</Text>
        </View>
      </Card>
      <Muted>{calc.join('\n')}</Muted>

      <Label>Phần từng người</Label>
      <Rows rows={rows} />
      <Text style={{ color: r.isBalanced() ? colors.brand : colors.owe }}>
        {(r.isBalanced() ? '✓ ' : '✗ ') +
          `Tổng các phần ${Money.format(r.sumOfShares())}` +
          (r.isBalanced() ? ' = tổng hoá đơn' : ` ≠ ${Money.format(r.total)}`)}
      </Text>

      {owner ? (
        <View style={{ gap: 8, marginTop: 8 }}>
          <Muted>Sửa số tiền sẽ đưa các khoản chưa xác nhận về “Chưa trả” và báo lại cho người nợ.</Muted>
          <Button
            title="Sửa hoá đơn"
            onPress={() => router.push({ pathname: '/bill/edit', params: { groupId: bill.groupId, billId } })}
          />
          <Button
            kind="danger"
            title="Xoá hoá đơn"
            onPress={() =>
              confirm('Xoá hoá đơn?', 'Mọi khoản nợ của hoá đơn này sẽ biến mất.', 'Xoá', async () => {
                const ok = await run(() => repo.deleteBill(billId, me));
                if (ok && router.canGoBack()) router.back();
              })
            }
          />
        </View>
      ) : (
        <Muted>{`Chỉ ${s.name(be.payerId)} (người trả) sửa được hoá đơn này.`}</Muted>
      )}
    </Screen>
  );
}
