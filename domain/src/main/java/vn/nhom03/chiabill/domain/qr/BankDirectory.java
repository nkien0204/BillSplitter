package vn.nhom03.chiabill.domain.qr;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bảng BIN → tên ngân hàng, đóng gói trong app. Cập nhật theo bản phát hành. */
public final class BankDirectory {
    private static final Map<String, String> BANKS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("970436", "Vietcombank");
        m.put("970418", "BIDV");
        m.put("970415", "VietinBank");
        m.put("970405", "Agribank");
        m.put("970407", "Techcombank");
        m.put("970422", "MB Bank");
        m.put("970416", "ACB");
        m.put("970432", "VPBank");
        m.put("970423", "TPBank");
        m.put("970403", "Sacombank");
        m.put("970437", "HDBank");
        m.put("970441", "VIB");
        BANKS = Collections.unmodifiableMap(m);
    }

    private BankDirectory() {}

    public static Map<String, String> all() { return BANKS; }

    public static String nameOf(String bin) {
        String n = BANKS.get(bin);
        return n != null ? n : "Ngân hàng BIN " + bin;
    }
}
