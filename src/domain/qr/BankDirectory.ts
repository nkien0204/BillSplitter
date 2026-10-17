/** Bảng BIN → tên ngân hàng, đóng gói trong app. Cập nhật theo bản phát hành. */
const BANKS: ReadonlyMap<string, string> = new Map([
  ['970436', 'Vietcombank'],
  ['970418', 'BIDV'],
  ['970415', 'VietinBank'],
  ['970405', 'Agribank'],
  ['970407', 'Techcombank'],
  ['970422', 'MB Bank'],
  ['970416', 'ACB'],
  ['970432', 'VPBank'],
  ['970423', 'TPBank'],
  ['970403', 'Sacombank'],
  ['970437', 'HDBank'],
  ['970441', 'VIB'],
]);

export function all(): ReadonlyMap<string, string> {
  return BANKS;
}

export function nameOf(bin: string): string {
  return BANKS.get(bin) ?? 'Ngân hàng BIN ' + bin;
}

export const BankDirectory = { all, nameOf };
