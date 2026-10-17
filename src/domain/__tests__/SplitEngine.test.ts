import { BillItem } from '../model/BillItem';
import { BillSpec } from '../model/BillSpec';
import { SplitEngine } from '../split/SplitEngine';
import { members as makeMembers, randomBill } from './Fixtures';
import { JavaRandom } from './javaRandom';

const G = ['dat', 'minh', 'lan', 'hung'];

const equal = (payer: string, subtotal: number, participants: string[], vat: number, svc: number, rounding: number) =>
  new BillSpec({ payerId: payer, mode: 'EQUAL', subtotal, participants, vatPercent: vat, servicePercent: svc, rounding });

const itemized = (payer: string, items: BillItem[], vat: number, svc: number, rounding: number) =>
  new BillSpec({ payerId: payer, mode: 'ITEMIZED', subtotal: 0, items, vatPercent: vat, servicePercent: svc, rounding });

const withShares = (
  payer: string,
  mode: 'CUSTOM' | 'PERCENT',
  subtotal: number,
  shares: Record<string, number>,
  vat: number,
  svc: number,
  rounding: number,
) => new BillSpec({ payerId: payer, mode, subtotal, shares, vatPercent: vat, servicePercent: svc, rounding });

describe('SplitEngine', () => {
  it('chia đều ba người, làm tròn một đồng', () => {
    const r = SplitEngine.split(equal('dat', 100000, ['dat', 'minh', 'lan'], 0, 0, 1), G);
    expect(r.lineOf('dat')?.amount).toBe(33334);
    expect(r.lineOf('minh')?.amount).toBe(33333);
    expect(r.lineOf('lan')?.amount).toBe(33333);
    expect(r.sumOfShares()).toBe(100000);
  });

  it('làm tròn nghìn: phần lẻ dồn người trả', () => {
    const r = SplitEngine.split(equal('dat', 100000, ['dat', 'minh', 'lan'], 0, 0, 1000), G);
    expect(r.lineOf('minh')?.amount).toBe(33000);
    expect(r.lineOf('lan')?.amount).toBe(33000);
    expect(r.lineOf('dat')?.amount).toBe(34000);
    expect(r.isBalanced()).toBe(true);
  });

  it('lẩu gà VAT 8%', () => {
    const r = SplitEngine.split(equal('minh', 1_140_000, G, 8, 0, 1000), G);
    expect(r.vat).toBe(91_200);
    expect(r.total).toBe(1_231_200);
    const dat = r.lineOf('dat');
    expect(dat?.base).toBe(285_000);
    expect(dat?.fee).toBe(22_800);
    expect(dat?.amount).toBe(308_000);
    expect(dat?.roundingAdj).toBe(200);
    expect(r.lineOf('minh')?.amount).toBe(307_200);
    expect(r.isBalanced()).toBe(true);
  });

  it('chia theo món', () => {
    const items = [
      new BillItem('Cà phê muối', 45000, ['dat']),
      new BillItem('Cà phê muối', 45000, ['lan']),
      new BillItem('Bạc xỉu', 40000, ['minh']),
      new BillItem('Trà atiso', 35000, ['hung']),
      new BillItem('Bánh tráng nướng', 60000, G),
    ];
    const r = SplitEngine.split(itemized('dat', items, 0, 0, 1000), G);
    expect(r.total).toBe(225_000);
    expect(r.lineOf('dat')?.amount).toBe(60_000);
    expect(r.lineOf('minh')?.amount).toBe(55_000);
    expect(r.lineOf('lan')?.amount).toBe(60_000);
    expect(r.lineOf('hung')?.amount).toBe(50_000);
  });

  it('phụ phí phân bổ theo tỉ lệ phần gốc', () => {
    const items = [new BillItem('Bò', 300000, ['dat']), new BillItem('Rau', 100000, ['minh'])];
    const r = SplitEngine.split(itemized('dat', items, 10, 5, 1), G);
    expect(r.vat).toBe(40000);
    expect(r.service).toBe(20000);
    expect(r.lineOf('dat')?.fee).toBe(45000);
    expect(r.lineOf('minh')?.fee).toBe(15000);
    expect(r.lineOf('minh')?.amount).toBe(115000);
  });

  it('món chưa gán thì không lưu được', () => {
    const items = [new BillItem('Lẩu', 200000, ['dat']), new BillItem('Bia', 90000, [])];
    const r = SplitEngine.split(itemized('dat', items, 0, 0, 1), G);
    expect(r.unassignedItems).toEqual(['Bia']);
    expect(r.isSavable()).toBe(false);
  });

  it('người trả không dùng gì thì làm tròn xuống', () => {
    // 100.500 / 3 = 33.500 mỗi người; làm tròn gần nhất ra 34.000 × 3 = 102.000 > tổng.
    const r = SplitEngine.split(equal('dat', 100_500, ['minh', 'lan', 'hung'], 0, 0, 1000), G);
    expect(r.lineOf('minh')?.amount).toBe(33_000);
    expect(r.lineOf('dat')?.amount).toBe(1_500);
    expect(r.lineOf('dat')?.amount).toBeGreaterThanOrEqual(0);
    expect(r.isBalanced()).toBe(true);
  });

  it('một người thì không ai nợ', () => {
    const r = SplitEngine.split(equal('dat', 55000, ['dat'], 0, 0, 1000), G);
    expect(r.lines).toHaveLength(1);
    expect(r.lineOf('dat')?.amount).toBe(55000);
  });

  it('tổng bằng không thì không lưu được', () => {
    expect(SplitEngine.split(equal('dat', 0, G, 8, 0, 1000), G).isSavable()).toBe(false);
  });

  it('số tiền tuỳ chỉnh giữ nguyên từng người', () => {
    const s = withShares('dat', 'CUSTOM', 0, { dat: 120_000, minh: 80_000, lan: 50_000 }, 0, 0, 1000);
    const r = SplitEngine.split(s, G);
    expect(SplitEngine.check(s, G)).toBeNull();
    expect(r.subtotal).toBe(250_000);
    expect(r.lineOf('minh')?.amount).toBe(80_000);
    expect(r.lineOf('lan')?.amount).toBe(50_000);
    expect(r.lineOf('dat')?.amount).toBe(120_000);
    expect(r.isBalanced()).toBe(true);
  });

  it('số tiền tuỳ chỉnh có VAT phân bổ theo tỉ lệ', () => {
    const s = withShares('dat', 'CUSTOM', 0, { minh: 100_000, lan: 300_000 }, 10, 0, 1);
    const r = SplitEngine.split(s, G);
    expect(r.vat).toBe(40_000);
    expect(r.lineOf('minh')?.amount).toBe(110_000);
    expect(r.lineOf('lan')?.amount).toBe(330_000);
    expect(r.lineOf('dat')?.amount).toBe(0);
    expect(r.sumOfShares()).toBe(r.total);
  });

  it('phần trăm chia đúng tỉ lệ', () => {
    const s = withShares('minh', 'PERCENT', 1_000_000, { dat: 5000, minh: 3000, lan: 2000 }, 0, 0, 1);
    expect(SplitEngine.check(s, G)).toBeNull();
    const r = SplitEngine.split(s, G);
    expect(r.lineOf('dat')?.amount).toBe(500_000);
    expect(r.lineOf('minh')?.amount).toBe(300_000);
    expect(r.lineOf('lan')?.amount).toBe(200_000);
  });

  it('phần trăm lẻ không lệch một đồng', () => {
    const s = withShares('dat', 'PERCENT', 100_000, { dat: 3333, minh: 3333, lan: 3334 }, 0, 0, 1);
    const r = SplitEngine.split(s, G);
    expect(r.sumOfShares()).toBe(100_000);
    expect(r.lineOf('lan')?.amount).toBe(33_340);
  });

  it('phần trăm không đủ 100 thì báo lỗi', () => {
    const s = withShares('dat', 'PERCENT', 100_000, { dat: 5000, minh: 2550 }, 0, 0, 1);
    const err = SplitEngine.check(s, G);
    expect(err).not.toBeNull();
    expect(err).toContain('75,5%');
  });

  it('đọc phần trăm người dùng gõ', () => {
    expect(SplitEngine.parsePercent('12,5')).toBe(1250);
    expect(SplitEngine.parsePercent('12.50')).toBe(1250);
    expect(SplitEngine.parsePercent('33,33%')).toBe(3333);
    expect(SplitEngine.parsePercent('100')).toBe(10_000);
    expect(SplitEngine.parsePercent(',5')).toBe(50);
    expect(SplitEngine.parsePercent('')).toBe(0);
    expect(SplitEngine.parsePercent('100,01')).toBe(-1);
    expect(SplitEngine.parsePercent('1,234')).toBe(-1);
    expect(SplitEngine.parsePercent('abc')).toBe(-1);
    expect(SplitEngine.formatPercent(1250)).toBe('12,5%');
    expect(SplitEngine.formatPercent(3333)).toBe('33,33%');
    expect(SplitEngine.formatPercent(705)).toBe('7,05%');
  });

  it('tuỳ chỉnh trống thì báo lỗi', () => {
    expect(SplitEngine.check(withShares('dat', 'CUSTOM', 0, {}, 0, 0, 1), G)).not.toBeNull();
  });

  it('không cho phần âm', () => {
    expect(() => withShares('dat', 'CUSTOM', 0, { minh: -1 }, 0, 0, 1)).toThrow();
  });

  it('property: 10000 hoá đơn không lệch một đồng', () => {
    const rnd = new JavaRandom(20260927);
    for (let k = 0; k < 10000; k++) {
      const ms = makeMembers(2 + rnd.nextInt(14));
      const s = randomBill(rnd, ms);
      const r = SplitEngine.split(s, ms);
      expect(r.sumOfShares()).toBe(r.total);
      for (const l of r.lines) {
        expect(l.amount).toBeGreaterThanOrEqual(0);
        if (l.memberId !== s.payerId && s.rounding > 1) expect(l.amount % s.rounding).toBe(0);
        expect(l.base + l.fee + l.roundingAdj).toBe(l.amount);
        expect(Math.abs(l.roundingAdj)).toBeLessThan(Math.max(1, s.rounding) * r.lines.length);
      }
      const again = SplitEngine.split(s, ms);
      r.lines.forEach((l, i) => expect(again.lines[i].amount).toBe(l.amount));
    }
  });
});
