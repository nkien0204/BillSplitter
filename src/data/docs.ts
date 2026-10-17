import type { DocumentData, DocumentSnapshot, QueryDocumentSnapshot } from 'firebase/firestore';
import { colorIndexFor } from './mappers';
import type {
  BillItemRecord,
  BillRecord,
  DebtStatusRecord,
  GroupRecord,
  InboxRecord,
  UserRecord,
} from './records';

type Snap = DocumentSnapshot<DocumentData> | QueryDocumentSnapshot<DocumentData>;

const str = (v: unknown, d = ''): string => (typeof v === 'string' ? v : d);
const strOrNull = (v: unknown): string | null => (typeof v === 'string' ? v : null);
const num = (v: unknown, d = 0): number => (typeof v === 'number' && Number.isFinite(v) ? v : d);
const strArray = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []);

/** Chuyển tài liệu Firestore thành bản ghi có kiểu. Trường thiếu hoặc sai kiểu lấy giá trị mặc định. */
export function userFrom(s: Snap): UserRecord {
  const d = s.data() ?? {};
  return {
    id: s.id,
    name: str(d.name),
    colorIndex: num(d.colorIndex, colorIndexFor(s.id)),
    guest: d.guest === true,
    phone: strOrNull(d.phone),
    bankBin: strOrNull(d.bankBin),
    accountNo: strOrNull(d.accountNo),
    accountName: strOrNull(d.accountName),
    qrUpdatedAt: num(d.qrUpdatedAt),
    createdAt: num(d.createdAt),
    createdBy: strOrNull(d.createdBy),
  };
}

export function groupFrom(s: Snap): GroupRecord {
  const d = s.data() ?? {};
  return {
    id: s.id,
    name: str(d.name),
    inviteCode: strOrNull(d.inviteCode),
    createdBy: str(d.createdBy),
    createdAt: num(d.createdAt),
    memberOrder: strArray(d.memberOrder),
    activeMemberIds: strArray(d.activeMemberIds),
  };
}

export function billFrom(s: Snap): BillRecord {
  const d = s.data() ?? {};
  const items: BillItemRecord[] = Array.isArray(d.items)
    ? d.items.map((it: Record<string, unknown>) => ({
        name: str(it?.name),
        price: num(it?.price),
        consumers: strArray(it?.consumers),
      }))
    : [];
  const shares: Record<string, number> = {};
  if (d.shares && typeof d.shares === 'object') {
    for (const [k, v] of Object.entries(d.shares as Record<string, unknown>)) shares[k] = num(v);
  }
  return {
    id: s.id,
    groupId: str(d.groupId),
    title: str(d.title),
    payerId: str(d.payerId),
    mode: str(d.mode, 'EQUAL'),
    subtotal: num(d.subtotal),
    participants: strArray(d.participants),
    vatPercent: num(d.vatPercent),
    servicePercent: num(d.servicePercent),
    rounding: num(d.rounding, 1000),
    category: str(d.category, 'KHAC'),
    shares,
    items,
    createdBy: strOrNull(d.createdBy),
    createdAt: num(d.createdAt),
    updatedAt: num(d.updatedAt),
  };
}

export function statusFrom(s: Snap): DebtStatusRecord {
  const d = s.data() ?? {};
  return {
    key: s.id,
    billId: str(d.billId),
    groupId: str(d.groupId),
    debtorId: str(d.debtorId),
    status: str(d.status, 'PENDING'),
    updatedAt: num(d.updatedAt),
  };
}

export function inboxFrom(s: Snap): InboxRecord {
  const d = s.data() ?? {};
  return {
    id: s.id,
    recipientId: str(d.recipientId),
    fromId: str(d.fromId),
    message: str(d.message),
    billId: strOrNull(d.billId),
    debtKey: strOrNull(d.debtKey),
    createdAt: num(d.createdAt),
  };
}
