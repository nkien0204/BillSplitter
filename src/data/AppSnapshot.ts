import { Bill, type DebtStatus, Ledger, PaymentTarget, parseDebtStatus } from '@/domain';
import { toBill, toTarget } from './mappers';
import type { BillRecord, DebtStatusRecord, GroupRecord, InboxRecord, UserRecord } from './records';

/** Ảnh chụp bất biến của toàn bộ dữ liệu tại một thời điểm, kèm sổ nợ đã tính. UI chỉ đọc từ đây. */
export class AppSnapshot {
  private readonly userMap = new Map<string, UserRecord>();
  private readonly groupList: readonly GroupRecord[];
  /** Mọi thành viên từng ở nhóm, theo thứ tự: dùng để chia hoá đơn. */
  private readonly allMembers = new Map<string, string[]>();
  /** Thành viên hiện tại: dùng cho giao diện, quyền và hoá đơn mới. */
  private readonly members = new Map<string, string[]>();
  private readonly billRecords = new Map<string, BillRecord>();
  private readonly inboxList: readonly InboxRecord[];
  private readonly ledgerValue: Ledger;

  constructor(
    users: readonly UserRecord[],
    groups: readonly GroupRecord[],
    bills: readonly BillRecord[],
    statuses: readonly DebtStatusRecord[],
    inbox: readonly InboxRecord[],
  ) {
    for (const u of users) this.userMap.set(u.id, u);
    this.groupList = [...groups];
    for (const g of groups) {
      this.allMembers.set(g.id, [...g.memberOrder]);
      this.members.set(g.id, g.memberOrder.filter((id) => g.activeMemberIds.includes(id)));
    }
    const domainBills: Bill[] = [];
    for (const b of bills) {
      this.billRecords.set(b.id, b);
      try {
        domainBills.push(toBill(b));
      } catch {
        // Dữ liệu hỏng thì bỏ qua hoá đơn đó thay vì làm sập cả app.
      }
    }
    const st = new Map<string, DebtStatus>();
    for (const s of statuses) {
      const parsed = parseDebtStatus(s.status);
      if (parsed) st.set(s.key, parsed); // trạng thái lạ → coi như PENDING
    }
    this.inboxList = [...inbox].sort((a, b) => b.createdAt - a.createdAt);
    this.ledgerValue = new Ledger(this.allMembers, domainBills, st);
  }

  ledger(): Ledger {
    return this.ledgerValue;
  }

  user(id: string): UserRecord | undefined {
    return this.userMap.get(id);
  }

  name(id: string): string {
    return this.userMap.get(id)?.name ?? '?';
  }

  colorIndex(id: string): number {
    return this.userMap.get(id)?.colorIndex ?? 0;
  }

  accounts(): UserRecord[] {
    return [...this.userMap.values()].filter((u) => !u.guest);
  }

  paymentTarget(userId: string): PaymentTarget | null {
    return toTarget(this.userMap.get(userId));
  }

  groupsOf(userId: string): GroupRecord[] {
    return this.groupList.filter((g) => this.membersOf(g.id).includes(userId));
  }

  group(id: string): GroupRecord | undefined {
    return this.groupList.find((g) => g.id === id);
  }

  /** Thành viên hiện tại của nhóm. */
  membersOf(groupId: string): readonly string[] {
    return this.members.get(groupId) ?? [];
  }

  /** Mọi người từng ở nhóm, kể cả đã rời: thứ tự chia hoá đơn. */
  allMembersOf(groupId: string): readonly string[] {
    return this.allMembers.get(groupId) ?? [];
  }

  billRecord(billId: string): BillRecord | undefined {
    return this.billRecords.get(billId);
  }

  inboxOf(userId: string): InboxRecord[] {
    return this.inboxList.filter((e) => e.recipientId === userId);
  }
}
