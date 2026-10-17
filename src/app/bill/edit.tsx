import DateTimePicker from '@react-native-community/datetimepicker';
import { Stack, router, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Platform, Pressable, Text, View } from 'react-native';
import { CATEGORIES, Money, PERCENT_TOTAL, type SplitMode, SplitEngine, categoryLabel } from '@/domain';
import { useAction, useLedger } from '@/state/AppContext';
import {
  Button, Card, Chip, ChipRow, Field, Label, Loading, Muted, Screen, Segmented, toast,
} from '@/ui/components';
import { colors } from '@/ui/theme';
import { type BillDraft, createDraft, draftFrom, emptyItem, prefillPercent, referencedIds, toSpec } from '@/ui/bill/billDraft';
import { fullDate } from '@/util/format';

const MAX_AMOUNT = 999_999_999;
const MODES: { value: SplitMode; label: string }[] = [
  { value: 'EQUAL', label: 'Đều' },
  { value: 'ITEMIZED', label: 'Theo món' },
  { value: 'CUSTOM', label: 'Số tiền' },
  { value: 'PERCENT', label: 'Theo %' },
];
const HINT: Record<SplitMode, string> = {
  EQUAL: 'Tạm tính chia đều cho những người được chọn.',
  ITEMIZED: 'Mỗi món chia đều cho những người dùng món đó.',
  CUSTOM: 'Nhập số tiền của từng người. Tạm tính = tổng các số này; VAT và phí chia theo tỉ lệ.',
  PERCENT: 'Nhập phần trăm của từng người, tổng phải đúng 100%. Để trống = 0%.',
};

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
 * Tạo / sửa hoá đơn. Bảng xem trước tính lại bằng SplitEngine sau mỗi thao tác, nên người trả thấy
 * phần từng người trước khi lưu. Không cho lưu khi còn món chưa gán, tổng bằng 0, hoặc chia theo % mà tổng chưa đúng 100%.
 *
 * Hai danh sách thành viên: `members` là người hiện tại (hiện thành chip), `order` là mọi người từng ở nhóm
 * (thứ tự chia). Sửa hoá đơn cũ có người đã rời thì người đó vẫn hiện để phần của họ không mất.
 */
export default function BillEdit() {
  const { groupId, billId } = useLocalSearchParams<{ groupId?: string; billId?: string }>();
  const app = useLedger();
  const run = useAction();
  const [draft, setDraft] = useState<BillDraft | null>(null);
  const [percentText, setPercentText] = useState<Record<string, string>>({});
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
      const d = draftFrom(bill);
      setDraft(d);
      setPercentText(Object.fromEntries(Object.entries(d.percent).map(([k, v]) => [k, SplitEngine.formatPercent(v).replace('%', '')])));
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
  else if (result.unassignedItems.length > 0) previewCheck = { text: 'Còn món chưa gán: ' + result.unassignedItems.join(', '), ok: false };
  else if (result.total <= 0) previewCheck = { text: 'Nhập số tiền để xem phần từng người', ok: false };
  else previewCheck = { text: `✓ Tổng các phần ${Money.format(result.sumOfShares())} khớp tổng hoá đơn`, ok: true };

  function setMode(mode: SplitMode) {
    const patch: Partial<BillDraft> = { mode };
    if (mode === 'PERCENT' && Object.keys(draft!.percent).length === 0) {
      const ids = members.filter((id) => s.membersOf(g.id).includes(id));
      const p = prefillPercent(ids, PERCENT_TOTAL);
      patch.percent = p;
      setPercentText(Object.fromEntries(Object.entries(p).map(([k, v]) => [k, SplitEngine.formatPercent(v).replace('%', '')])));
    }
    // Số tiền tự nhập phải giữ nguyên từng đồng: bỏ làm tròn 1.000đ
    if (mode === 'CUSTOM' && draft!.rounding !== 1) patch.rounding = 1;
    set(patch);
  }

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

      <Label>Cách chia</Label>
      <Segmented options={MODES} value={draft.mode} onChange={setMode} />
      <Muted>{HINT[draft.mode]}</Muted>

      {(draft.mode === 'EQUAL' || draft.mode === 'PERCENT') && (
        <MoneyField label="Tạm tính (VND)" placeholder="0" value={draft.subtotal} onChange={(subtotal) => set({ subtotal })} />
      )}

      {draft.mode === 'EQUAL' && (
        <>
          <Label>Người tham gia</Label>
          <ChipRow>
            {members.map((id) => (
              <Chip key={id} text={name(id)} selected={draft.participants.includes(id)} onPress={() => set({ participants: toggle(draft.participants, id) })} />
            ))}
          </ChipRow>
        </>
      )}

      {draft.mode === 'ITEMIZED' && (
        <>
          <Label>Các món</Label>
          {draft.items.map((it, idx) => (
            <Card key={idx} style={{ gap: 8 }}>
              <View style={{ flexDirection: 'row', gap: 8 }}>
                <View style={{ flex: 2 }}>
                  <Field placeholder="Tên món" value={it.name} onChangeText={(n) => set({ items: draft.items.map((x, j) => (j === idx ? { ...x, name: n } : x)) })} />
                </View>
                <View style={{ flex: 1.2 }}>
                  <MoneyField placeholder="Giá" value={it.price} onChange={(p) => set({ items: draft.items.map((x, j) => (j === idx ? { ...x, price: p } : x)) })} />
                </View>
                <Pressable
                  hitSlop={8}
                  onPress={() => {
                    const next = draft.items.filter((_, j) => j !== idx);
                    set({ items: next.length === 0 ? [emptyItem()] : next });
                  }}
                  style={{ justifyContent: 'center' }}
                >
                  <Text style={{ color: colors.owe, fontSize: 18 }}>✕</Text>
                </Pressable>
              </View>
              <ChipRow>
                {members.map((id) => (
                  <Chip
                    key={id}
                    text={name(id)}
                    selected={it.who.includes(id)}
                    onPress={() => set({ items: draft.items.map((x, j) => (j === idx ? { ...x, who: toggle(x.who, id) } : x)) })}
                  />
                ))}
              </ChipRow>
              {it.price > 0 && it.who.length === 0 && <Text style={{ color: colors.owe, fontSize: 12 }}>Món này chưa gán cho ai</Text>}
            </Card>
          ))}
          <Button kind="outline" title="+ Thêm món" onPress={() => set({ items: [...draft.items, emptyItem()] })} />
        </>
      )}

      {(draft.mode === 'CUSTOM' || draft.mode === 'PERCENT') && (
        <>
          <Label>{draft.mode === 'PERCENT' ? 'Phần trăm từng người' : 'Số tiền từng người'}</Label>
          {members.map((id) => (
            <View key={id} style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
              <Text style={{ flex: 1 }}>{name(id)}</Text>
              <View style={{ flex: 1 }}>
                {draft.mode === 'PERCENT' ? (
                  <Field
                    placeholder="0 %"
                    keyboardType="decimal-pad"
                    value={percentText[id] ?? ''}
                    error={SplitEngine.parsePercent(percentText[id]) < 0 ? 'Sai' : null}
                    onChangeText={(t) => {
                      setPercentText({ ...percentText, [id]: t });
                      const bp = SplitEngine.parsePercent(t);
                      const next = { ...draft.percent };
                      if (bp > 0) next[id] = bp;
                      else delete next[id];
                      set({ percent: next });
                    }}
                  />
                ) : (
                  <MoneyField
                    placeholder="0"
                    value={draft.custom[id] ?? 0}
                    onChange={(v) => {
                      const next = { ...draft.custom };
                      if (v > 0) next[id] = v;
                      else delete next[id];
                      set({ custom: next });
                    }}
                  />
                )}
              </View>
            </View>
          ))}
          {draft.mode === 'CUSTOM' ? (
            <Muted>Tạm tính {Money.format(result.subtotal)}</Muted>
          ) : (
            <Text style={{ color: sumPercent(order, draft.percent) === PERCENT_TOTAL ? colors.brand : colors.owe }}>
              Tổng {SplitEngine.formatPercent(sumPercent(order, draft.percent))} / 100%
            </Text>
          )}
        </>
      )}

      <Label>VAT</Label>
      <Segmented options={[{ value: 0, label: '0%' }, { value: 8, label: '8%' }, { value: 10, label: '10%' }]} value={draft.vat} onChange={(vat) => set({ vat })} />
      <Label>Phí phục vụ</Label>
      <Segmented options={[{ value: 0, label: '0%' }, { value: 5, label: '5%' }, { value: 10, label: '10%' }]} value={draft.service} onChange={(service) => set({ service })} />
      <Label>Làm tròn phần người nợ</Label>
      <Segmented options={[{ value: 1, label: '1đ' }, { value: 1000, label: '1.000đ' }]} value={draft.rounding} onChange={(rounding) => set({ rounding })} />

      <Card style={{ backgroundColor: colors.ink, gap: 6 }}>
        <Text style={{ color: colors.onDarkMuted, fontSize: 12 }}>Xem trước</Text>
        <Text style={{ color: '#fff', fontSize: 24, fontWeight: '800' }}>{Money.format(result.total)}</Text>
        <Text style={{ color: colors.onDarkMuted, fontSize: 12 }}>
          {`Tạm tính ${Money.format(result.subtotal)}`}
          {result.vat > 0 ? ` · VAT ${Money.format(result.vat)}` : ''}
          {result.service > 0 ? ` · phí ${Money.format(result.service)}` : ''}
        </Text>
        {result.lines.map((l) => (
          <View key={l.memberId} style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
            <Text style={{ color: '#fff' }}>{name(l.memberId)}{l.memberId === draft.payerId ? ' · người trả' : ''}</Text>
            <Text style={{ color: '#fff' }}>
              {Money.format(l.amount)}
              {l.roundingAdj !== 0 ? `  ${l.roundingAdj > 0 ? '+' : ''}${Money.dots(l.roundingAdj)}` : ''}
            </Text>
          </View>
        ))}
        <Text style={{ color: previewCheck.ok ? colors.brandOnDark : colors.oweOnDark }}>{previewCheck.text}</Text>
      </Card>

      <Button title="Lưu hoá đơn" onPress={save} disabled={!canSave || busy} />
    </Screen>
  );
}

function sumPercent(order: readonly string[], percent: Record<string, number>): number {
  return order.reduce((a, id) => a + (percent[id] ?? 0), 0);
}
