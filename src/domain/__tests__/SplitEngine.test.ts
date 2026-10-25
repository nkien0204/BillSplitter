import { BillSpec } from '../model/BillSpec';
import { SplitEngine } from '../split/SplitEngine';
import { members as makeMembers, randomBill } from './Fixtures';
import { JavaRandom } from './javaRandom';

const G = ['dat', 'minh', 'lan', 'hung'];

const equal = (payer: string, subtotal: number, participants: string[]) =>
  new BillSpec({ payerId: payer, subtotal, participants });

describe('SplitEngine', () => {
  it('chia đều ba người: đồng dư về người đứng đầu', () => {
    const r = SplitEngine.split(equal('dat', 100000, ['dat', 'minh', 'lan']), G);
    expect(r.lineOf('dat')?.amount).toBe(33334);
    expect(r.lineOf('minh')?.amount).toBe(33333);
    expect(r.lineOf('lan')?.amount).toBe(33333);
    expect(r.sumOfShares()).toBe(100000);
  });

  it('đồng dư phát theo thứ tự nhóm, không theo thứ tự chọn', () => {
    const r = SplitEngine.split(equal('dat', 100002, ['hung', 'lan', 'minh', 'dat']), G);
    expect(r.lines.map((l) => [l.memberId, l.amount])).toEqual([
      ['dat', 25001],
      ['minh', 25001],
      ['lan', 25000],
      ['hung', 25000],
    ]);
  });

  it('lẩu gà bốn người chia chẵn', () => {
    const r = SplitEngine.split(equal('minh', 1_140_000, G), G);
    expect(r.total).toBe(1_140_000);
    for (const id of G) expect(r.lineOf(id)?.amount).toBe(285_000);
  });

  it('người trả không tham gia vẫn có dòng với phần 0', () => {
    const r = SplitEngine.split(equal('dat', 90_000, ['minh', 'lan', 'hung']), G);
    expect(r.lineOf('dat')?.amount).toBe(0);
    expect(r.lineOf('minh')?.amount).toBe(30_000);
    expect(r.isBalanced()).toBe(true);
  });

  it('một người thì không ai nợ', () => {
    const r = SplitEngine.split(equal('dat', 55000, ['dat']), G);
    expect(r.lines).toHaveLength(1);
    expect(r.lineOf('dat')?.amount).toBe(55000);
  });

  it('người ngoài memberOrder bị bỏ qua', () => {
    const r = SplitEngine.split(equal('dat', 10_000, ['dat', 'nguoi-la']), G);
    expect(r.lines.map((l) => l.memberId)).toEqual(['dat']);
    expect(r.lineOf('dat')?.amount).toBe(10_000);
  });

  it('tổng bằng không thì không lưu được', () => {
    expect(SplitEngine.split(equal('dat', 0, G), G).isSavable()).toBe(false);
  });

  it('chưa chọn ai thì báo lỗi', () => {
    expect(SplitEngine.check(equal('dat', 1000, []), G)).toBe('Chọn ít nhất một người tham gia');
    expect(SplitEngine.check(equal('dat', 1000, ['dat']), G)).toBeNull();
  });

  it('không cho tổng âm', () => {
    expect(() => equal('dat', -1, G)).toThrow();
  });

  it('equal: các phần chênh nhau tối đa 1 đồng', () => {
    expect(SplitEngine.equal(10, 3)).toEqual([4, 3, 3]);
    expect(SplitEngine.equal(2, 3)).toEqual([1, 1, 0]);
    expect(SplitEngine.equal(0, 3)).toEqual([0, 0, 0]);
    expect(SplitEngine.equal(5, 0)).toEqual([]);
  });

  it('property: 10000 hoá đơn không lệch một đồng', () => {
    const rnd = new JavaRandom(20260927);
    for (let k = 0; k < 10000; k++) {
      const ms = makeMembers(2 + rnd.nextInt(14));
      const s = randomBill(rnd, ms);
      const r = SplitEngine.split(s, ms);
      expect(r.sumOfShares()).toBe(r.total);
      const shares = r.lines.filter((l) => s.participants.includes(l.memberId)).map((l) => l.amount);
      for (const a of shares) expect(a).toBeGreaterThanOrEqual(0);
      expect(Math.max(...shares) - Math.min(...shares)).toBeLessThanOrEqual(1);
      const again = SplitEngine.split(s, ms);
      r.lines.forEach((l, i) => expect(again.lines[i].amount).toBe(l.amount));
    }
  });
});
