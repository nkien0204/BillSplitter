import type { QrError } from './QrError';

export class TlvError extends Error {
  constructor(readonly error: QrError) {
    super(error);
    this.name = 'TlvError';
  }
}

/** Mã hoá/giải mã trường TLV kiểu EMVCo: ID 2 chữ số, độ dài 2 chữ số, rồi giá trị. */
export function field(id: string, value: string): string {
  if (value.length > 99) throw new Error('TLV value > 99 chars: ' + id);
  return id + (value.length < 10 ? '0' : '') + value.length + value;
}

const isDigits = (s: string) => s.length > 0 && /^[0-9]+$/.test(s);

export function parse(s: string): Map<string, string> {
  const out = new Map<string, string>();
  let i = 0;
  while (i < s.length) {
    if (i + 4 > s.length) throw new TlvError('TRUNCATED');
    const id = s.substring(i, i + 2);
    const lenStr = s.substring(i + 2, i + 4);
    if (!isDigits(id) || !isDigits(lenStr)) throw new TlvError('NOT_EMV');
    const len = parseInt(lenStr, 10);
    if (i + 4 + len > s.length) throw new TlvError('TRUNCATED');
    out.set(id, s.substring(i + 4, i + 4 + len));
    i += 4 + len;
  }
  return out;
}

export const Tlv = { field, parse };
