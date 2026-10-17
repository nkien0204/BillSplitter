/**
 * Chia một số nguyên theo trọng số bằng phương pháp phần dư lớn nhất (largest remainder).
 * Tổng kết quả luôn bằng đúng total. Hoà phần dư thì ưu tiên chỉ số nhỏ hơn, nên kết quả tất định.
 *
 * Tích total × weight có thể vượt 2^53 nên tính bằng BigInt; kết quả luôn nằm trong [0, total].
 */
export function allocate(total: number, weights: readonly number[]): number[] {
  if (total < 0) throw new Error('total < 0');
  const out = new Array<number>(weights.length).fill(0);
  let sumW = 0n;
  for (const w of weights) {
    if (w < 0) throw new Error('weight < 0');
    sumW += BigInt(w);
  }
  if (sumW === 0n || total === 0) return out;

  const bigTotal = BigInt(total);
  const rem = new Array<bigint>(weights.length).fill(0n);
  let assigned = 0n;
  for (let i = 0; i < weights.length; i++) {
    const p = bigTotal * BigInt(weights[i]);
    const q = p / sumW;
    out[i] = Number(q);
    rem[i] = p % sumW;
    assigned += q;
  }
  const left = Number(bigTotal - assigned); // 0 <= left < số phần tử có trọng số > 0
  const order: number[] = [];
  for (let i = 0; i < weights.length; i++) if (weights[i] > 0) order.push(i);
  order.sort((a, b) => (rem[a] !== rem[b] ? (rem[b] > rem[a] ? 1 : -1) : a - b));
  for (let k = 0; k < left; k++) out[order[k]]++;
  return out;
}

/** Chia đều cho n phần. */
export function equal(total: number, n: number): number[] {
  return allocate(total, new Array<number>(n).fill(1));
}

export const Allocator = { allocate, equal };
