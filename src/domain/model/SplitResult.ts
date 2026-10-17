import type { ShareLine } from './types';

export class SplitResult {
  constructor(
    readonly lines: readonly ShareLine[],
    readonly subtotal: number,
    readonly vat: number,
    readonly service: number,
    readonly total: number,
    /** Tên các món có giá nhưng chưa gán cho ai (chỉ ở chế độ ITEMIZED). */
    readonly unassignedItems: readonly string[],
  ) {}

  sumOfShares(): number {
    let s = 0;
    for (const l of this.lines) s += l.amount;
    return s;
  }

  /** Bất biến chính: tổng các phần bằng đúng tổng hoá đơn. */
  isBalanced(): boolean {
    return this.sumOfShares() === this.total;
  }

  /** Lưu được khi có tiền, không còn món chưa gán, và tổng khớp. */
  isSavable(): boolean {
    return this.total > 0 && this.unassignedItems.length === 0 && this.isBalanced();
  }

  lineOf(memberId: string): ShareLine | null {
    return this.lines.find((l) => l.memberId === memberId) ?? null;
  }
}
