/** Người nhận tiền: mã BIN ngân hàng (6 số) + số tài khoản, tên chủ tài khoản nếu biết. */
export class PaymentTarget {
  readonly accountName: string;

  constructor(
    readonly bankBin: string,
    readonly accountNo: string,
    accountName?: string | null,
  ) {
    if (bankBin == null) throw new Error('bankBin is required');
    if (accountNo == null) throw new Error('accountNo is required');
    this.accountName = accountName ?? '';
  }

  /** 1012345678 → 101•••678 */
  maskedAccount(): string {
    if (this.accountNo.length <= 6) return this.accountNo;
    return this.accountNo.substring(0, 3) + '•••' + this.accountNo.substring(this.accountNo.length - 3);
  }

  static isValidBin(bin: string | null | undefined): boolean {
    return bin != null && /^[0-9]{6}$/.test(bin);
  }

  static isValidAccount(acc: string | null | undefined): boolean {
    return acc != null && /^[0-9A-Za-z]{6,19}$/.test(acc);
  }
}
