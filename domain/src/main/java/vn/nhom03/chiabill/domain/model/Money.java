package vn.nhom03.chiabill.domain.model;

/** Tiền luôn là số nguyên VND (long). Lớp này chỉ định dạng, không làm phép tính. */
public final class Money {
    private Money() {}

    /** 1231200 → "1.231.200" */
    public static String dots(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder out = new StringBuilder();
        int lead = s.length() % 3;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (i - lead) % 3 == 0) out.append('.');
            out.append(s.charAt(i));
        }
        return (n < 0 ? "−" : "") + out;
    }

    /** 1231200 → "1.231.200đ" */
    public static String format(long n) {
        return dots(n) + "đ";
    }

    /** +50.000đ / −50.000đ / 0đ */
    public static String formatSigned(long n) {
        return (n > 0 ? "+" : "") + format(n);
    }

    /** Lấy phần số từ chuỗi người dùng gõ ("1.140.000" → 1140000). Rỗng → 0. Chặn tràn. */
    public static long parseDigits(String text, long max) {
        if (text == null) return 0;
        long v = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                v = v * 10 + (c - '0');
                if (v > max) return max;
            }
        }
        return v;
    }
}
