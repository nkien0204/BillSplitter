import { PhoneNumber } from '../user/PhoneNumber';
import { JavaRandom } from './javaRandom';

describe('PhoneNumber', () => {
  it('các cách gõ cùng một số ra cùng kết quả', () => {
    const want = '0912345678';
    for (const raw of [
      '0912345678',
      '0912 345 678',
      '0912.345.678',
      '0912-345-678',
      '+84912345678',
      '+84 912 345 678',
      '84912345678',
      '  (0912) 345 678 ',
    ]) {
      expect(PhoneNumber.normalize(raw)).toBe(want);
    }
  });

  it('từ chối số không hợp lệ', () => {
    expect(PhoneNumber.normalize(null)).toBeNull();
    expect(PhoneNumber.normalize('')).toBeNull();
    expect(PhoneNumber.normalize('091234567')).toBeNull(); // 9 số
    expect(PhoneNumber.normalize('09123456789')).toBeNull(); // 11 số
    expect(PhoneNumber.normalize('0212345678')).toBeNull(); // đầu 02 là cố định
    expect(PhoneNumber.normalize('1912345678')).toBeNull();
    expect(PhoneNumber.normalize('0912abc678')).toBeNull();
    expect(PhoneNumber.normalize('09+12345678')).toBeNull();
    expect(PhoneNumber.isValid('123')).toBe(false);
    expect(PhoneNumber.isValid('0388 000 111')).toBe(true);
  });

  it('định dạng và ẩn', () => {
    expect(PhoneNumber.format('0912345678')).toBe('0912 345 678');
    expect(PhoneNumber.masked('0912345678')).toBe('0912 ••• 678');
  });

  it('property: chuẩn hoá là luỹ đẳng', () => {
    const r = new JavaRandom(84);
    const nets = ['3', '5', '7', '8', '9'];
    for (let k = 0; k < 5000; k++) {
      let n = '0' + nets[r.nextInt(nets.length)];
      for (let i = 0; i < 8; i++) n += String(r.nextInt(10));
      expect(PhoneNumber.normalize(n)).toBe(n);
      expect(PhoneNumber.normalize(PhoneNumber.normalize(n))).toBe(n);
      expect(PhoneNumber.normalize(PhoneNumber.format(n))).toBe(n);
      expect(PhoneNumber.normalize('+84' + n.substring(1))).toBe(n);
    }
  });
});
