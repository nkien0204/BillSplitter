/** Tiền luôn là số nguyên VND. Module này chỉ định dạng, không làm phép tính. */

/** 1231200 → "1.231.200" */
export function dots(n: number): string {
  const s = String(Math.abs(n));
  const lead = s.length % 3;
  let out = '';
  for (let i = 0; i < s.length; i++) {
    if (i > 0 && (i - lead) % 3 === 0) out += '.';
    out += s.charAt(i);
  }
  return (n < 0 ? '−' : '') + out;
}

/** 1231200 → "1.231.200đ" */
export function format(n: number): string {
  return dots(n) + 'đ';
}

/** +50.000đ / −50.000đ / 0đ */
export function formatSigned(n: number): string {
  return (n > 0 ? '+' : '') + format(n);
}

/** Lấy phần số từ chuỗi người dùng gõ ("1.140.000" → 1140000). Rỗng → 0. Chặn tràn. */
export function parseDigits(text: string | null | undefined, max: number): number {
  if (text == null) return 0;
  let v = 0;
  for (let i = 0; i < text.length; i++) {
    const c = text.charAt(i);
    if (c >= '0' && c <= '9') {
      v = v * 10 + (c.charCodeAt(0) - 48);
      if (v > max) return max;
    }
  }
  return v;
}

export const Money = { dots, format, formatSigned, parseDigits };
