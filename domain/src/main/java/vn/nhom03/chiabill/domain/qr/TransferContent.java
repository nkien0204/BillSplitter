package vn.nhom03.chiabill.domain.qr;

import java.text.Normalizer;
import java.util.Locale;

/** Nội dung chuyển khoản: chữ không dấu in hoa, số, khoảng trắng. Ngân hàng hay từ chối ký tự khác. */
public final class TransferContent {
    private TransferContent() {}

    public static String toAscii(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replace('đ', 'd').replace('Đ', 'D');
    }

    public static String sanitize(String s) {
        String a = toAscii(s).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", " ").replaceAll(" +", " ").trim();
        return a.length() > VietQrCodec.MAX_CONTENT ? a.substring(0, VietQrCodec.MAX_CONTENT).trim() : a;
    }

    /** Nội dung gợi ý cho một khoản nợ, vd "CB 7K2Q DAT". */
    public static String forDebt(String billId, String debtorName) {
        String code = billId.replaceAll("[^A-Za-z0-9]", "");
        if (code.length() > 6) code = code.substring(code.length() - 6);
        return sanitize("CB " + code + " " + debtorName);
    }
}
