package vn.nhom03.chiabill.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import vn.nhom03.chiabill.domain.model.BillItem;
import vn.nhom03.chiabill.domain.model.BillSpec;
import vn.nhom03.chiabill.domain.model.SplitMode;

/** Bộ sinh dữ liệu ngẫu nhiên có seed cố định, dùng cho property test (tái lập được). */
final class Fixtures {
    private Fixtures() {}

    static List<String> members(int n) {
        List<String> m = new ArrayList<>();
        for (int i = 0; i < n; i++) m.add("u" + i);
        return m;
    }

    static List<String> subset(Random r, List<String> all, int min) {
        List<String> out = new ArrayList<>();
        for (String s : all) if (r.nextInt(3) > 0) out.add(s);
        while (out.size() < min) {
            String pick = all.get(r.nextInt(all.size()));
            if (!out.contains(pick)) out.add(pick);
        }
        return out;
    }

    static BillSpec randomBill(Random r, List<String> members) {
        String payer = members.get(r.nextInt(members.size()));
        int[] vats = {0, 0, 8, 10};
        int[] svcs = {0, 0, 5, 10};
        long rounding = r.nextBoolean() ? 1 : 1000;
        int vat = vats[r.nextInt(vats.length)];
        int svc = svcs[r.nextInt(svcs.length)];
        int kind = r.nextInt(4);
        if (kind == 2) {
            Map<String, Long> sh = new LinkedHashMap<>();
            for (String id : subset(r, members, 1)) sh.put(id, r.nextInt(5) == 0 ? (long) r.nextInt(999) + 1 : 1000L * (1 + r.nextInt(900)) + r.nextInt(1000));
            return new BillSpec(payer, SplitMode.CUSTOM, 0, null, null, vat, svc, rounding, sh);
        }
        if (kind == 3) {
            List<String> who = subset(r, members, 1);
            Map<String, Long> sh = new LinkedHashMap<>();
            long left = 10_000;
            for (int i = 0; i < who.size(); i++) {
                long v = i == who.size() - 1 ? left : r.nextInt((int) left + 1);
                sh.put(who.get(i), v);
                left -= v;
            }
            long subtotal = 1000L * (1 + r.nextInt(5000)) + r.nextInt(1000);
            return new BillSpec(payer, SplitMode.PERCENT, subtotal, null, null, vat, svc, rounding, sh);
        }
        if (kind == 0) {
            long subtotal = r.nextInt(5) == 0 ? r.nextInt(1000) : 1000L * (1 + r.nextInt(5000)) + r.nextInt(1000);
            return new BillSpec(payer, SplitMode.EQUAL, subtotal, subset(r, members, 1), null, vat, svc, rounding);
        }
        List<BillItem> items = new ArrayList<>();
        int n = 1 + r.nextInt(12);
        for (int i = 0; i < n; i++) {
            long price = r.nextInt(6) == 0 ? r.nextInt(999) + 1 : 1000L * (5 + r.nextInt(500));
            items.add(new BillItem("m" + i, price, subset(r, members, 1)));
        }
        return new BillSpec(payer, SplitMode.ITEMIZED, 0, null, items, vat, svc, rounding);
    }

    static List<String> list(String... ids) {
        return Arrays.asList(ids);
    }
}
