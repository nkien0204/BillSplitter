package vn.nhom03.chiabill.domain.model;

/** Một lần chuyển khoản đề xuất khi tất toán nhóm. */
public final class Transfer {
    private final String fromId;
    private final String toId;
    private final long amount;

    public Transfer(String fromId, String toId, long amount) {
        this.fromId = fromId;
        this.toId = toId;
        this.amount = amount;
    }

    public String getFromId() { return fromId; }
    public String getToId() { return toId; }
    public long getAmount() { return amount; }
}
