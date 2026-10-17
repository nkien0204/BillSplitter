import { MAX_CONTENT } from './constants';

const COMBINING_MARKS = /[̀-ͯ]+/g;

/** Bỏ dấu tiếng Việt: NFD rồi xoá dấu kết hợp, đ/Đ thành d/D. */
export function toAscii(s: string | null | undefined): string {
  if (s == null) return '';
  const n = (typeof s.normalize === 'function' ? s.normalize('NFD') : s).replace(COMBINING_MARKS, '');
  return n.replace(/đ/g, 'd').replace(/Đ/g, 'D');
}

/** Nội dung chuyển khoản: chữ không dấu in hoa, số, khoảng trắng. Ngân hàng hay từ chối ký tự khác. */
export function sanitize(s: string | null | undefined): string {
  const a = toAscii(s)
    .toUpperCase()
    .replace(/[^A-Z0-9 ]/g, ' ')
    .replace(/ +/g, ' ')
    .trim();
  return a.length > MAX_CONTENT ? a.substring(0, MAX_CONTENT).trim() : a;
}

/** Nội dung gợi ý cho một khoản nợ, vd "CB 7K2Q DAT". */
export function forDebt(billId: string, debtorName: string): string {
  let code = billId.replace(/[^A-Za-z0-9]/g, '');
  if (code.length > 6) code = code.substring(code.length - 6);
  return sanitize('CB ' + code + ' ' + debtorName);
}

export const TransferContent = { toAscii, sanitize, forDebt };
