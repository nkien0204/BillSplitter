package vn.nhom03.chiabill.domain.model;

/** Danh mục khoản chi, dùng để lọc lịch sử. */
public enum Category {
    AN_UONG("Ăn uống"),
    DI_LAI("Đi lại"),
    LUU_TRU("Lưu trú"),
    MUA_SAM("Mua sắm"),
    GIAI_TRI("Giải trí"),
    KHAC("Khác");

    private final String label;

    Category(String label) { this.label = label; }

    public String label() { return label; }

    /** Đọc từ chuỗi đã lưu; giá trị lạ hoặc null thành KHAC. */
    public static Category parse(String s) {
        if (s != null) for (Category c : values()) if (c.name().equals(s)) return c;
        return KHAC;
    }
}
