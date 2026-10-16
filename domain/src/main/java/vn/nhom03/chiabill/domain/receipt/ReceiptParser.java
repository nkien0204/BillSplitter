package vn.nhom03.chiabill.domain.receipt;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc hoá đơn từ chữ mà OCR (ML Kit) nhận ra trong ảnh chụp. Thuần Java để test được.
 *
 * <p>Bước 1: {@link #rows} ghép các mảnh chữ cùng hàng ngang thành một dòng (OCR hay tách
 * tên món và giá thành hai khối). Bước 2: {@link #parse} tìm tổng tiền theo từ khoá
 * ("Tổng thanh toán", "Khách phải trả", "Total"...), nếu không có thì lấy số tiền lớn nhất;
 * kèm theo tên quán và danh sách món nếu đọc được.
 *
 * <p>Kết quả chỉ là gợi ý: app điền sẵn vào form và người dùng luôn xem lại trước khi lưu.
 */
public final class ReceiptParser {
    private ReceiptParser() {}

    /** Số tiền nhỏ hơn mức này coi như số lượng, số bàn... không phải tiền. */
    static final long MIN_AMOUNT = 1_000;
    static final long MAX_AMOUNT = 999_999_999L;

    /** Một mảnh chữ OCR trả về, kèm khung bao (pixel). */
    public static final class Fragment {
        final String text;
        final int left, top, right, bottom;

        public Fragment(String text, int left, int top, int right, int bottom) {
            this.text = text == null ? "" : text;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int centerY() { return (top + bottom) / 2; }
        int height() { return Math.max(1, bottom - top); }
    }

    /** Một món đọc được: tên và thành tiền của dòng đó. */
    public static final class Item {
        public final String name;
        public final long price;

        Item(String name, long price) {
            this.name = name;
            this.price = price;
        }
    }

    public static final class Result {
        /** Tổng tiền cần trả, 0 nếu không đọc được. */
        public final long total;
        /** Tên quán / dòng đầu hoá đơn, "" nếu không có. */
        public final String title;
        public final List<Item> items;
        /** true khi tổng các món đúng bằng tổng tiền, tức danh sách món đáng tin. */
        public final boolean itemsMatchTotal;
        /** true khi tổng tìm được nhờ từ khoá (đáng tin hơn khi chỉ lấy số lớn nhất). */
        public final boolean totalFromKeyword;

        Result(long total, String title, List<Item> items, boolean totalFromKeyword) {
            this.total = total;
            this.title = title;
            this.items = Collections.unmodifiableList(items);
            this.totalFromKeyword = totalFromKeyword;
            long sum = 0;
            for (Item i : items) sum += i.price;
            this.itemsMatchTotal = total > 0 && !items.isEmpty() && sum == total;
        }
    }

    // ------------------------------------------------------------------ ghép dòng

    /**
     * Ghép các mảnh có tâm dọc gần nhau (lệch ít hơn nửa chiều cao chữ) thành một dòng,
     * đọc từ trái sang phải; các dòng xếp từ trên xuống.
     */
    public static List<String> rows(List<Fragment> fragments) {
        List<Fragment> sorted = new ArrayList<>(fragments);
        sorted.sort(Comparator.comparingInt(Fragment::centerY));
        List<List<Fragment>> groups = new ArrayList<>();
        for (Fragment f : sorted) {
            if (f.text.trim().isEmpty()) continue;
            List<Fragment> last = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            if (last != null) {
                Fragment ref = last.get(0);
                int tolerance = Math.min(ref.height(), f.height()) / 2;
                if (Math.abs(f.centerY() - ref.centerY()) <= tolerance) {
                    last.add(f);
                    continue;
                }
            }
            List<Fragment> g = new ArrayList<>();
            g.add(f);
            groups.add(g);
        }
        List<String> out = new ArrayList<>();
        for (List<Fragment> g : groups) {
            g.sort(Comparator.comparingInt(fr -> fr.left));
            StringBuilder sb = new StringBuilder();
            for (Fragment fr : g) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(fr.text.trim());
            }
            out.add(sb.toString());
        }
        return out;
    }

    // ------------------------------------------------------------------ đọc số tiền

    // 150.000 | 1,250,000 | 150.000,00 | 150000 | 150k | 1.5tr. Không dính chữ cái hay / : - ở hai đầu.
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\p{L}\\d/:.,-])"
            + "(\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{2}(?!\\d))?|\\d+(?:[.,]\\d+)?\\s?(?:k|tr)\\b|\\d{4,9})"
            + "(?![\\d/:]|[.,]\\d)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Các số tiền trong một dòng, theo thứ tự xuất hiện. */
    public static List<Long> amountsIn(String line) {
        List<Long> out = new ArrayList<>();
        Matcher m = AMOUNT.matcher(line);
        while (m.find()) {
            long v = parseAmount(m.group(1));
            if (v >= MIN_AMOUNT && v <= MAX_AMOUNT) out.add(v);
        }
        return out;
    }

    /** "150.000" → 150000, "150k" → 150000, "1.5tr" → 1500000, "150.000,00" → 150000. -1 nếu không đọc được. */
    public static long parseAmount(String token) {
        String t = token.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        long unit = 1;
        if (t.endsWith("tr")) { unit = 1_000_000; t = t.substring(0, t.length() - 2); }
        else if (t.endsWith("k")) { unit = 1_000; t = t.substring(0, t.length() - 1); }
        try {
            if (unit > 1) {
                return Math.round(Double.parseDouble(t.replace(',', '.')) * unit);
            }
            // bỏ phần lẻ ",00" / ".00" ở cuối, rồi bỏ mọi dấu phân cách nghìn
            t = t.replaceFirst("[.,]\\d{2}$", "");
            return Long.parseLong(t.replace(".", "").replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ đọc hoá đơn

    // Thứ tự là độ ưu tiên: dòng "phải trả" đáng tin hơn dòng "tổng" chung chung.
    private static final String[][] TOTAL_KEYS = {
            {"khach phai tra", "can thanh toan", "tong thanh toan", "phai thanh toan", "tong tien thanh toan",
                    "thanh toan", "grand total", "amount due", "total due"},
            {"tong cong", "tong tien", "tong", "total", "cong"},
    };

    // Dòng có những chữ này không phải tổng phải trả và không phải món.
    private static final String[] NOT_TOTAL = {
            "tien mat", "khach dua", "tien thua", "tra lai", "tien khach", "change", "cash", "giam gia",
            "chiet khau", "discount", "tam tinh", "subtotal", "sub total", "thue", "vat", "tax", "phi dich vu",
            "service", "so luong", "tong sl", "the ", "card", "chuyen khoan", "diem",
    };

    private static final String[] NOT_TITLE = {
            "hoa don", "phieu", "receipt", "invoice", "dia chi", "dc:", "d/c", "dt:", "tel", "hotline", "mst",
            "ngay", "gio", "ban:", "so:", "thu ngan", "nhan vien", "wifi", "pass",
    };

    // LUỒNG 7 · Chụp hoá đơn · Bước 3/4: Ghép các mảnh thành hàng, tìm tên quán, tổng tiền và các món (tên + giá). Java thuần, có test.
    //   → BillEditActivity.applyReceipt
    public static Result parse(List<String> lines) {
        List<String> rows = new ArrayList<>();
        for (String l : lines) if (l != null && !l.trim().isEmpty()) rows.add(l.trim());

        long total = 0;
        int totalRow = -1;
        boolean fromKeyword = false;
        for (String[] keys : TOTAL_KEYS) {
            for (int i = 0; i < rows.size(); i++) {
                String n = normalize(rows.get(i));
                if (!containsAny(n, keys) || containsAny(n, NOT_TOTAL)) continue;
                long v = amountNear(rows, i);
                // nhiều dòng cùng mức ưu tiên: lấy số lớn nhất (tổng sau thuế, phí)
                if (v > total) {
                    total = v;
                    totalRow = i;
                    fromKeyword = true;
                }
            }
            if (fromKeyword) break;
        }
        if (!fromKeyword) {
            for (int i = 0; i < rows.size(); i++) {
                if (containsAny(normalize(rows.get(i)), NOT_TOTAL)) continue;
                for (long v : amountsIn(rows.get(i))) {
                    if (v > total) {
                        total = v;
                        totalRow = i;
                    }
                }
            }
        }

        return new Result(total, title(rows), items(rows, totalRow), fromKeyword);
    }

    /** Số tiền cuối của dòng i, hoặc số đầu tiên của một trong hai dòng kế tiếp (nhãn và số bị tách dòng). */
    private static long amountNear(List<String> rows, int i) {
        List<Long> here = amountsIn(rows.get(i));
        if (!here.isEmpty()) return here.get(here.size() - 1);
        for (int j = i + 1; j <= i + 2 && j < rows.size(); j++) {
            String next = rows.get(j);
            if (letters(next) > 3) continue; // dòng tiếp theo là chữ khác, không phải số của nhãn này
            List<Long> a = amountsIn(next);
            if (!a.isEmpty()) return a.get(0);
        }
        return 0;
    }

    private static String title(List<String> rows) {
        int limit = Math.min(rows.size(), 4);
        for (int i = 0; i < limit; i++) {
            String r = rows.get(i);
            String n = normalize(r);
            if (letters(r) < 4 || !amountsIn(r).isEmpty() || containsAny(n, NOT_TITLE)) continue;
            String t = r.replaceAll("\\s+", " ").trim();
            return t.length() > 40 ? t.substring(0, 40).trim() : t;
        }
        return "";
    }

    /** Các dòng phía trên dòng tổng có tên và số tiền: tên món + thành tiền (số cuối dòng). */
    private static List<Item> items(List<String> rows, int totalRow) {
        List<Item> out = new ArrayList<>();
        int end = totalRow < 0 ? rows.size() : totalRow;
        String pendingName = null;
        for (int i = 0; i < end; i++) {
            String r = rows.get(i);
            String n = normalize(r);
            if (containsAny(n, NOT_TOTAL) || containsAny(n, TOTAL_KEYS[0]) || containsAny(n, TOTAL_KEYS[1])) {
                pendingName = null;
                continue;
            }
            List<Long> amounts = amountsIn(r);
            String name = cleanName(r);
            if (amounts.isEmpty()) {
                pendingName = letters(name) >= 2 && !containsAny(n, NOT_TITLE) ? name : null;
                continue;
            }
            if (letters(name) < 2) {
                if (pendingName == null) continue;
                name = pendingName; // tên ở dòng trên, giá ở dòng này
            }
            pendingName = null;
            out.add(new Item(name, amounts.get(amounts.size() - 1)));
        }
        // Dòng tên quán / địa chỉ có thể lọt vào nếu chứa số; chỉ giữ danh sách khi có ít nhất một món.
        return out;
    }

    private static String cleanName(String row) {
        String s = AMOUNT.matcher(row).replaceAll(" ");
        s = s.replaceAll("(?i)\\b\\d+\\s*[x×]\\b|\\b[x×]\\s*\\d+\\b", " "); // "2 x", "x2"
        s = s.replaceAll("(?i)\\b(vnd|vnđ|đ|d)\\b", " ");
        s = s.replaceAll("[₫|:*]", " ");
        s = s.replaceAll("^\\s*\\d+[.)]?\\s+", ""); // số thứ tự đầu dòng
        s = s.replaceAll("\\s+\\d{1,2}\\s*$", ""); // số lượng cuối dòng
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Chữ thường, bỏ dấu tiếng Việt, đ → d; để so từ khoá không phụ thuộc OCR đọc đúng dấu. */
    static String normalize(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd');
        return " " + n.replaceAll("\\s+", " ").trim() + " ";
    }

    private static boolean containsAny(String normalized, String[] keys) {
        for (String k : keys) {
            // khớp trọn từ: "tong" không khớp "tonga"; "cong" không khớp "công ty"
            String tail = k.equals("cong") ? "(?!\\s*ty)" : "";
            if (Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(k) + "([^a-z0-9]|$)" + tail).matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    private static int letters(String s) {
        int c = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) c++;
        return c;
    }
}
