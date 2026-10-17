import { BillItem } from './BillItem';
import type { SplitMode } from './types';

export interface BillSpecInit {
  payerId: string;
  mode: SplitMode;
  subtotal: number;
  participants?: readonly string[] | null;
  items?: readonly BillItem[] | null;
  vatPercent: number;
  servicePercent: number;
  rounding: number;
  /** Chỉ dùng cho CUSTOM (VND mỗi người) và PERCENT (phần vạn, 1% = 100). */
  shares?: ReadonlyMap<string, number> | Readonly<Record<string, number>> | null;
}

/**
 * Mọi thứ SplitEngine cần để chia một hoá đơn. Bất biến (immutable).
 * vatPercent, servicePercent tính theo phần trăm nguyên; rounding là 1 hoặc 1000.
 */
export class BillSpec {
  readonly payerId: string;
  readonly mode: SplitMode;
  readonly subtotal: number;
  readonly participants: readonly string[];
  readonly items: readonly BillItem[];
  readonly vatPercent: number;
  readonly servicePercent: number;
  readonly rounding: number;
  /** Chỉ chứa các giá trị > 0, giữ thứ tự chèn. */
  readonly shares: ReadonlyMap<string, number>;

  constructor(init: BillSpecInit) {
    if (init.payerId == null) throw new Error('payerId is required');
    if (init.subtotal < 0) throw new Error('subtotal < 0');
    if (
      init.vatPercent < 0 ||
      init.vatPercent > 100 ||
      init.servicePercent < 0 ||
      init.servicePercent > 100
    ) {
      throw new Error('percent out of range');
    }
    if (init.rounding < 1) throw new Error('rounding < 1');
    this.payerId = init.payerId;
    this.mode = init.mode;
    this.subtotal = init.subtotal;
    this.participants = [...(init.participants ?? [])];
    this.items = [...(init.items ?? [])];
    this.vatPercent = init.vatPercent;
    this.servicePercent = init.servicePercent;
    this.rounding = init.rounding;
    const sh = new Map<string, number>();
    const entries: Iterable<[string, number | null | undefined]> =
      init.shares == null
        ? []
        : init.shares instanceof Map
          ? init.shares.entries()
          : Object.entries(init.shares);
    for (const [k, v] of entries) {
      if (k == null || v == null) continue;
      if (v < 0) throw new Error('share < 0');
      if (v > 0) sh.set(k, v);
    }
    this.shares = sh;
  }
}
