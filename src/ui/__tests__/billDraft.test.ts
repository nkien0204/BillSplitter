import { Bill, BillSpec } from '@/domain';
import { createDraft, draftFrom, referencedIds, toSpec } from '../bill/billDraft';

describe('billDraft', () => {
  it('bản nháp mới: chia đều cho cả nhóm, người trả là mình', () => {
    const d = createDraft('g', 'dat', ['dat', 'minh']);
    expect(d.participants).toEqual(['dat', 'minh']);
    expect(d.payerId).toBe('dat');
    expect(d.id).toBeNull();
  });

  it('đi vòng Bill → nháp → spec không mất dữ liệu', () => {
    const spec = new BillSpec({ payerId: 'dat', subtotal: 400_000, participants: ['dat', 'minh'] });
    const bill = new Bill('b1', 'g', 'Lẩu', 123, 'dat', spec, 'AN_UONG');
    const d = draftFrom(bill);
    expect(d.id).toBe('b1');
    expect(d.date).toBe(123);
    const back = toSpec(d);
    expect(back.subtotal).toBe(400_000);
    expect(back.participants).toEqual(['dat', 'minh']);
    expect(referencedIds(d)).toEqual(expect.arrayContaining(['dat', 'minh']));
  });
});
