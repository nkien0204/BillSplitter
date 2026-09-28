package vn.nhom03.chiabill.domain.qr;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mã hoá/giải mã trường TLV kiểu EMVCo: ID 2 chữ số, độ dài 2 chữ số, rồi giá trị. */
public final class Tlv {
    private Tlv() {}

    public static final class TlvException extends Exception {
        private final QrError error;
        TlvException(QrError error) {
            super(error.name());
            this.error = error;
        }
        public QrError getError() { return error; }
    }

    public static String field(String id, String value) {
        if (value.length() > 99) throw new IllegalArgumentException("TLV value > 99 chars: " + id);
        return id + (value.length() < 10 ? "0" : "") + value.length() + value;
    }

    public static Map<String, String> parse(String s) throws TlvException {
        Map<String, String> out = new LinkedHashMap<>();
        int i = 0;
        while (i < s.length()) {
            if (i + 4 > s.length()) throw new TlvException(QrError.TRUNCATED);
            String id = s.substring(i, i + 2);
            String lenStr = s.substring(i + 2, i + 4);
            if (!isDigits(id) || !isDigits(lenStr)) throw new TlvException(QrError.NOT_EMV);
            int len = Integer.parseInt(lenStr);
            if (i + 4 + len > s.length()) throw new TlvException(QrError.TRUNCATED);
            out.put(id, s.substring(i + 4, i + 4 + len));
            i += 4 + len;
        }
        return out;
    }

    private static boolean isDigits(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        return !s.isEmpty();
    }
}
