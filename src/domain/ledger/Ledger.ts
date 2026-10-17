import { Bill } from '../model/Bill';
import type { SplitResult } from '../model/SplitResult';
import { type Debt, type DebtStatus, type Transfer, debtKeyOf, isOpen } from '../model/types';
import { split } from '../split/SplitEngine';
import { simplify } from './DebtSimplifier';

/**
 * Sổ nợ của toàn bộ dữ liệu: từ hoá đơn + trạng thái khoản nợ suy ra khoản nợ, số dư, tất toán.
 * Bất biến, dựng lại mỗi khi dữ liệu đổi (dữ liệu của app nhỏ nên không cần tăng dần).
 */
export class Ledger {
  private readonly billList: readonly Bill[];
  private readonly splits = new Map<string, SplitResult>();
  private readonly debtList: Debt[] = [];

  /**
   * @param groupMembers groupId → mọi thành viên từng ở nhóm, theo thứ tự
   * @param bills        mọi hoá đơn
   * @param statuses     khoá debtKeyOf → trạng thái; thiếu = PENDING
   */
  constructor(
    private readonly groupMembers: ReadonlyMap<string, readonly string[]>,
    bills: readonly Bill[],
    statuses: ReadonlyMap<string, DebtStatus>,
  ) {
    this.billList = [...bills];
    for (const b of this.billList) {
      const order = groupMembers.get(b.groupId) ?? [];
      const r = split(b.spec, order);
      this.splits.set(b.id, r);
      for (const l of r.lines) {
        if (l.memberId === b.payerId || l.amount <= 0) continue;
        this.debtList.push({
          key: debtKeyOf(b.id, l.memberId),
          billId: b.id,
          groupId: b.groupId,
          debtorId: l.memberId,
          creditorId: b.payerId,
          amount: l.amount,
          status: statuses.get(debtKeyOf(b.id, l.memberId)) ?? 'PENDING',
        });
      }
    }
  }

  bills(): readonly Bill[] {
    return this.billList;
  }

  splitOf(billId: string): SplitResult | undefined {
    return this.splits.get(billId);
  }

  bill(billId: string): Bill | undefined {
    return this.billList.find((b) => b.id === billId);
  }

  billsOfGroup(groupId: string): Bill[] {
    return this.billList.filter((b) => b.groupId === groupId);
  }

  debts(): readonly Debt[] {
    return this.debtList;
  }

  debt(key: string): Debt | undefined {
    return this.debtList.find((d) => d.key === key);
  }

  debtsOfGroup(groupId: string): Debt[] {
    return this.debtList.filter((d) => d.groupId === groupId);
  }

  debtsOfBill(billId: string): Debt[] {
    return this.debtList.filter((d) => d.billId === billId);
  }

  /** Số tiền người khác còn nợ userId (chưa xác nhận), trong nhóm hoặc toàn bộ (groupId = null). */
  owedTo(userId: string, groupId: string | null): number {
    let s = 0;
    for (const d of this.debtList) {
      if (!isOpen(d.status) || d.creditorId !== userId) continue;
      if (groupId == null || d.groupId === groupId) s += d.amount;
    }
    return s;
  }

  /** Số tiền userId còn nợ người khác (chưa xác nhận). */
  owedBy(userId: string, groupId: string | null): number {
    let s = 0;
    for (const d of this.debtList) {
      if (!isOpen(d.status) || d.debtorId !== userId) continue;
      if (groupId == null || d.groupId === groupId) s += d.amount;
    }
    return s;
  }

  /** Số dư ròng mỗi thành viên nhóm trên các khoản chưa xác nhận. */
  netBalances(groupId: string): Map<string, number> {
    const net = new Map<string, number>();
    for (const id of this.groupMembers.get(groupId) ?? []) net.set(id, 0);
    for (const d of this.debtsOfGroup(groupId)) {
      if (!isOpen(d.status)) continue;
      net.set(d.creditorId, (net.get(d.creditorId) ?? 0) + d.amount);
      net.set(d.debtorId, (net.get(d.debtorId) ?? 0) - d.amount);
    }
    return net;
  }

  settlement(groupId: string): Transfer[] {
    return simplify(this.netBalances(groupId));
  }

  openDebtCount(groupId: string): number {
    return this.debtsOfGroup(groupId).filter((d) => isOpen(d.status)).length;
  }
}
