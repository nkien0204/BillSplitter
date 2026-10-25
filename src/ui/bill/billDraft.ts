import { Bill, BillSpec, type Category } from '@/domain';

/** Trạng thái form hoá đơn đang sửa (có thể đổi). */
export interface BillDraft {
  /** null = hoá đơn mới */
  id: string | null;
  groupId: string;
  title: string;
  payerId: string;
  subtotal: number;
  participants: string[];
  category: Category;
  /** Ngày chi, epoch ms. */
  date: number;
}

export function createDraft(groupId: string, payerId: string, members: readonly string[], now = Date.now()): BillDraft {
  return {
    id: null,
    groupId,
    title: '',
    payerId,
    subtotal: 0,
    participants: [...members],
    category: 'AN_UONG',
    date: now,
  };
}

export function draftFrom(bill: Bill): BillDraft {
  const s = bill.spec;
  return {
    id: bill.id,
    groupId: bill.groupId,
    title: bill.title,
    payerId: s.payerId,
    subtotal: s.subtotal,
    participants: [...s.participants],
    category: bill.category,
    date: bill.createdAt,
  };
}

export function toSpec(d: BillDraft): BillSpec {
  return new BillSpec({ payerId: d.payerId, subtotal: d.subtotal, participants: d.participants });
}

/** Mọi người được nhắc tới trong bản nháp (người trả, người tham gia). */
export function referencedIds(d: BillDraft): string[] {
  return [d.payerId, ...d.participants];
}
