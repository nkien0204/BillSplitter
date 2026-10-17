import { Bill, BillItem, BillSpec, type Category, type SplitMode } from '@/domain';

export interface DraftItem {
  name: string;
  price: number;
  who: string[];
}

/** Trạng thái form hoá đơn đang sửa (có thể đổi). */
export interface BillDraft {
  /** null = hoá đơn mới */
  id: string | null;
  groupId: string;
  title: string;
  payerId: string;
  mode: SplitMode;
  subtotal: number;
  participants: string[];
  items: DraftItem[];
  vat: number;
  service: number;
  rounding: number;
  category: Category;
  /** Ngày chi, epoch ms. */
  date: number;
  /** Số tiền từng người ở chế độ CUSTOM (VND). */
  custom: Record<string, number>;
  /** Phần trăm từng người ở chế độ PERCENT, theo phần vạn (1% = 100). */
  percent: Record<string, number>;
}

export const emptyItem = (): DraftItem => ({ name: '', price: 0, who: [] });

export function createDraft(groupId: string, payerId: string, members: readonly string[], now = Date.now()): BillDraft {
  return {
    id: null,
    groupId,
    title: '',
    payerId,
    mode: 'EQUAL',
    subtotal: 0,
    participants: [...members],
    items: [emptyItem()],
    vat: 0,
    service: 0,
    rounding: 1000,
    category: 'AN_UONG',
    date: now,
    custom: {},
    percent: {},
  };
}

export function draftFrom(bill: Bill): BillDraft {
  const s = bill.spec;
  const d: BillDraft = {
    id: bill.id,
    groupId: bill.groupId,
    title: bill.title,
    payerId: s.payerId,
    mode: s.mode,
    subtotal: s.subtotal,
    participants: [...s.participants],
    items: s.items.map((it) => ({ name: it.name, price: it.price, who: [...it.consumers] })),
    vat: s.vatPercent,
    service: s.servicePercent,
    rounding: s.rounding,
    category: bill.category,
    date: bill.createdAt,
    custom: {},
    percent: {},
  };
  if (d.items.length === 0) d.items.push(emptyItem());
  if (d.mode === 'CUSTOM') d.custom = Object.fromEntries(s.shares);
  if (d.mode === 'PERCENT') d.percent = Object.fromEntries(s.shares);
  return d;
}

export function toSpec(d: BillDraft): BillSpec {
  const items = d.items.map((i) => new BillItem(i.name, i.price, i.who));
  const shares = d.mode === 'CUSTOM' ? d.custom : d.mode === 'PERCENT' ? d.percent : null;
  let sub = d.subtotal;
  if (d.mode === 'CUSTOM') sub = Object.values(d.custom).reduce((a, v) => a + (v ?? 0), 0);
  return new BillSpec({
    payerId: d.payerId,
    mode: d.mode,
    subtotal: sub,
    participants: d.participants,
    items,
    vatPercent: d.vat,
    servicePercent: d.service,
    rounding: d.rounding,
    shares,
  });
}

/** Mọi người được nhắc tới trong bản nháp (người trả, người tham gia, người dùng món, người có phần). */
export function referencedIds(d: BillDraft): string[] {
  return [d.payerId, ...d.participants, ...d.items.flatMap((i) => i.who), ...Object.keys(d.custom), ...Object.keys(d.percent)];
}

/** Chia % đều cho các thành viên làm điểm xuất phát; phần dư phần vạn cho người đầu. */
export function prefillPercent(ids: readonly string[], total: number): Record<string, number> {
  if (ids.length === 0) return {};
  const each = Math.floor(total / ids.length);
  const rest = total - each * ids.length;
  return Object.fromEntries(ids.map((id, i) => [id, each + (i === 0 ? rest : 0)]));
}
