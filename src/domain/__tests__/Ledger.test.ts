import { Bill } from '../model/Bill';
import { BillSpec } from '../model/BillSpec';
import { type DebtStatus, debtKeyOf } from '../model/types';
import { DebtSimplifier } from '../ledger/DebtSimplifier';
import { Ledger } from '../ledger/Ledger';
import { PaymentStateMachine as PSM } from '../ledger/PaymentStateMachine';
import { JavaRandom } from './javaRandom';

const G = ['dat', 'minh', 'lan', 'hung'];

function demo(st: Map<string, DebtStatus>): Ledger {
  const gm = new Map([['g1', G]]);
  const bills = [
    new Bill('b1', 'g1', 'Lẩu', 1, 'minh', new BillSpec({ payerId: 'minh', mode: 'EQUAL', subtotal: 1_140_000, participants: G, vatPercent: 8, servicePercent: 0, rounding: 1000 })),
    new Bill('b3', 'g1', 'Xăng', 2, 'lan', new BillSpec({ payerId: 'lan', mode: 'EQUAL', subtotal: 420_000, participants: G, vatPercent: 0, servicePercent: 0, rounding: 1000 })),
  ];
  return new Ledger(gm, bills, st);
}

describe('Ledger', () => {
  it('sinh khoản nợ từ hoá đơn', () => {
    const l = demo(new Map());
    expect(l.debts()).toHaveLength(6);
    const d = l.debt(debtKeyOf('b1', 'dat'));
    expect(d?.creditorId).toBe('minh');
    expect(d?.amount).toBe(308_000);
    expect(d?.status).toBe('PENDING');
    expect(l.owedBy('dat', 'g1')).toBe(308_000 + 105_000);
  });

  it('khoản đã xác nhận không tính vào số dư', () => {
    const l = demo(new Map<string, DebtStatus>([[debtKeyOf('b3', 'dat'), 'CONFIRMED']]));
    expect(l.owedBy('dat', 'g1')).toBe(308_000);
  });

  it('tất toán gộp về ít giao dịch', () => {
    const l = demo(new Map());
    const t = l.settlement('g1');
    expect(t.length).toBeLessThanOrEqual(3);
    const net = new Map(l.netBalances('g1'));
    for (const x of t) {
      net.set(x.fromId, (net.get(x.fromId) as number) + x.amount);
      net.set(x.toId, (net.get(x.toId) as number) - x.amount);
    }
    for (const v of net.values()) expect(v).toBe(0);
  });

  it('vòng tròn triệt tiêu', () => {
    expect(DebtSimplifier.simplify(new Map([['a', 0], ['b', 0], ['c', 0]]))).toHaveLength(0);
  });

  it('tổng số dư khác không bị từ chối', () => {
    expect(() => DebtSimplifier.simplify(new Map([['a', 10], ['b', -9]]))).toThrow();
  });

  it('property: tối giản nợ luôn về không', () => {
    const r = new JavaRandom(99);
    for (let k = 0; k < 5000; k++) {
      const n = 2 + r.nextInt(12);
      const net = new Map<string, number>();
      let sum = 0;
      for (let i = 0; i < n - 1; i++) {
        const v = r.nextInt(5) === 0 ? 0 : r.nextInt(2_000_000) - 1_000_000;
        net.set('u' + i, v);
        sum += v;
      }
      net.set('u' + (n - 1), -sum);
      const nonZero = [...net.values()].filter((v) => v !== 0).length;
      const t = DebtSimplifier.simplify(net);
      expect(t.length).toBeLessThanOrEqual(Math.max(0, nonZero - 1));
      const left = new Map(net);
      for (const x of t) {
        expect(x.amount).toBeGreaterThan(0);
        left.set(x.fromId, (left.get(x.fromId) as number) + x.amount);
        left.set(x.toId, (left.get(x.toId) as number) - x.amount);
      }
      for (const v of left.values()) expect(v + 0).toBe(0); // +0 gộp -0
    }
  });

  it('máy trạng thái', () => {
    expect(PSM.canApply('PENDING', 'MARK_PAID', 'DEBTOR')).toBe(true);
    expect(PSM.canApply('PENDING', 'MARK_PAID', 'CREDITOR')).toBe(false);
    expect(PSM.canApply('MARKED_PAID', 'CONFIRM', 'DEBTOR')).toBe(false);
    expect(PSM.canApply('MARKED_PAID', 'CONFIRM', 'CREDITOR')).toBe(true);
    expect(PSM.canApply('MARKED_PAID', 'DISPUTE', 'CREDITOR')).toBe(true);
    expect(PSM.canApply('PENDING', 'DISPUTE', 'CREDITOR')).toBe(false);
    expect(PSM.canApply('PENDING', 'CONFIRM', 'CREDITOR')).toBe(true);
    expect(PSM.canApply('DISPUTED', 'MARK_PAID', 'DEBTOR')).toBe(true);
    expect(PSM.canApply('CONFIRMED', 'CONFIRM', 'CREDITOR')).toBe(false);
    expect(PSM.canApply('PENDING', 'MARK_PAID', 'OTHER')).toBe(false);
    expect(() => PSM.apply('CONFIRMED', 'MARK_PAID', 'DEBTOR')).toThrow();
    expect(PSM.roleOf('dat', 'dat', 'minh')).toBe('DEBTOR');
  });
});
