import DateTimePicker from '@react-native-community/datetimepicker';
import { Stack, router, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Platform, Pressable, Text, View } from 'react-native';
import { CATEGORIES, Money, SplitEngine, categoryLabel } from '@/domain';
import { useAction, useLedger } from '@/state/AppContext';
import {
  Button, Card, Chip, ChipRow, Field, Label, Loading, Muted, Screen, toast,
} from '@/ui/components';
import { colors } from '@/ui/theme';
import { type BillDraft, createDraft, draftFrom, referencedIds, toSpec } from '@/ui/bill/billDraft';
import { fullDate } from '@/util/format';

const MAX_AMOUNT = 999_999_999;

/** Ô tiền: chỉ nhận số, tự chèn dấu chấm hàng nghìn. */
function MoneyField({ value, onChange, ...rest }: { value: number; onChange: (v: number) => void; placeholder?: string; label?: string }) {
  return (
    <Field
      {...rest}
      keyboardType="number-pad"
      value={value > 0 ? Money.dots(value) : ''}
      onChangeText={(t) => onChange(Money.parseDigits(t, MAX_AMOUNT))}
    />
  );
}

/**
 * Tạo / sửa hoá đơn. Tổng tiền chia đều cho những người tham gia. Bảng xem trước tính lại bằng SplitEngine
 * sau mỗi thao tác, nên người trả thấy phần từng người trước khi lưu. Không cho lưu khi tổng bằng 0 hoặc chưa chọn ai.
 *
 * Hai danh sách thành viên: `members` là người hiện tại (hiện thành chip), `order` là mọi người từng ở nhóm
 * (thứ tự chia). Sửa hoá đơn cũ có người đã rời thì người đó vẫn hiện để phần của họ không mất.
 */
export default function BillEdit() {
  const { groupId, billId } = useLocalSearchParams<{ groupId?: string; billId?: string }>();
  const app = useLedger();
  const run = useAction();
  const [draft, setDraft] = useState<BillDraft | null>(null);
  const [pickDate, setPickDate] = useState(false);
  const [busy, setBusy] = useState(false);
  const [blocked, setBlocked] = useState<string | null>(null);

  useEffect(() => {
    if (!app || draft || blocked) return;
    const { snapshot: s, userId: me } = app;
    if (billId) {
      const bill = s.ledger().bill(billId);
      const rec = s.billRecord(billId);
      if (!bill || !rec) return void router.back();
      if (me !== rec.createdBy && me !== rec.payerId) {
        setBlocked('Chỉ người tạo hoặc người trả được sửa hoá đơn này.');
        return;
      }
      setDraft(draftFrom(bill));
    } else if (groupId) {
      setDraft(createDraft(groupId, me, s.membersOf(groupId)));
    }
  }, [app, draft, billId, groupId, blocked]);

  useEffect(() => {
    if (blocked) {
      toast(blocked);
      router.back();
    }
  }, [blocked]);

  const ctx = useMemo(() => {
    if (!app || !draft) return null;
    const s = app.snapshot;
    const g = s.group(draft.groupId);
    if (!g) return null;
    const order = s.allMembersOf(g.id);
    const active = s.membersOf(g.id);
    const referenced = referencedIds(draft);
    return { g, order, members: order.filter((id) => active.includes(id) || referenced.includes(id)) };
  }, [app, draft]);

  if (!app || !draft || !ctx) return <Loading />;
  const { snapshot: s, userId: me, repo } = app;
  const { g, order, members } = ctx;
  const set = (patch: Partial<BillDraft>) => setDraft({ ...draft, ...patch });
  const name = (id: string) => s.name(id) + (id === me ? ' (bạn)' : '');
  const toggle = (list: string[], id: string) => (list.includes(id) ? list.filter((x) => x !== id) : [...list, id]);

  const spec = toSpec(draft);
  const result = SplitEngine.split(spec, order);
  const invalid = SplitEngine.check(spec, order);
  const canSave = invalid == null && result.isSavable();

  let previewCheck: { text: string; ok: boolean };
  if (invalid != null) previewCheck = { text: invalid, ok: false };
  else if (result.total <= 0) previewCheck = { text: 'Nhập số tiền để xem phần từng người', ok: false };
  else previewCheck = { text: `✓ Tổng các phần ${Money.format(result.sumOfShares())} khớp tổng hoá đơn`, ok: true };

  async function save() {
    const d = draft as BillDraft;
    setBusy(true);
    let id: string | null = null;
    const ok = await run(async () => {
      id = await repo.saveBill({ id: d.id, groupId: d.groupId, title: d.title, spec: toSpec(d), category: d.category, date: d.date }, me);
    });
    setBusy(false);
    if (!ok || !id) return;
    toast(d.id == null ? 'Đã lưu và báo cho những người cần trả.' : 'Đã cập nhật hoá đơn.');
    if (d.id == null) router.replace({ pathname: '/bill/[id]', params: { id } });
    else router.back();
  }

  return (
    <Screen>
      <Stack.Screen options={{ title: draft.id == null ? 'Hoá đơn mới' : 'Sửa hoá đơn', headerBackTitle: g.name }} />
      <Field label="Tiêu đề" placeholder="vd Lẩu gà lá é" value={draft.title} onChangeText={(title) => set({ title })} />

      <Label>Danh mục</Label>
      <ChipRow>
        {CATEGORIES.map((c) => (
          <Chip key={c} text={categoryLabel(c)} selected={draft.category === c} onPress={() => set({ category: c })} />
        ))}
      </ChipRow>

      <Label>Ngày chi</Label>
      <Pressable onPress={() => setPickDate(true)}>
        <Card><Text>Ngày {fullDate(draft.date)}</Text></Card>
      </Pressable>
      {pickDate && (
        <DateTimePicker
          value={new Date(draft.date)}
          mode="date"
          maximumDate={new Date()}
          onChange={(e, picked) => {
            setPickDate(Platform.OS === 'ios');
            if (e.type === 'set' && picked) set({ date: Math.min(picked.getTime(), Date.now()) });
          }}
        />
      )}

      <Label>Ai trả</Label>
      <ChipRow>
        {members.map((id) => (
          <Chip key={id} text={name(id)} selected={draft.payerId === id} onPress={() => set({ payerId: id })} />
        ))}
      </ChipRow>

      <MoneyField label="Tổng tiền (VND)" placeholder="0" value={draft.subtotal} onChange={(subtotal) => set({ subtotal })} />

      <Label>Người tham gia (chia đều)</Label>
      <ChipRow>
        {members.map((id) => (
          <Chip key={id} text={name(id)} selected={draft.participants.includes(id)} onPress={() => set({ participants: toggle(draft.participants, id) })} />
        ))}
      </ChipRow>

      <Card style={{ backgroundColor: colors.ink, gap: 6 }}>
        <Text style={{ color: colors.onDarkMuted, fontSize: 12 }}>Xem trước</Text>
        <Text style={{ color: '#fff', fontSize: 24, fontWeight: '800' }}>{Money.format(result.total)}</Text>
        {result.lines.map((l) => (
          <View key={l.memberId} style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
            <Text style={{ color: '#fff' }}>{name(l.memberId)}{l.memberId === draft.payerId ? ' · người trả' : ''}</Text>
            <Text style={{ color: '#fff' }}>{Money.format(l.amount)}</Text>
          </View>
        ))}
        <Text style={{ color: previewCheck.ok ? colors.brandOnDark : colors.oweOnDark }}>{previewCheck.text}</Text>
      </Card>

      <Button title="Lưu hoá đơn" onPress={save} disabled={!canSave || busy} />
    </Screen>
  );
}
