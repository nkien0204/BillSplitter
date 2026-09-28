package vn.nhom03.chiabill.domain.split;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import vn.nhom03.chiabill.domain.model.BillItem;
import vn.nhom03.chiabill.domain.model.BillSpec;
import vn.nhom03.chiabill.domain.model.ShareLine;
import vn.nhom03.chiabill.domain.model.SplitMode;
import vn.nhom03.chiabill.domain.model.SplitResult;

/**
 * Chia hoá đơn thành phần của từng người. Mọi phép tính trên số nguyên VND.
 *
 * <ol>
 *   <li>Phần gốc: chia đều tạm tính; chia từng món cho người dùng món đó; mỗi người một số tiền
 *       (CUSTOM); hoặc chia tạm tính theo phần trăm (PERCENT, phần dư lớn nhất).</li>
 *   <li>VAT và phí phục vụ tính trên tạm tính (làm tròn nửa lên), phân bổ theo tỉ lệ phần gốc.</li>
 *   <li>Người nợ được làm tròn theo mức nhóm chọn; phần chênh dồn vào người trả.</li>
 * </ol>
 *
 * Bất biến: tổng các phần = tổng hoá đơn; mọi phần ≥ 0; kết quả tất định.
 */
public final class SplitEngine {
    private SplitEngine() {}

    /**
     * @param spec        nội dung hoá đơn
     * @param memberOrder thứ tự thành viên của nhóm; quyết định thứ tự dòng và thứ tự phát phần dư
     */
    public static SplitResult split(BillSpec spec, List<String> memberOrder) {
        Map<String, Long> base = new LinkedHashMap<>();
        for (String id : memberOrder) base.put(id, 0L);
        List<String> unassigned = new ArrayList<>();

        if (spec.getMode() == SplitMode.EQUAL) {
            List<String> ps = inOrder(memberOrder, spec.getParticipants());
            long[] parts = Allocator.equal(spec.getSubtotal(), ps.size());
            for (int i = 0; i < ps.size(); i++) base.put(ps.get(i), base.get(ps.get(i)) + parts[i]);
        } else if (spec.getMode() == SplitMode.CUSTOM) {
            for (String id : memberOrder) {
                Long v = spec.getShares().get(id);
                if (v != null) base.put(id, v);
            }
        } else if (spec.getMode() == SplitMode.PERCENT) {
            List<String> ps = new ArrayList<>();
            for (String id : memberOrder) if (spec.getShares().containsKey(id)) ps.add(id);
            long[] w = new long[ps.size()];
            for (int i = 0; i < ps.size(); i++) w[i] = spec.getShares().get(ps.get(i));
            long[] parts = Allocator.allocate(spec.getSubtotal(), w);
            for (int i = 0; i < ps.size(); i++) base.put(ps.get(i), parts[i]);
        } else {
            for (BillItem it : spec.getItems()) {
                List<String> who = inOrder(memberOrder, it.getConsumers());
                if (who.isEmpty()) {
                    if (it.getPrice() > 0) unassigned.add(it.getName().trim().isEmpty() ? "(món chưa đặt tên)" : it.getName().trim());
                    continue;
                }
                long[] parts = Allocator.equal(it.getPrice(), who.size());
                for (int i = 0; i < who.size(); i++) base.put(who.get(i), base.get(who.get(i)) + parts[i]);
            }
        }

        String payer = spec.getPayerId();
        List<String> ids = new ArrayList<>();
        for (String id : memberOrder) if (base.get(id) > 0 || id.equals(payer)) ids.add(id);
        if (!ids.contains(payer)) ids.add(payer); // người trả không nằm trong danh sách nhóm: vẫn có dòng

        long[] bw = new long[ids.size()];
        long subtotal = 0;
        for (int i = 0; i < ids.size(); i++) {
            Long b = base.get(ids.get(i));
            bw[i] = b == null ? 0 : b;
            subtotal += bw[i];
        }
        long vat = percentOf(subtotal, spec.getVatPercent());
        long service = percentOf(subtotal, spec.getServicePercent());
        long total = subtotal + vat + service;
        long[] va = Allocator.allocate(vat, bw);
        long[] sa = Allocator.allocate(service, bw);

        long[] raw = new long[ids.size()];
        for (int i = 0; i < ids.size(); i++) raw[i] = bw[i] + va[i] + sa[i];

        long[] amt = roundDebtors(ids, raw, payer, total, spec.getRounding(), false);
        int payerIdx = ids.indexOf(payer);
        if (amt[payerIdx] < 0) {
            // Làm tròn gần nhất khiến người trả bị âm (người trả dùng rất ít hoặc không dùng):
            // chuyển sang làm tròn xuống cho người nợ, người trả luôn ≥ phần thật của mình.
            amt = roundDebtors(ids, raw, payer, total, spec.getRounding(), true);
        }

        List<ShareLine> lines = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            lines.add(new ShareLine(ids.get(i), bw[i], va[i] + sa[i], amt[i], amt[i] - raw[i]));
        }
        return new SplitResult(lines, subtotal, vat, service, total, unassigned);
    }

    /** Tổng phần trăm (phần vạn) bắt buộc của chế độ PERCENT. */
    public static final long PERCENT_TOTAL = 10_000;

    /**
     * Lỗi nhập liệu khiến hoá đơn chưa lưu được, hoặc null nếu hợp lệ.
     * Chỉ tính những người có trong memberOrder.
     */
    public static String check(BillSpec spec, List<String> memberOrder) {
        switch (spec.getMode()) {
            case EQUAL:
                if (inOrder(memberOrder, spec.getParticipants()).isEmpty()) return "Chọn ít nhất một người tham gia";
                return null;
            case CUSTOM: {
                long sum = 0;
                for (String id : memberOrder) {
                    Long v = spec.getShares().get(id);
                    if (v != null) sum += v;
                }
                return sum > 0 ? null : "Nhập số tiền cho ít nhất một người";
            }
            case PERCENT: {
                long sum = 0;
                for (String id : memberOrder) {
                    Long v = spec.getShares().get(id);
                    if (v != null) sum += v;
                }
                if (sum != PERCENT_TOTAL) return "Tổng phần trăm đang là " + formatPercent(sum) + ", cần đúng 100%";
                return null;
            }
            default:
                return null;
        }
    }

    /**
     * "12,5" hoặc "12.5" → 1250 phần vạn. Rỗng → 0. Sai định dạng, quá 2 chữ số thập phân hoặc &gt; 100 → -1.
     */
    public static long parsePercent(String text) {
        if (text == null) return 0;
        String t = text.trim().replace('.', ',');
        if (t.endsWith("%")) t = t.substring(0, t.length() - 1).trim();
        if (t.isEmpty()) return 0;
        int comma = t.indexOf(',');
        String whole = comma < 0 ? t : t.substring(0, comma);
        String frac = comma < 0 ? "" : t.substring(comma + 1);
        if (whole.isEmpty()) whole = "0";
        if (whole.length() > 3 || frac.length() > 2 || frac.indexOf(',') >= 0) return -1;
        for (char c : (whole + frac).toCharArray()) if (c < '0' || c > '9') return -1;
        long bp = Long.parseLong(whole) * 100 + (frac.isEmpty() ? 0 : Long.parseLong(frac.length() == 1 ? frac + "0" : frac));
        return bp > PERCENT_TOTAL ? -1 : bp;
    }

    /** 1250 phần vạn → "12,5%". */
    public static String formatPercent(long basisPoints) {
        long whole = basisPoints / 100, frac = Math.abs(basisPoints % 100);
        if (frac == 0) return whole + "%";
        String f = frac % 10 == 0 ? String.valueOf(frac / 10) : String.format(java.util.Locale.ROOT, "%02d", frac);
        return whole + "," + f + "%";
    }

    private static long[] roundDebtors(List<String> ids, long[] raw, String payer, long total, long step, boolean floor) {
        long[] amt = new long[raw.length];
        long others = 0;
        int payerIdx = -1;
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).equals(payer)) { payerIdx = i; continue; }
            amt[i] = step <= 1 ? raw[i] : (floor ? (raw[i] / step) * step : ((raw[i] + step / 2) / step) * step);
            others += amt[i];
        }
        amt[payerIdx] = total - others;
        return amt;
    }

    /** value × percent / 100, làm tròn nửa lên. */
    static long percentOf(long value, int percent) {
        return (Math.multiplyExact(value, (long) percent) + 50) / 100;
    }

    private static List<String> inOrder(List<String> order, List<String> subset) {
        List<String> out = new ArrayList<>();
        for (String id : order) if (subset.contains(id)) out.add(id);
        return out;
    }
}
