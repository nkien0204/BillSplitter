import { Crc16 } from '../qr/Crc16';
import { PaymentTarget } from '../qr/PaymentTarget';
import { Tlv } from '../qr/Tlv';
import { TransferContent } from '../qr/TransferContent';
import { VietQrCodec } from '../qr/VietQrCodec';
import { JavaRandom } from './javaRandom';

const VCB = new PaymentTarget('970436', '1012345678', 'TRAN TUAN DAT');
const errorOf = (r: ReturnType<typeof VietQrCodec.parse>) => (r.ok ? null : r.error);

describe('VietQrCodec', () => {
  it('CRC khớp vector chuẩn', () => {
    expect(Crc16.ccittFalseHex('123456789')).toBe('29B1');
  });

  it('sinh QR động đúng cấu trúc', () => {
    const p = VietQrCodec.encode(VCB, 308000, 'CB B1 DAT');
    expect(p).toBe(
      '00020101021238540010A00000072701240006970436011010123456780208QRIBFTTA530370454063080005802VN62130809CB B1 DAT6304' +
        p.substring(p.length - 4),
    );
    expect(p.startsWith('000201010212')).toBe(true);
  });

  it('QR tĩnh không có số tiền', () => {
    const p = VietQrCodec.encode(VCB, 0, '');
    expect(p.startsWith('000201010211')).toBe(true);
    expect(p).not.toContain('5406');
    const r = VietQrCodec.parse(p);
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.dynamic).toBe(false);
      expect(r.amount).toBe(0);
    }
  });

  it('đọc lại ra đúng dữ liệu', () => {
    const r = VietQrCodec.parse(VietQrCodec.encode(VCB, 1_231_200, 'CB B1 DAT'));
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.target.bankBin).toBe('970436');
      expect(r.target.accountNo).toBe('1012345678');
      expect(r.amount).toBe(1_231_200);
      expect(r.content).toBe('CB B1 DAT');
      expect(r.dynamic).toBe(true);
    }
  });

  it('đọc QR có tên người nhận và xuống dòng', () => {
    // Dựng QR như app ngân hàng: có trường 59 tên, có xuống dòng thừa ở cuối.
    const body =
      Tlv.field('00', '01') +
      Tlv.field('01', '11') +
      Tlv.field(
        '38',
        Tlv.field('00', 'A000000727') +
          Tlv.field('01', Tlv.field('00', '970422') + Tlv.field('01', '0987654321')) +
          Tlv.field('02', 'QRIBFTTA'),
      ) +
      Tlv.field('53', '704') +
      Tlv.field('58', 'VN') +
      Tlv.field('59', 'NGUYEN VAN MINH') +
      '6304';
    const r = VietQrCodec.parse(body + Crc16.ccittFalseHex(body) + '\n');
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.target.accountName).toBe('NGUYEN VAN MINH');
      expect(r.target.bankBin).toBe('970422');
    }
  });

  it('CRC sai bị từ chối', () => {
    const p = VietQrCodec.encode(VCB, 50000, '');
    const broken = p.substring(0, p.length - 1) + (p.endsWith('A') ? 'B' : 'A');
    expect(errorOf(VietQrCodec.parse(broken))).toBe('BAD_CRC');
  });

  it('sửa số tiền mà không tính lại CRC bị phát hiện', () => {
    const p = VietQrCodec.encode(VCB, 50000, '');
    expect(errorOf(VietQrCodec.parse(p.replace('540550000', '540590000')))).toBe('BAD_CRC');
  });

  it('không phải EMV', () => {
    expect(errorOf(VietQrCodec.parse('https://example.com'))).toBe('NOT_EMV');
    expect(errorOf(VietQrCodec.parse(''))).toBe('NOT_EMV');
    expect(errorOf(VietQrCodec.parse(null))).toBe('NOT_EMV');
  });

  it('bị cắt', () => {
    const p = VietQrCodec.encode(VCB, 50000, '');
    expect(errorOf(VietQrCodec.parse(p.substring(0, p.length - 10)))).toBe('TRUNCATED');
  });

  it('GUID khác NAPAS không phải VietQR', () => {
    const body =
      Tlv.field('00', '01') +
      Tlv.field('01', '11') +
      Tlv.field('38', Tlv.field('00', 'A000000999') + Tlv.field('01', 'abc')) +
      Tlv.field('53', '704') +
      Tlv.field('58', 'VN') +
      '6304';
    expect(errorOf(VietQrCodec.parse(body + Crc16.ccittFalseHex(body)))).toBe('NOT_VIETQR');
  });

  it('dịch vụ không hỗ trợ', () => {
    const body =
      Tlv.field('00', '01') +
      Tlv.field('01', '11') +
      Tlv.field(
        '38',
        Tlv.field('00', 'A000000727') +
          Tlv.field('01', Tlv.field('00', '970436') + Tlv.field('01', '123456')) +
          Tlv.field('02', 'QRPUSH'),
      ) +
      Tlv.field('53', '704') +
      Tlv.field('58', 'VN') +
      '6304';
    expect(errorOf(VietQrCodec.parse(body + Crc16.ccittFalseHex(body)))).toBe('UNSUPPORTED_SERVICE');
  });

  it('nội dung bỏ dấu và ký tự lạ', () => {
    expect(TransferContent.sanitize('cb lẩu gà lá é, Đạt!')).toBe('CB LAU GA LA E DAT');
    expect(TransferContent.forDebt('b12', 'Hùng')).toBe('CB B12 HUNG');
    expect(
      TransferContent.sanitize('Một nội dung rất rất dài vượt quá giới hạn').length,
    ).toBeLessThanOrEqual(VietQrCodec.MAX_CONTENT);
  });

  it('ẩn số tài khoản', () => {
    expect(VCB.maskedAccount()).toBe('101•••678');
  });

  it('property: mã hoá rồi đọc lại luôn khớp', () => {
    const r = new JavaRandom(7);
    const bins = ['970436', '970407', '970422', '970418', '970415', '970416'];
    for (let k = 0; k < 5000; k++) {
      let acc = '';
      const len = 6 + r.nextInt(14);
      for (let i = 0; i < len; i++) acc += String(r.nextInt(10));
      const t = new PaymentTarget(bins[r.nextInt(bins.length)], acc, '');
      const amount = r.nextInt(3) === 0 ? 0 : 1 + r.nextInt(999_999_999);
      const content = r.nextBoolean() ? '' : TransferContent.forDebt('b' + r.nextInt(99999), 'Người ' + k);
      const p = VietQrCodec.encode(t, amount, content);
      const res = VietQrCodec.parse(p);
      expect(res.ok).toBe(true);
      if (res.ok) {
        expect(res.target.bankBin).toBe(t.bankBin);
        expect(res.target.accountNo).toBe(t.accountNo);
        expect(res.amount).toBe(amount);
        expect(res.content).toBe(content);
      }
      expect(Crc16.ccittFalseHex(p.substring(0, p.length - 4))).toBe(p.substring(p.length - 4));
    }
  });
});
