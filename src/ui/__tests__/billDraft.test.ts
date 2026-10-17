import { Bill, BillItem, BillSpec, SplitEngine } from '@/domain';
import { createDraft, draftFrom, prefillPercent, referencedIds, toSpec } from '../bill/billDraft';

describe('billDraft', () => {
  it('bản nháp mới: chia đều cho cả nhóm, người trả là mình', () => {
    const d = createDraft('g', 'dat', ['dat', 'minh']);
    expect(d.participants).toEqual(['dat', 'minh']);
    expect(d.items).toHaveLength(1);
    expect(d.rounding).toBe(1000);
    expect(d.id).toBeNull();
  });

  it('CUSTOM: tạm tính là tổng các số nhập', () => {
    const d = { ...createDraft('g', 'dat', ['dat', 'minh']), mode: 'CUSTOM' as const, custom: { dat: 30_000, minh: 20_000 }, rounding: 1 };
    const spec = toSpec(d);
    expect(spec.subtotal).toBe(50_000);
    expect(SplitEngine.split(spec, ['dat', 'minh']).total).toBe(50_000);
  });

  it('đi vòng Bill → nháp → spec không mất dữ liệu', () => {
    const spec = new BillSpec({
      payerId: 'dat',
      mode: 'ITEMIZED',
      subtotal: 0,
      items: [new BillItem('Bò', 300_000, ['dat']), new BillItem('Rau', 100_000, ['minh'])],
      vatPercent: 10,
      servicePercent: 5,
      rounding: 1,
    });
    const bill = new Bill('b1', 'g', 'Lẩu', 123, 'dat', spec, 'AN_UONG');
    const d = draftFrom(bill);
    expect(d.id).toBe('b1');
    expect(d.date).toBe(123);
    const back = toSpec(d);
    expect(back.items.map((i) => [i.name, i.price, [...i.consumers]])).toEqual([
      ['Bò', 300_000, ['dat']],
      ['Rau', 100_000, ['minh']],
    ]);
    expect(referencedIds(d)).toEqual(expect.arrayContaining(['dat', 'minh']));
  });

  it('chia % đều: phần dư phần vạn về người đầu', () => {
    expect(prefillPercent(['a', 'b', 'c'], 10_000)).toEqual({ a: 3334, b: 3333, c: 3333 });
    expect(prefillPercent([], 10_000)).toEqual({});
  });
});
