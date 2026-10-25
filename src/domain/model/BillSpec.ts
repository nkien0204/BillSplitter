export interface BillSpecInit {
  payerId: string;
  /** Tổng hoá đơn (VND). */
  subtotal: number;
  /** Những người cùng chia đều hoá đơn. */
  participants?: readonly string[] | null;
}

/**
 * Mọi thứ SplitEngine cần để chia một hoá đơn. Bất biến (immutable).
 * Chỉ có một cách chia: tổng hoá đơn chia đều cho những người tham gia.
 */
export class BillSpec {
  readonly payerId: string;
  readonly subtotal: number;
  readonly participants: readonly string[];

  constructor(init: BillSpecInit) {
    if (init.payerId == null) throw new Error('payerId is required');
    if (init.subtotal < 0) throw new Error('subtotal < 0');
    this.payerId = init.payerId;
    this.subtotal = init.subtotal;
    this.participants = [...(init.participants ?? [])];
  }
}
