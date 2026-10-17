import { Stack, router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Modal, Share, Text, View } from 'react-native';
import {
  type Bill,
  CATEGORIES,
  type Category,
  InviteCode,
  Money,
  PhoneNumber,
  type SplitResult,
  categoryLabel,
} from '@/domain';
import { useAction, useLedger } from '@/state/AppContext';
import { Button, Card, Chip, ChipRow, Field, Label, Loading, Muted, type RowData, Rows, Screen, choose, confirm, toast } from '@/ui/components';
import { PILL_NEUTRAL, PILL_OK, colors, statusColors, statusLabel } from '@/ui/theme';
import { day } from '@/util/format';

const PERIODS = ['Mọi lúc', '7 ngày qua', '30 ngày qua', 'Tháng này'];

export default function GroupDetail() {
  const { id: groupId } = useLocalSearchParams<{ id: string }>();
  const app = useLedger();
  const run = useAction();
  const leaving = useRef(false);

  // Bộ lọc lịch sử (FR8). null / 0 = không lọc.
  const [category, setCategory] = useState<Category | null>(null);
  const [member, setMember] = useState<string | null>(null);
  const [period, setPeriod] = useState(0);
  const [adding, setAdding] = useState(false);
  const [phone, setPhone] = useState('');

  const present = !!app && !!app.snapshot.group(groupId) && app.snapshot.membersOf(groupId).includes(app.userId);
  useEffect(() => {
    // Bị xoá khỏi nhóm / nhóm bị xoá khi đang xem
    if (app && !present) {
      if (!leaving.current) toast('Bạn không ở trong nhóm này.');
      if (router.canGoBack()) router.back();
    }
  }, [app, present]);

  if (!app || !present) return <Loading />;
  const { snapshot: s, userId: me, repo } = app;
  const g = s.group(groupId)!;
  const l = s.ledger();
  const members = s.membersOf(groupId);

  const net = l.owedTo(me, groupId) - l.owedBy(me, groupId);
  const all: Bill[] = [...l.billsOfGroup(groupId)].reverse(); // mới nhất lên đầu
  const matches = (bill: Bill, r: SplitResult | undefined) => {
    if (category && bill.category !== category) return false;
    if (member && bill.payerId !== member) {
      const line = r?.lineOf(member);
      if (!line || line.amount <= 0) return false;
    }
    if (period !== 0) {
      const now = Date.now();
      let from: number;
      if (period === 1) from = now - 7 * 86_400_000;
      else if (period === 2) from = now - 30 * 86_400_000;
      else {
        const c = new Date();
        from = new Date(c.getFullYear(), c.getMonth(), 1).getTime();
      }
      if (bill.createdAt < from) return false;
    }
    return true;
  };
  const list = all.filter((b) => matches(b, l.splitOf(b.id)));
  const shownTotal = list.reduce((a, b) => a + (l.splitOf(b.id)?.total ?? 0), 0);
  const filtered = category != null || member != null || period !== 0;

  const rows: RowData[] = list.map((bill) => {
    const mine = l.debtsOfBill(bill.id).find((d) => d.debtorId === me);
    const base: RowData = {
      key: bill.id,
      avatarName: s.name(bill.payerId),
      colorIndex: s.colorIndex(bill.payerId),
      title: bill.title,
      subtitle: `${day(bill.createdAt)} · ${categoryLabel(bill.category)} · ${bill.payerId === me ? 'Bạn' : s.name(bill.payerId)} trả`,
      amount: Money.format(l.splitOf(bill.id)?.total ?? 0),
      onPress: () => router.push({ pathname: '/bill/[id]', params: { id: bill.id } }),
    };
    if (bill.payerId === me) {
      const open = l.debtsOfBill(bill.id).filter((d) => d.status !== 'CONFIRMED').length;
      return { ...base, pill: open === 0 ? 'Đã thu đủ' : `Chờ ${open} người trả`, pillColors: open === 0 ? PILL_OK : PILL_NEUTRAL };
    }
    if (mine) {
      return {
        ...base,
        pill: `Phần bạn ${Money.format(mine.amount)} · ${statusLabel(mine.status).toLowerCase()}`,
        pillColors: statusColors(mine.status),
      };
    }
    return { ...base, pill: 'Bạn không tham gia', pillColors: PILL_NEUTRAL };
  });

  function pickCategory() {
    choose('Lọc theo danh mục', ['Tất cả', ...CATEGORIES.map(categoryLabel)], (i) => setCategory(i === 0 ? null : CATEGORIES[i - 1]));
  }
  function pickMember() {
    const ids = s.allMembersOf(groupId);
    choose('Hoá đơn có người này', ['Tất cả', ...ids.map((id) => s.name(id) + (id === me ? ' (bạn)' : ''))], (i) =>
      setMember(i === 0 ? null : ids[i - 1]),
    );
  }

  async function shareInvite() {
    if (!g.inviteCode) return;
    await Share.share({
      message: `Vào nhóm “${g.name}” trên ChiaBill: mở app → tab Nhóm → “Vào nhóm bằng mã” → nhập ${InviteCode.format(g.inviteCode)}`,
    });
  }

  async function lookup() {
    try {
      const u = await repo.findUserByPhone(phone);
      if (!u) return toast(`Chưa có tài khoản nào dùng số ${phone.trim()}.`);
      setAdding(false);
      setPhone('');
      confirm(
        `Thêm ${u.name}?`,
        `${u.name} · ${PhoneNumber.masked(u.phone)}\n\nNgười này sẽ thấy các hoá đơn mới của nhóm. Hoá đơn cũ không đổi.`,
        'Thêm vào nhóm',
        () => run(async () => { await repo.addMember(groupId, u.id, me); toast(`Đã thêm ${u.name} vào nhóm.`); }),
      );
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e));
    }
  }

  return (
    <Screen>
      <Stack.Screen options={{ title: g.name }} />
      <Card>
        <Muted>{net > 0 ? 'Bạn được nợ trong nhóm này' : net < 0 ? 'Bạn đang nợ trong nhóm này' : 'Số dư của bạn'}</Muted>
        <Text style={{ fontSize: 24, fontWeight: '800', color: net > 0 ? colors.brand : net < 0 ? colors.owe : colors.muted }}>
          {net === 0 ? 'Sòng phẳng' : Money.format(Math.abs(net))}
        </Text>
      </Card>
      <View style={{ flexDirection: 'row', gap: 12 }}>
        <Button style={{ flex: 1 }} title="Thêm hoá đơn" onPress={() => router.push({ pathname: '/bill/edit', params: { groupId } })} />
        <Button
          style={{ flex: 1 }}
          kind="outline"
          title="Tất toán"
          disabled={l.openDebtCount(groupId) === 0}
          onPress={() => router.push({ pathname: '/group/settle', params: { groupId } })}
        />
      </View>

      <Label>{members.length} thành viên</Label>
      <ChipRow>
        {members.map((id) => {
          const u = s.user(id);
          const label = s.name(id) + (id === me ? ' (bạn)' : u?.guest ? ' (khách)' : '');
          const removable = me === g.createdBy && id !== me;
          return (
            <Chip
              key={id}
              text={label}
              onPress={
                removable
                  ? () =>
                      confirm(
                        `Xoá ${s.name(id)} khỏi nhóm?`,
                        `Chỉ xoá được khi mọi khoản của ${s.name(id)} trong nhóm đã xác nhận xong. Hoá đơn cũ vẫn giữ nguyên phần của ${s.name(id)}.`,
                        'Xoá khỏi nhóm',
                        () => run(async () => { await repo.removeMember(groupId, id, me); toast(`Đã xoá ${s.name(id)} khỏi nhóm.`); }),
                      )
                  : undefined
              }
            />
          );
        })}
      </ChipRow>
      <Button kind="outline" title="Thêm thành viên bằng số điện thoại" onPress={() => setAdding(true)} />

      <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <View style={{ flex: 1 }}>
          <Muted>Mã mời</Muted>
          <Text style={{ fontSize: 20, fontWeight: '800', letterSpacing: 2 }}>
            {g.inviteCode ? InviteCode.format(g.inviteCode) : '—'}
          </Text>
        </View>
        <Button kind="outline" title="Chia sẻ" onPress={shareInvite} disabled={!g.inviteCode} />
      </Card>

      <Label>Lịch sử</Label>
      <ChipRow>
        <Chip text={`Danh mục: ${category ? categoryLabel(category) : 'tất cả'}`} selected={category != null} onPress={pickCategory} />
        <Chip text={`Người: ${member ? s.name(member) : 'tất cả'}`} selected={member != null} onPress={pickMember} />
        <Chip
          text={`Thời gian: ${PERIODS[period].toLowerCase()}`}
          selected={period !== 0}
          onPress={() => choose('Lọc theo thời gian', PERIODS, setPeriod)}
        />
        {filtered && (
          <Chip
            text="Bỏ lọc"
            onPress={() => {
              setCategory(null);
              setMember(null);
              setPeriod(0);
            }}
          />
        )}
      </ChipRow>
      <Muted>
        {filtered
          ? `Hiện ${list.length}/${all.length} hoá đơn · tổng ${Money.format(shownTotal)}`
          : `${all.length} hoá đơn · tổng ${Money.format(shownTotal)}`}
      </Muted>
      <Rows
        rows={rows}
        empty={all.length === 0 ? 'Nhóm chưa có hoá đơn. Ai trả trước thì bấm “Thêm hoá đơn”.' : 'Không có hoá đơn nào khớp bộ lọc.'}
      />

      <Button
        kind="outline"
        title="Rời nhóm"
        style={{ marginTop: 16 }}
        onPress={() =>
          confirm(
            'Rời nhóm?',
            'Bạn chỉ rời được khi không còn nợ ai và không còn ai nợ bạn trong nhóm. Hoá đơn cũ vẫn giữ nguyên. Muốn quay lại thì nhập mã mời.',
            'Rời nhóm',
            async () => {
              leaving.current = true; // snapshot có thể tới trước kết quả: đừng báo "không ở trong nhóm"
              const ok = await run(() => repo.leaveGroup(groupId, me));
              if (ok) toast('Đã rời nhóm.');
              else leaving.current = false;
            },
          )
        }
      />
      {me === g.createdBy && (
        <Button
          kind="danger"
          title="Xoá nhóm"
          onPress={() =>
            confirm(
              `Xoá nhóm “${g.name}”?`,
              'Toàn bộ hoá đơn và lịch sử của nhóm sẽ bị xoá vĩnh viễn, với mọi thành viên. Chỉ xoá được khi mọi khoản trong nhóm đã xác nhận xong. Không thể hoàn tác.',
              'Xoá nhóm',
              async () => {
                leaving.current = true;
                const ok = await run(() => repo.deleteGroup(groupId, me));
                if (ok) toast('Đã xoá nhóm.');
                else leaving.current = false;
              },
            )
          }
        />
      )}

      <Modal transparent animationType="fade" visible={adding} onRequestClose={() => setAdding(false)}>
        <View style={{ flex: 1, backgroundColor: '#0008', justifyContent: 'center', padding: 24 }}>
          <Card style={{ gap: 12 }}>
            <Text style={{ fontSize: 17, fontWeight: '700' }}>Thêm thành viên</Text>
            <Field placeholder="Số điện thoại, vd 0904 444 444" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
            <View style={{ flexDirection: 'row', gap: 12, justifyContent: 'flex-end' }}>
              <Button kind="text" title="Huỷ" onPress={() => setAdding(false)} />
              <Button title="Tìm" onPress={lookup} />
            </View>
          </Card>
        </View>
      </Modal>
    </Screen>
  );
}
