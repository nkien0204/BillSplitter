import type { IntRandom } from '../user/InviteCode';

/**
 * java.util.Random (LCG 48 bit) để test dùng đúng dãy số với bộ test JUnit cũ:
 * cùng seed thì cùng dữ liệu thử, nên kết quả hai bản so sánh được.
 */
export class JavaRandom implements IntRandom {
  private seed: bigint;
  private static readonly MASK = (1n << 48n) - 1n;
  private static readonly MULT = 0x5deece66dn;

  constructor(seed: number | bigint) {
    this.seed = (BigInt(seed) ^ JavaRandom.MULT) & JavaRandom.MASK;
  }

  private next(bits: number): number {
    this.seed = (this.seed * JavaRandom.MULT + 0xbn) & JavaRandom.MASK;
    // lấy 32 bit cao của (seed >> (48 - bits)) rồi đổi sang số có dấu 32 bit
    return Number(BigInt.asIntN(32, this.seed >> BigInt(48 - bits)));
  }

  nextInt(bound?: number): number {
    if (bound === undefined) return this.next(32);
    if (bound <= 0) throw new Error('bound must be positive');
    let r = this.next(31);
    const m = bound - 1;
    if ((bound & m) === 0) {
      return Number((BigInt(bound) * BigInt(r)) >> 31n);
    }
    for (let u = r; u - (r = u % bound) + m > 0x7fffffff; u = this.next(31)) {
      // lặp lại cho tới khi không thiên lệch
    }
    return r;
  }

  nextBoolean(): boolean {
    return this.next(1) !== 0;
  }
}
