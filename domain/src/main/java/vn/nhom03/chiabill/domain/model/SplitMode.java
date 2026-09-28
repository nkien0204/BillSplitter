package vn.nhom03.chiabill.domain.model;

public enum SplitMode {
    /** Chia đều tạm tính cho những người tham gia. */
    EQUAL,
    /** Mỗi món chia đều cho những người dùng món đó. */
    ITEMIZED,
    /** Mỗi người một số tiền tự nhập (VND). Tạm tính = tổng các số đó. */
    CUSTOM,
    /** Chia tạm tính theo phần trăm; lưu theo phần vạn (1% = 100), tổng phải đúng 10 000. */
    PERCENT
}
