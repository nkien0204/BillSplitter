package vn.nhom03.chiabill.data.repo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import vn.nhom03.chiabill.data.db.BillEntity;
import vn.nhom03.chiabill.data.db.BillItemEntity;
import vn.nhom03.chiabill.data.db.UserEntity;
import vn.nhom03.chiabill.domain.model.Bill;
import vn.nhom03.chiabill.domain.model.BillItem;
import vn.nhom03.chiabill.domain.model.BillSpec;
import vn.nhom03.chiabill.domain.model.Category;
import vn.nhom03.chiabill.domain.model.SplitMode;
import vn.nhom03.chiabill.domain.qr.PaymentTarget;

/** Chuyển giữa entity Room và model miền. Dữ liệu đọc từ DB vẫn được kiểm trước khi thành model. */
public final class Mappers {
    private Mappers() {}

    public static List<String> csv(String s) {
        if (s == null || s.trim().isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String p : Arrays.asList(s.split(","))) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }

    public static String csv(Iterable<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    /** "a:100,b:250" → {a=100, b=250}. Bỏ qua phần hỏng hoặc âm. */
    public static Map<String, Long> shares(String s) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String p : csv(s)) {
            int i = p.lastIndexOf(':');
            if (i <= 0) continue;
            try {
                long v = Long.parseLong(p.substring(i + 1).trim());
                if (v > 0) out.put(p.substring(0, i).trim(), v);
            } catch (NumberFormatException ignored) {
                // bỏ qua
            }
        }
        return out;
    }

    public static String shares(Map<String, Long> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> e : m.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    public static SplitMode mode(String s) {
        if (s != null) for (SplitMode m : SplitMode.values()) if (m.name().equals(s)) return m;
        return SplitMode.EQUAL;
    }

    public static Bill toBill(BillEntity e, List<BillItemEntity> items) {
        SplitMode mode = mode(e.mode);
        List<BillItem> list = new ArrayList<>();
        if (items != null) {
            for (BillItemEntity it : items) list.add(new BillItem(it.name, Math.max(0, it.price), csv(it.consumersCsv)));
        }
        int vat = clamp(e.vatPercent, 0, 100);
        int svc = clamp(e.servicePercent, 0, 100);
        long rounding = e.rounding >= 1 ? e.rounding : 1;
        BillSpec spec = new BillSpec(e.payerId, mode, Math.max(0, e.subtotal), csv(e.participantsCsv), list, vat, svc, rounding,
                shares(e.sharesCsv));
        return new Bill(e.id, e.groupId, e.title, e.createdAt, e.createdBy, spec, Category.parse(e.category));
    }

    public static PaymentTarget toTarget(UserEntity u) {
        if (u == null || !u.hasPaymentProfile()) return null;
        if (!PaymentTarget.isValidBin(u.bankBin) || !PaymentTarget.isValidAccount(u.accountNo)) return null;
        return new PaymentTarget(u.bankBin, u.accountNo, u.accountName);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
