import { ccittFalseHex } from './Crc16';
import { MAX_CONTENT, NAPAS_GUID, SERVICE_ACCOUNT, SERVICE_CARD } from './constants';
import { PaymentTarget } from './PaymentTarget';
import { type QrError, qrErrorMessage } from './QrError';
import { TlvError, field, parse as parseTlv } from './Tlv';
import { sanitize } from './TransferContent';

export type QrParseResult =
  | {
      ok: true;
      target: PaymentTarget;
      /** 0 nếu QR tĩnh. */
      amount: number;
      content: string;
      dynamic: boolean;
    }
  | {
      ok: false;
      error: QrError;
      /** Thông điệp cho người dùng. */
      message: string;
    };

function fail(error: QrError, detail?: string): QrParseResult {
  return { ok: false, error, message: detail ?? qrErrorMessage(error) };
}

/**
 * Mã hoá và giải mã VietQR (chuẩn NAPAS dựa trên EMVCo Merchant-Presented Mode).
 *
 *   00 Payload format = 01          01 Point of initiation: 11 tĩnh, 12 động
 *   38 Người nhận: 00 GUID A000000727 · 01 (00 BIN, 01 số TK) · 02 QRIBFTTA|QRIBFTTC
 *   53 Tiền tệ 704 · 54 Số tiền · 58 VN · 59 Tên · 62.08 Nội dung · 63 CRC
 *
 * Sinh chuỗi VietQR. amount = 0 → QR tĩnh; content rỗng → không có trường 62.
 */
export function encode(t: PaymentTarget, amount: number, content: string): string {
  if (amount < 0) throw new Error('amount < 0');
  const c = sanitize(content);
  let p = '';
  p += field('00', '01');
  p += field('01', amount > 0 ? '12' : '11');
  const bank = field('00', t.bankBin) + field('01', t.accountNo);
  p += field('38', field('00', NAPAS_GUID) + field('01', bank) + field('02', SERVICE_ACCOUNT));
  p += field('53', '704');
  if (amount > 0) p += field('54', String(amount));
  p += field('58', 'VN');
  if (c !== '') p += field('62', field('08', c));
  p += '6304';
  return p + ccittFalseHex(p);
}

export function parse(raw: string | null | undefined): QrParseResult {
  if (raw == null) return fail('NOT_EMV');
  const s = raw.trim().replace(/\r/g, '').replace(/\n/g, '');
  if (!s.startsWith('000201')) return fail('NOT_EMV');
  const idx = s.lastIndexOf('6304');
  if (idx < 0 || idx + 8 !== s.length) return fail('TRUNCATED');
  const want = s.substring(idx + 4).toUpperCase();
  const got = ccittFalseHex(s.substring(0, idx + 4));
  if (want !== got) {
    return fail(
      'BAD_CRC',
      'CRC trong mã là ' + want + ', tính lại được ' + got + '. Ảnh QR hỏng hoặc chuỗi đã bị sửa.',
    );
  }
  try {
    const top = parseTlv(s);
    const m38 = top.get('38');
    if (m38 == null) return fail('NOT_VIETQR');
    const merchant = parseTlv(m38);
    if (merchant.get('00') !== NAPAS_GUID || merchant.get('01') == null) return fail('NOT_VIETQR');
    const svc = merchant.get('02');
    if (svc !== SERVICE_ACCOUNT && svc !== SERVICE_CARD) return fail('UNSUPPORTED_SERVICE');
    const bank = parseTlv(merchant.get('01') as string);
    const bin = bank.get('00');
    const acc = bank.get('01');
    if (!PaymentTarget.isValidBin(bin) || acc == null || acc === '') return fail('NOT_VIETQR');

    let amount = 0;
    const a = top.get('54');
    if (a != null) {
      const n = parseJavaDouble(a);
      if (n == null) return fail('NOT_EMV', 'Trường số tiền không hợp lệ: ' + a);
      amount = Math.round(n);
    }
    let content = '';
    const f62 = top.get('62');
    if (f62 != null) {
      const c = parseTlv(f62).get('08');
      if (c != null) content = c;
    }
    return {
      ok: true,
      target: new PaymentTarget(bin as string, acc, top.get('59')),
      amount,
      content,
      dynamic: top.get('01') === '12',
    };
  } catch (e) {
    if (e instanceof TlvError) return fail(e.error);
    throw e;
  }
}

/** Double.parseDouble: bỏ khoảng trắng hai đầu, không nhận chuỗi rỗng hay rác phía sau số. */
function parseJavaDouble(s: string): number | null {
  const t = s.trim();
  if (t === '') return null;
  if (t === 'NaN') return 0; // Math.round(NaN) = 0 trong Java
  if (!/^[+-]?(Infinity|(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?)$/.test(t)) return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : n > 0 ? Number.MAX_SAFE_INTEGER : Number.MIN_SAFE_INTEGER;
}

export const VietQrCodec = {
  encode,
  parse,
  NAPAS_GUID,
  SERVICE_ACCOUNT,
  SERVICE_CARD,
  MAX_CONTENT,
};
