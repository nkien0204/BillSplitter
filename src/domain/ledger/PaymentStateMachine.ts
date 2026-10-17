import type { DebtStatus } from '../model/types';

/**
 * Chuyển trạng thái khoản nợ. Mỗi bước chỉ một bên được làm.
 *
 *   PENDING  --người nợ: đã chuyển-->  MARKED_PAID  --người trả: đã nhận-->  CONFIRMED
 *      |                                   \--người trả: chưa nhận--> DISPUTED
 *      \--người trả: nhận tiền mặt--> CONFIRMED
 *   DISPUTED --người nợ: đã chuyển lại--> MARKED_PAID
 *   DISPUTED --người trả: đã nhận--> CONFIRMED
 */
export type Role = 'DEBTOR' | 'CREDITOR' | 'OTHER';
export type Action = 'MARK_PAID' | 'CONFIRM' | 'DISPUTE';

const TARGET: Record<Action, DebtStatus> = {
  MARK_PAID: 'MARKED_PAID',
  CONFIRM: 'CONFIRMED',
  DISPUTE: 'DISPUTED',
};

export function targetOf(action: Action): DebtStatus {
  return TARGET[action];
}

export function canApply(from: DebtStatus, action: Action, role: Role): boolean {
  switch (action) {
    case 'MARK_PAID':
      return role === 'DEBTOR' && (from === 'PENDING' || from === 'DISPUTED');
    case 'CONFIRM':
      return role === 'CREDITOR' && from !== 'CONFIRMED';
    case 'DISPUTE':
      return role === 'CREDITOR' && from === 'MARKED_PAID';
    default:
      return false;
  }
}

/** @throws Error nếu bước chuyển không hợp lệ */
export function apply(from: DebtStatus, action: Action, role: Role): DebtStatus {
  if (!canApply(from, action, role)) {
    throw new Error(`${role} không được ${action} khi đang ${from}`);
  }
  return TARGET[action];
}

export function roleOf(userId: string, debtorId: string, creditorId: string): Role {
  if (userId === debtorId) return 'DEBTOR';
  if (userId === creditorId) return 'CREDITOR';
  return 'OTHER';
}

export const PaymentStateMachine = { canApply, apply, roleOf, targetOf };
