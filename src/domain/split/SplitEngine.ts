import { BillSpec } from '../model/BillSpec';
import { SplitResult } from '../model/SplitResult';
import type { ShareLine } from '../model/types';

/**
 * Chia đều hoá đơn cho những người tham gia. Mọi phép tính trên số nguyên VND.
 *
 *  1. Lấy người tham gia theo thứ tự memberOrder.
 *  2. Mỗi người nhận floor(tổng / n); r = tổng mod n đồng còn dư, phát mỗi người 1 đồng cho r người đầu.
 *  3. Người trả luôn có một dòng (phần 0 nếu không tham gia), để màn hình hiện "người trả".
 *
 * Bất biến: tổng các phần = tổng hoá đơn; mọi phần ≥ 0; cùng đầu vào thì cùng kết quả.
 *
 * @param spec        nội dung hoá đơn
 * @param memberOrder thứ tự thành viên của nhóm; quyết định thứ tự dòng và ai nhận đồng dư
 */
export function split(spec: BillSpec, memberOrder: readonly string[]): SplitResult {
  const people = inOrder(memberOrder, spec.participants);
  const parts = equal(spec.subtotal, people.length);

  const lines: ShareLine[] = people.map((id, i) => ({ memberId: id, amount: parts[i] }));
  if (!people.includes(spec.payerId)) lines.push({ memberId: spec.payerId, amount: 0 });
  return new SplitResult(lines, spec.subtotal);
}

/** Lỗi nhập liệu khiến hoá đơn chưa lưu được, hoặc null nếu hợp lệ. Chỉ tính những người có trong memberOrder. */
export function check(spec: BillSpec, memberOrder: readonly string[]): string | null {
  return inOrder(memberOrder, spec.participants).length === 0 ? 'Chọn ít nhất một người tham gia' : null;
}

/** Chia total thành n phần nguyên, chênh nhau tối đa 1 đồng; đồng dư về những người đầu. */
export function equal(total: number, n: number): number[] {
  if (n === 0) return [];
  const each = Math.floor(total / n);
  const rest = total - each * n;
  return Array.from({ length: n }, (_, i) => each + (i < rest ? 1 : 0));
}

function inOrder(order: readonly string[], subset: readonly string[]): string[] {
  return order.filter((id) => subset.includes(id));
}

export const SplitEngine = { split, check, equal };
