/** Kiểu dữ liệu miền: không phụ thuộc React Native hay Firebase. */

export type SplitMode =
  /** Chia đều tạm tính cho những người tham gia. */
  | 'EQUAL'
  /** Mỗi món chia đều cho những người dùng món đó. */
  | 'ITEMIZED'
  /** Mỗi người một số tiền tự nhập (VND). Tạm tính = tổng các số đó. */
  | 'CUSTOM'
  /** Chia tạm tính theo phần trăm; lưu theo phần vạn (1% = 100), tổng phải đúng 10 000. */
  | 'PERCENT';

export const SPLIT_MODES: readonly SplitMode[] = ['EQUAL', 'ITEMIZED', 'CUSTOM', 'PERCENT'];

export type DebtStatus =
  /** Chưa trả. */
  | 'PENDING'
  /** Người nợ báo đã chuyển, chờ người được trả xác nhận. */
  | 'MARKED_PAID'
  /** Người được trả xác nhận đã nhận. */
  | 'CONFIRMED'
  /** Người được trả báo chưa nhận được. */
  | 'DISPUTED';

export const DEBT_STATUSES: readonly DebtStatus[] = ['PENDING', 'MARKED_PAID', 'CONFIRMED', 'DISPUTED'];

export function isOpen(status: DebtStatus): boolean {
  return status !== 'CONFIRMED';
}

/** Đọc từ chuỗi đã lưu; giá trị lạ → undefined (người gọi coi như PENDING). */
export function parseDebtStatus(s: string | null | undefined): DebtStatus | undefined {
  return DEBT_STATUSES.find((d) => d === s);
}

/** Danh mục khoản chi, dùng để lọc lịch sử. */
export type Category = 'AN_UONG' | 'DI_LAI' | 'LUU_TRU' | 'MUA_SAM' | 'GIAI_TRI' | 'KHAC';

export const CATEGORIES: readonly Category[] = [
  'AN_UONG',
  'DI_LAI',
  'LUU_TRU',
  'MUA_SAM',
  'GIAI_TRI',
  'KHAC',
];

const CATEGORY_LABELS: Record<Category, string> = {
  AN_UONG: 'Ăn uống',
  DI_LAI: 'Đi lại',
  LUU_TRU: 'Lưu trú',
  MUA_SAM: 'Mua sắm',
  GIAI_TRI: 'Giải trí',
  KHAC: 'Khác',
};

export function categoryLabel(c: Category): string {
  return CATEGORY_LABELS[c];
}

/** Đọc từ chuỗi đã lưu; giá trị lạ hoặc null thành KHAC. */
export function parseCategory(s: string | null | undefined): Category {
  return CATEGORIES.find((c) => c === s) ?? 'KHAC';
}

export function parseSplitMode(s: string | null | undefined): SplitMode {
  return SPLIT_MODES.find((m) => m === s) ?? 'EQUAL';
}

/** Một lần chuyển khoản đề xuất khi tất toán nhóm. */
export interface Transfer {
  readonly fromId: string;
  readonly toId: string;
  readonly amount: number;
}

/** Phần của một người trong một hoá đơn, kèm cách tính. */
export interface ShareLine {
  readonly memberId: string;
  /** Phần gốc (món hoặc chia đều), chưa phí. */
  readonly base: number;
  /** VAT + phí phục vụ phân bổ theo tỉ lệ phần gốc. */
  readonly fee: number;
  /** Số tiền cuối cùng sau làm tròn. */
  readonly amount: number;
  /** amount − (base + fee). Âm hoặc dương vài trăm/nghìn đồng. */
  readonly roundingAdj: number;
}

/** Khoản nợ của một người với người trả của một hoá đơn. Khoá tất định: billId:debtorId. */
export interface Debt {
  readonly key: string;
  readonly billId: string;
  readonly groupId: string;
  readonly debtorId: string;
  readonly creditorId: string;
  readonly amount: number;
  readonly status: DebtStatus;
}

export function debtKeyOf(billId: string, debtorId: string): string {
  return billId + ':' + debtorId;
}

/** Tách "billId:debtorId". Id không chứa dấu ':' nên tách ở dấu đầu tiên, giống bản Java. */
export function splitDebtKey(key: string): { billId: string; debtorId: string } | null {
  const sep = key.indexOf(':');
  if (sep < 0) return null;
  return { billId: key.substring(0, sep), debtorId: key.substring(sep + 1) };
}
