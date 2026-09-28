package vn.nhom03.chiabill.domain.model;

public enum DebtStatus {
    /** Chưa trả. */
    PENDING,
    /** Người nợ báo đã chuyển, chờ người được trả xác nhận. */
    MARKED_PAID,
    /** Người được trả xác nhận đã nhận. */
    CONFIRMED,
    /** Người được trả báo chưa nhận được. */
    DISPUTED;

    public boolean isOpen() {
        return this != CONFIRMED;
    }
}
