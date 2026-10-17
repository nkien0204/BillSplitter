import {
  Bill,
  BillItem,
  BillSpec,
  PaymentTarget,
  parseCategory,
  parseSplitMode,
} from '@/domain';
import { type BillRecord, type UserRecord, hasPaymentProfile } from './records';

const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));

/** Dữ liệu đọc từ DB vẫn được kiểm trước khi thành model miền. Ném lỗi nếu bản ghi hỏng. */
export function toBill(r: BillRecord): Bill {
  const mode = parseSplitMode(r.mode);
  const items = (r.items ?? []).map((it) => new BillItem(it.name, Math.max(0, it.price), it.consumers ?? []));
  const shares = new Map<string, number>();
  for (const [id, v] of Object.entries(r.shares ?? {})) if (typeof v === 'number' && v > 0) shares.set(id, v);
  const spec = new BillSpec({
    payerId: r.payerId,
    mode,
    subtotal: Math.max(0, r.subtotal ?? 0),
    participants: r.participants ?? [],
    items,
    vatPercent: clamp(r.vatPercent ?? 0, 0, 100),
    servicePercent: clamp(r.servicePercent ?? 0, 0, 100),
    rounding: r.rounding >= 1 ? r.rounding : 1,
    shares,
  });
  return new Bill(r.id, r.groupId, r.title, r.createdAt, r.createdBy, spec, parseCategory(r.category));
}

export function toTarget(u: UserRecord | null | undefined): PaymentTarget | null {
  if (!u || !hasPaymentProfile(u)) return null;
  if (!PaymentTarget.isValidBin(u.bankBin) || !PaymentTarget.isValidAccount(u.accountNo)) return null;
  return new PaymentTarget(u.bankBin as string, u.accountNo as string, u.accountName);
}

/** Màu avatar cố định theo id, thay cho "số người dùng mod 6" của bản Room (không đếm được toàn bộ user trên Firestore). */
export const PALETTE_SIZE = 6;

export function colorIndexFor(id: string): number {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) | 0;
  return Math.abs(h) % PALETTE_SIZE;
}
