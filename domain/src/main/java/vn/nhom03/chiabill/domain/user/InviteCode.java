package vn.nhom03.chiabill.domain.user;

import java.util.Random;

/**
 * Mã mời vào nhóm: 6 ký tự từ bảng chữ không gây nhầm (bỏ I, O, 0, 1).
 * Người dùng gõ thường, có khoảng trắng hay gạch nối đều được.
 */
public final class InviteCode {
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 6;

    private InviteCode() {}

    /** Chuẩn hoá chuỗi người dùng gõ; trả null nếu không phải mã hợp lệ. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toUpperCase(java.util.Locale.ROOT).toCharArray()) {
            if (c == ' ' || c == '-' || c == '.') continue;
            if (ALPHABET.indexOf(c) < 0) return null;
            sb.append(c);
        }
        return sb.length() == LENGTH ? sb.toString() : null;
    }

    public static String generate(Random r) {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) sb.append(ALPHABET.charAt(r.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    /** "DALAT7" → "DAL-AT7" cho dễ đọc. */
    public static String format(String code) {
        if (code == null || code.length() != LENGTH) return code == null ? "" : code;
        return code.substring(0, 3) + "-" + code.substring(3);
    }
}
