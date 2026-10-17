import { router } from 'expo-router';
import { type Debt, Money, isOpen } from '@/domain';
import { useLedger } from '@/state/AppContext';
import type { AppSnapshot } from '@/data/AppSnapshot';
import { Label, Loading, type RowData, Rows, Screen } from '@/ui/components';
import { colors, statusColors, statusLabel } from '@/ui/theme';

export default function Debts() {
  const app = useLedger();
  if (!app) return <Loading />;
  const { snapshot: s, userId: me } = app;
  const mine: Debt[] = [];
  const theirs: Debt[] = [];
  for (const d of s.ledger().debts()) {
    if (d.debtorId === me) mine.push(d);
    else if (d.creditorId === me) theirs.push(d);
  }
  // Khoản còn mở lên trước, mới trước cũ.
  const order = (x: Debt, y: Debt) => {
    const open = Number(!isOpen(x.status)) - Number(!isOpen(y.status));
    if (open !== 0) return open;
    return (s.ledger().bill(y.billId)?.createdAt ?? 0) - (s.ledger().bill(x.billId)?.createdAt ?? 0);
  };
  mine.sort(order);
  theirs.sort(order);
  return (
    <Screen>
      <Label>Bạn cần trả</Label>
      <Rows rows={rows(s, mine, true)} empty="Bạn không nợ ai." />
      <Label>Người khác nợ bạn</Label>
      <Rows rows={rows(s, theirs, false)} empty="Chưa ai nợ bạn." />
    </Screen>
  );
}

function rows(s: AppSnapshot, debts: Debt[], iOwe: boolean): RowData[] {
  return debts.map((d) => {
    const other = iOwe ? d.creditorId : d.debtorId;
    const u = s.user(other);
    const bill = s.ledger().bill(d.billId);
    const g = s.group(d.groupId);
    return {
      key: d.key,
      avatarName: s.name(other),
      colorIndex: s.colorIndex(other),
      title: iOwe ? `Trả ${s.name(other)}` : `${s.name(other)}${u?.guest ? ' (khách)' : ''} nợ bạn`,
      subtitle: `${bill?.title ?? ''} · ${g?.name ?? ''}`,
      amount: Money.format(d.amount),
      amountColor: isOpen(d.status) ? (iOwe ? colors.owe : colors.brand) : colors.muted,
      pill: statusLabel(d.status),
      pillColors: statusColors(d.status),
      onPress: () => router.push({ pathname: '/debt/[key]', params: { key: d.key } }),
    };
  });
}
