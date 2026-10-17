import { BillSpec } from '../model/BillSpec';
import { SplitResult } from '../model/SplitResult';
import type { ShareLine } from '../model/types';
import { allocate, equal } from './Allocator';

/** Tổng phần trăm (phần vạn) bắt buộc của chế độ PERCENT. */
export const PERCENT_TOTAL = 10_000;

/**
 * Chia hoá đơn thành phần của từng người. Mọi phép tính trên số nguyên VND.
 *
 *  1. Phần gốc: chia đều tạm tính; chia từng món cho người dùng món đó; mỗi người một số tiền
 *     (CUSTOM); hoặc chia tạm tính theo phần trăm (PERCENT, phần dư lớn nhất).
 *  2. VAT và phí phục vụ tính trên tạm tính (làm tròn nửa lên), phân bổ theo tỉ lệ phần gốc.
 *  3. Người nợ được làm tròn theo mức nhóm chọn; phần chênh dồn vào người trả.
 *
 * Bất biến: tổng các phần = tổng hoá đơn; mọi phần ≥ 0; kết quả tất định.
 *
 * @param spec        nội dung hoá đơn
 * @param memberOrder thứ tự thành viên của nhóm; quyết định thứ tự dòng và thứ tự phát phần dư
 */
export function split(spec: BillSpec, memberOrder: readonly string[]): SplitResult {
  const base = new Map<string, number>();
  for (const id of memberOrder) base.set(id, 0);
  const unassigned: string[] = [];
  const add = (id: string, v: number) => base.set(id, (base.get(id) ?? 0) + v);

  if (spec.mode === 'EQUAL') {
    const ps = inOrder(memberOrder, spec.participants);
    const parts = equal(spec.subtotal, ps.length);
    ps.forEach((id, i) => add(id, parts[i]));
  } else if (spec.mode === 'CUSTOM') {
    for (const id of memberOrder) {
      const v = spec.shares.get(id);
      if (v != null) base.set(id, v);
    }
  } else if (spec.mode === 'PERCENT') {
    const ps = memberOrder.filter((id) => spec.shares.has(id));
    const w = ps.map((id) => spec.shares.get(id) as number);
    const parts = allocate(spec.subtotal, w);
    ps.forEach((id, i) => base.set(id, parts[i]));
  } else {
    for (const it of spec.items) {
      const who = inOrder(memberOrder, it.consumers);
      if (who.length === 0) {
        if (it.price > 0) unassigned.push(it.name.trim() === '' ? '(món chưa đặt tên)' : it.name.trim());
        continue;
      }
      const parts = equal(it.price, who.length);
      who.forEach((id, i) => add(id, parts[i]));
    }
  }

  const payer = spec.payerId;
  const ids: string[] = [];
  for (const id of memberOrder) if ((base.get(id) as number) > 0 || id === payer) ids.push(id);
  if (!ids.includes(payer)) ids.push(payer); // người trả không nằm trong danh sách nhóm: vẫn có dòng

  const bw = ids.map((id) => base.get(id) ?? 0);
  const subtotal = bw.reduce((a, b) => a + b, 0);
  const vat = percentOf(subtotal, spec.vatPercent);
  const service = percentOf(subtotal, spec.servicePercent);
  const total = subtotal + vat + service;
  const va = allocate(vat, bw);
  const sa = allocate(service, bw);

  const raw = ids.map((_, i) => bw[i] + va[i] + sa[i]);

  let amt = roundDebtors(ids, raw, payer, total, spec.rounding, false);
  const payerIdx = ids.indexOf(payer);
  if (amt[payerIdx] < 0) {
    // Làm tròn gần nhất khiến người trả bị âm (người trả dùng rất ít hoặc không dùng):
    // chuyển sang làm tròn xuống cho người nợ, người trả luôn ≥ phần thật của mình.
    amt = roundDebtors(ids, raw, payer, total, spec.rounding, true);
  }

  const lines: ShareLine[] = ids.map((id, i) => ({
    memberId: id,
    base: bw[i],
    fee: va[i] + sa[i],
    amount: amt[i],
    roundingAdj: amt[i] - raw[i],
  }));
  return new SplitResult(lines, subtotal, vat, service, total, unassigned);
}

/**
 * Lỗi nhập liệu khiến hoá đơn chưa lưu được, hoặc null nếu hợp lệ.
 * Chỉ tính những người có trong memberOrder.
 */
export function check(spec: BillSpec, memberOrder: readonly string[]): string | null {
  switch (spec.mode) {
    case 'EQUAL':
      return inOrder(memberOrder, spec.participants).length === 0 ? 'Chọn ít nhất một người tham gia' : null;
    case 'CUSTOM': {
      let sum = 0;
      for (const id of memberOrder) sum += spec.shares.get(id) ?? 0;
      return sum > 0 ? null : 'Nhập số tiền cho ít nhất một người';
    }
    case 'PERCENT': {
      let sum = 0;
      for (const id of memberOrder) sum += spec.shares.get(id) ?? 0;
      return sum !== PERCENT_TOTAL
        ? 'Tổng phần trăm đang là ' + formatPercent(sum) + ', cần đúng 100%'
        : null;
    }
    default:
      return null;
  }
}

/**
 * "12,5" hoặc "12.5" → 1250 phần vạn. Rỗng → 0. Sai định dạng, quá 2 chữ số thập phân hoặc > 100 → -1.
 */
export function parsePercent(text: string | null | undefined): number {
  if (text == null) return 0;
  let t = text.trim().replace(/\./g, ',');
  if (t.endsWith('%')) t = t.substring(0, t.length - 1).trim();
  if (t === '') return 0;
  const comma = t.indexOf(',');
  let whole = comma < 0 ? t : t.substring(0, comma);
  const frac = comma < 0 ? '' : t.substring(comma + 1);
  if (whole === '') whole = '0';
  if (whole.length > 3 || frac.length > 2 || frac.indexOf(',') >= 0) return -1;
  if (!/^[0-9]*$/.test(whole + frac)) return -1;
  const bp =
    parseInt(whole, 10) * 100 + (frac === '' ? 0 : parseInt(frac.length === 1 ? frac + '0' : frac, 10));
  return bp > PERCENT_TOTAL ? -1 : bp;
}

/** 1250 phần vạn → "12,5%". */
export function formatPercent(basisPoints: number): string {
  const whole = Math.trunc(basisPoints / 100);
  const frac = Math.abs(basisPoints % 100);
  if (frac === 0) return whole + '%';
  const f = frac % 10 === 0 ? String(frac / 10) : String(frac).padStart(2, '0');
  return whole + ',' + f + '%';
}

function roundDebtors(
  ids: readonly string[],
  raw: readonly number[],
  payer: string,
  total: number,
  step: number,
  floor: boolean,
): number[] {
  const amt = new Array<number>(raw.length).fill(0);
  let others = 0;
  let payerIdx = -1;
  for (let i = 0; i < ids.length; i++) {
    if (ids[i] === payer) {
      payerIdx = i;
      continue;
    }
    amt[i] =
      step <= 1
        ? raw[i]
        : floor
          ? Math.floor(raw[i] / step) * step
          : Math.floor((raw[i] + Math.floor(step / 2)) / step) * step;
    others += amt[i];
  }
  amt[payerIdx] = total - others;
  return amt;
}

/** value × percent / 100, làm tròn nửa lên. */
export function percentOf(value: number, percent: number): number {
  const p = value * percent;
  if (!Number.isSafeInteger(p)) throw new RangeError('integer overflow');
  return Math.floor((p + 50) / 100);
}

function inOrder(order: readonly string[], subset: readonly string[]): string[] {
  return order.filter((id) => subset.includes(id));
}

export const SplitEngine = { split, check, parsePercent, formatPercent, percentOf, PERCENT_TOTAL };
