package vn.nhom03.chiabill.domain.model;

/** Khoản nợ của một người với người trả của một hoá đơn. Khoá tất định: billId:debtorId. */
public final class Debt {
    private final String billId;
    private final String groupId;
    private final String debtorId;
    private final String creditorId;
    private final long amount;
    private final DebtStatus status;

    public Debt(String billId, String groupId, String debtorId, String creditorId, long amount, DebtStatus status) {
        this.billId = billId;
        this.groupId = groupId;
        this.debtorId = debtorId;
        this.creditorId = creditorId;
        this.amount = amount;
        this.status = status;
    }

    public static String keyOf(String billId, String debtorId) {
        return billId + ":" + debtorId;
    }

    public String getKey() { return keyOf(billId, debtorId); }
    public String getBillId() { return billId; }
    public String getGroupId() { return groupId; }
    public String getDebtorId() { return debtorId; }
    public String getCreditorId() { return creditorId; }
    public long getAmount() { return amount; }
    public DebtStatus getStatus() { return status; }
}
