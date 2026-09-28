package vn.nhom03.chiabill.domain.user;

/**
 * Số điện thoại di động Việt Nam, chuẩn hoá về dạng 10 số bắt đầu bằng 0 (vd 0912345678).
 * Nhận các cách gõ thường gặp: có khoảng trắng, dấu chấm, gạch; tiền tố +84 hoặc 84.
 * Số điện thoại là khoá để người dùng tìm thấy nhau, nên hai cách gõ của cùng một số phải ra cùng một chuỗi.
 */
public final class PhoneNumber {
    private PhoneNumber() {}

    /** @return số đã chuẩn hoá, hoặc null nếu không phải số di động Việt Nam hợp lệ. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        StringBuilder digits = new StringBuilder();
        String s = raw.trim();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
            else if (c == '+' && digits.length() == 0 && i == 0) continue;
            else if (c == ' ' || c == '.' || c == '-' || c == '(' || c == ')') continue;
            else return null;
        }
        String d = digits.toString();
        if (d.startsWith("84") && d.length() == 11) d = "0" + d.substring(2);
        if (d.length() != 10 || d.charAt(0) != '0') return null;
        char network = d.charAt(1);
        if (network != '3' && network != '5' && network != '7' && network != '8' && network != '9') return null;
        return d;
    }

    public static boolean isValid(String raw) {
        return normalize(raw) != null;
    }

    /** 0912345678 → 0912 345 678 */
    public static String format(String normalized) {
        if (normalized == null || normalized.length() != 10) return normalized == null ? "" : normalized;
        return normalized.substring(0, 4) + " " + normalized.substring(4, 7) + " " + normalized.substring(7);
    }

    /** 0912345678 → 0912 ••• 678, dùng khi hiện cho người chưa cùng nhóm. */
    public static String masked(String normalized) {
        if (normalized == null || normalized.length() != 10) return "";
        return normalized.substring(0, 4) + " ••• " + normalized.substring(7);
    }
}
