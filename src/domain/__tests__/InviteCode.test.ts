import { InviteCode } from '../user/InviteCode';
import { JavaRandom } from './javaRandom';

describe('InviteCode', () => {
  it('gõ thường và gạch nối đều được', () => {
    expect(InviteCode.normalize('dal-at7')).toBe('DALAT7');
    expect(InviteCode.normalize(' DAL AT7 ')).toBe('DALAT7');
    expect(InviteCode.format('DALAT7')).toBe('DAL-AT7');
  });

  it('từ chối mã sai', () => {
    expect(InviteCode.normalize('DALAT')).toBeNull();
    expect(InviteCode.normalize('DALAT77')).toBeNull();
    expect(InviteCode.normalize('DAL0T7')).toBeNull(); // số 0 không có trong bảng chữ
    expect(InviteCode.normalize(null)).toBeNull();
  });

  it('property: mã sinh ra luôn hợp lệ', () => {
    const r = new JavaRandom(7);
    for (let i = 0; i < 5000; i++) {
      const c = InviteCode.generate(r);
      expect(InviteCode.normalize(c)).toBe(c);
      expect(InviteCode.normalize(InviteCode.format(c).toLowerCase())).toBe(c);
      expect(c).toHaveLength(InviteCode.LENGTH);
    }
  });
});
