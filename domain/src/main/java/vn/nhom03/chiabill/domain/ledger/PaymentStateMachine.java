package vn.nhom03.chiabill.domain.ledger;

import vn.nhom03.chiabill.domain.model.DebtStatus;

/**
 * Chuyển trạng thái khoản nợ. Mỗi bước chỉ một bên được làm.
 *
 * <pre>
 * PENDING  --người nợ: đã chuyển-->  MARKED_PAID  --người trả: đã nhận-->  CONFIRMED
 *    |                                   \--người trả: chưa nhận--> DISPUTED
 *    \--người trả: nhận tiền mặt--> CONFIRMED
 * DISPUTED --người nợ: đã chuyển lại--> MARKED_PAID
 * DISPUTED --người trả: đã nhận--> CONFIRMED
 * </pre>
 */
public final class PaymentStateMachine {
    public enum Role { DEBTOR, CREDITOR, OTHER }

    public enum Action {
        MARK_PAID(DebtStatus.MARKED_PAID),
        CONFIRM(DebtStatus.CONFIRMED),
        DISPUTE(DebtStatus.DISPUTED);

        private final DebtStatus target;
        Action(DebtStatus target) { this.target = target; }
        public DebtStatus target() { return target; }
    }

    private PaymentStateMachine() {}

    public static boolean canApply(DebtStatus from, Action action, Role role) {
        switch (action) {
            case MARK_PAID:
                return role == Role.DEBTOR && (from == DebtStatus.PENDING || from == DebtStatus.DISPUTED);
            case CONFIRM:
                return role == Role.CREDITOR && from != DebtStatus.CONFIRMED;
            case DISPUTE:
                return role == Role.CREDITOR && from == DebtStatus.MARKED_PAID;
            default:
                return false;
        }
    }

    /** @throws IllegalStateException nếu bước chuyển không hợp lệ */
    public static DebtStatus apply(DebtStatus from, Action action, Role role) {
        if (!canApply(from, action, role)) {
            throw new IllegalStateException(role + " không được " + action + " khi đang " + from);
        }
        return action.target();
    }

    public static Role roleOf(String userId, String debtorId, String creditorId) {
        if (userId.equals(debtorId)) return Role.DEBTOR;
        if (userId.equals(creditorId)) return Role.CREDITOR;
        return Role.OTHER;
    }
}
