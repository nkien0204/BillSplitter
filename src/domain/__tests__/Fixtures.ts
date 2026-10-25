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
  const subtotal = r.nextInt(5) === 0 ? r.nextInt(1000) : 1000 * (1 + r.nextInt(5000)) + r.nextInt(1000);
  return new BillSpec({ payerId: payer, subtotal, participants: subset(r, ms, 1) });
}
