/** Một món trên hoá đơn. price là tiền của cả dòng (đã nhân số lượng). */
export class BillItem {
  readonly name: string;
  readonly price: number;
  readonly consumers: readonly string[];

  constructor(name: string | null | undefined, price: number, consumers: readonly string[]) {
    if (price < 0) throw new Error('price < 0');
    this.name = name ?? '';
    this.price = price;
    this.consumers = [...consumers];
  }
}
