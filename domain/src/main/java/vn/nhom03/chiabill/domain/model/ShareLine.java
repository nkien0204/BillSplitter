package vn.nhom03.chiabill.domain.model;

/** Phần của một người trong một hoá đơn, kèm cách tính. */
public final class ShareLine {
    private final String memberId;
    private final long base;
    private final long fee;
    private final long amount;
    private final long roundingAdj;

    public ShareLine(String memberId, long base, long fee, long amount, long roundingAdj) {
        this.memberId = memberId;
        this.base = base;
        this.fee = fee;
        this.amount = amount;
        this.roundingAdj = roundingAdj;
    }

    public String getMemberId() { return memberId; }
    /** Phần gốc (món hoặc chia đều), chưa phí. */
    public long getBase() { return base; }
    /** VAT + phí phục vụ phân bổ theo tỉ lệ phần gốc. */
    public long getFee() { return fee; }
    /** Số tiền cuối cùng sau làm tròn. */
    public long getAmount() { return amount; }
    /** amount − (base + fee). Âm hoặc dương vài trăm/nghìn đồng. */
    public long getRoundingAdj() { return roundingAdj; }
}
