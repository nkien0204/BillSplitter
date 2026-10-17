import type { Transfer } from '../model/types';

interface Bal {
  readonly id: string;
  v: number;
  readonly order: number;
}

/**
 * Tối giản nợ bằng tham lam: ghép người nợ nhiều nhất với người được nợ nhiều nhất.
 * Cho tối đa (số người có số dư ≠ 0) − 1 giao dịch. Tìm số giao dịch ít nhất tuyệt đối là NP-khó;
 * thuật toán này không hứa điều đó.
 *
 * @param net số dư ròng mỗi người (dương = được nợ). Tổng phải bằng 0. Thứ tự map dùng để phá hoà.
 */
export function simplify(net: ReadonlyMap<string, number>): Transfer[] {
  let sum = 0;
  const cred: Bal[] = [];
  const debt: Bal[] = [];
  let i = 0;
  for (const [id, v] of net) {
    sum += v;
    if (v > 0) cred.push({ id, v, order: i });
    else if (v < 0) debt.push({ id, v: -v, order: i });
    i++;
  }
  if (sum !== 0) throw new Error('Tổng số dư phải bằng 0, đang là ' + sum);

  const out: Transfer[] = [];
  while (cred.length > 0 && debt.length > 0) {
    const c = largest(cred);
    const d = largest(debt);
    const x = Math.min(c.v, d.v);
    out.push({ fromId: d.id, toId: c.id, amount: x });
    c.v -= x;
    d.v -= x;
    if (c.v === 0) cred.splice(cred.indexOf(c), 1);
    if (d.v === 0) debt.splice(debt.indexOf(d), 1);
  }
  return out;
}

/** Lớn nhất theo số dư; hoà thì lấy người đứng trước trong map. */
function largest(list: readonly Bal[]): Bal {
  return list.reduce((best, b) => (b.v > best.v || (b.v === best.v && b.order < best.order) ? b : best));
}

export const DebtSimplifier = { simplify };
