import { BillItem } from '../model/BillItem';
import { BillSpec } from '../model/BillSpec';
import type { JavaRandom } from './javaRandom';

/** Bộ sinh dữ liệu ngẫu nhiên có seed cố định, dùng cho property test (tái lập được). */
export function members(n: number): string[] {
  return Array.from({ length: n }, (_, i) => 'u' + i);
}

export function subset(r: JavaRandom, all: readonly string[], min: number): string[] {
  const out: string[] = [];
  for (const s of all) if (r.nextInt(3) > 0) out.push(s);
  while (out.length < min) {
    const pick = all[r.nextInt(all.length)];
    if (!out.includes(pick)) out.push(pick);
  }
  return out;
}

export function randomBill(r: JavaRandom, ms: readonly string[]): BillSpec {
  const payer = ms[r.nextInt(ms.length)];
  const vats = [0, 0, 8, 10];
  const svcs = [0, 0, 5, 10];
  const rounding = r.nextBoolean() ? 1 : 1000;
  const vatPercent = vats[r.nextInt(vats.length)];
  const servicePercent = svcs[r.nextInt(svcs.length)];
  const kind = r.nextInt(4);
  if (kind === 2) {
    const shares = new Map<string, number>();
    for (const id of subset(r, ms, 1)) {
      shares.set(id, r.nextInt(5) === 0 ? r.nextInt(999) + 1 : 1000 * (1 + r.nextInt(900)) + r.nextInt(1000));
    }
    return new BillSpec({ payerId: payer, mode: 'CUSTOM', subtotal: 0, vatPercent, servicePercent, rounding, shares });
  }
  if (kind === 3) {
    const who = subset(r, ms, 1);
    const shares = new Map<string, number>();
    let left = 10_000;
    for (let i = 0; i < who.length; i++) {
      const v = i === who.length - 1 ? left : r.nextInt(left + 1);
      shares.set(who[i], v);
      left -= v;
    }
    const subtotal = 1000 * (1 + r.nextInt(5000)) + r.nextInt(1000);
    return new BillSpec({ payerId: payer, mode: 'PERCENT', subtotal, vatPercent, servicePercent, rounding, shares });
  }
  if (kind === 0) {
    const subtotal = r.nextInt(5) === 0 ? r.nextInt(1000) : 1000 * (1 + r.nextInt(5000)) + r.nextInt(1000);
    return new BillSpec({
      payerId: payer,
      mode: 'EQUAL',
      subtotal,
      participants: subset(r, ms, 1),
      vatPercent,
      servicePercent,
      rounding,
    });
  }
  const items: BillItem[] = [];
  const n = 1 + r.nextInt(12);
  for (let i = 0; i < n; i++) {
    const price = r.nextInt(6) === 0 ? r.nextInt(999) + 1 : 1000 * (5 + r.nextInt(500));
    items.push(new BillItem('m' + i, price, subset(r, ms, 1)));
  }
  return new BillSpec({ payerId: payer, mode: 'ITEMIZED', subtotal: 0, items, vatPercent, servicePercent, rounding });
}
