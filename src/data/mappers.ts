import { Bill, BillSpec, PaymentTarget, parseCategory } from '@/domain';
import { type BillRecord, type UserRecord, hasPaymentProfile } from './records';

/** Dữ liệu đọc từ DB vẫn được kiểm trước khi thành model miền. Ném lỗi nếu bản ghi hỏng. */
export function toBill(r: BillRecord): Bill {
  const spec = new BillSpec({
    payerId: r.payerId,
    subtotal: Math.max(0, r.subtotal ?? 0),
    participants: r.participants ?? [],
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
