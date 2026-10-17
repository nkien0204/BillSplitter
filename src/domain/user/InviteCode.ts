export const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
export const LENGTH = 6;

/** Nguồn ngẫu nhiên tối thiểu: cho phép test dùng bộ sinh có seed. */
export interface IntRandom {
  nextInt(bound: number): number;
}

export const defaultRandom: IntRandom = {
  nextInt: (bound) => Math.floor(Math.random() * bound),
};

/**
 * Mã mời vào nhóm: 6 ký tự từ bảng chữ không gây nhầm (bỏ I, O, 0, 1).
 * Người dùng gõ thường, có khoảng trắng hay gạch nối đều được.
 *
 * Chuẩn hoá chuỗi người dùng gõ; trả null nếu không phải mã hợp lệ.
 */
export function normalize(raw: string | null | undefined): string | null {
  if (raw == null) return null;
  let sb = '';
  for (const c of raw.toUpperCase()) {
    if (c === ' ' || c === '-' || c === '.') continue;
    if (ALPHABET.indexOf(c) < 0) return null;
    sb += c;
  }
  return sb.length === LENGTH ? sb : null;
}

export function generate(r: IntRandom = defaultRandom): string {
  let sb = '';
  for (let i = 0; i < LENGTH; i++) sb += ALPHABET.charAt(r.nextInt(ALPHABET.length));
  return sb;
}

/** "DALAT7" → "DAL-AT7" cho dễ đọc. */
export function format(code: string | null | undefined): string {
  if (code == null || code.length !== LENGTH) return code ?? '';
  return code.substring(0, 3) + '-' + code.substring(3);
}

export const InviteCode = { normalize, generate, format, ALPHABET, LENGTH };
