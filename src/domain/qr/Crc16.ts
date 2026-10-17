/** UTF-8 thủ công: Hermes không phải bản nào cũng có TextEncoder. */
export function utf8Bytes(s: string): number[] {
  const out: number[] = [];
  for (const ch of s) {
    const cp = ch.codePointAt(0) as number;
    if (cp < 0x80) out.push(cp);
    else if (cp < 0x800) out.push(0xc0 | (cp >> 6), 0x80 | (cp & 0x3f));
    else if (cp < 0x10000) out.push(0xe0 | (cp >> 12), 0x80 | ((cp >> 6) & 0x3f), 0x80 | (cp & 0x3f));
    else
      out.push(0xf0 | (cp >> 18), 0x80 | ((cp >> 12) & 0x3f), 0x80 | ((cp >> 6) & 0x3f), 0x80 | (cp & 0x3f));
  }
  return out;
}

/** CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF) theo EMVCo QR. Vector chuẩn: "123456789" → 29B1. */
export function ccittFalseHex(s: string): string {
  let crc = 0xffff;
  for (const b of utf8Bytes(s)) {
    crc ^= (b & 0xff) << 8;
    for (let j = 0; j < 8; j++) {
      crc = (crc & 0x8000) !== 0 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
    }
  }
  return crc.toString(16).toUpperCase().padStart(4, '0');
}

export const Crc16 = { ccittFalseHex };
