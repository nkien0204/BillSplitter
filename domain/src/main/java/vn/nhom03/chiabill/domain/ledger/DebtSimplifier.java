package vn.nhom03.chiabill.domain.ledger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import vn.nhom03.chiabill.domain.model.Transfer;

/**
 * Tối giản nợ bằng tham lam: ghép người nợ nhiều nhất với người được nợ nhiều nhất.
 * Cho tối đa (số người có số dư ≠ 0) − 1 giao dịch. Tìm số giao dịch ít nhất tuyệt đối là NP-khó;
 * thuật toán này không hứa điều đó.
 */
public final class DebtSimplifier {
    private DebtSimplifier() {}

    private static final class Bal {
        final String id;
        long v;
        final int order;
        Bal(String id, long v, int order) { this.id = id; this.v = v; this.order = order; }
    }

    /** @param net số dư ròng mỗi người (dương = được nợ). Tổng phải bằng 0. Thứ tự map dùng để phá hoà. */
    public static List<Transfer> simplify(Map<String, Long> net) {
        long sum = 0;
        List<Bal> cred = new ArrayList<>();
        List<Bal> debt = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Long> e : net.entrySet()) {
            long v = e.getValue();
            sum += v;
            if (v > 0) cred.add(new Bal(e.getKey(), v, i));
            else if (v < 0) debt.add(new Bal(e.getKey(), -v, i));
            i++;
        }
        if (sum != 0) throw new IllegalArgumentException("Tổng số dư phải bằng 0, đang là " + sum);

        List<Transfer> out = new ArrayList<>();
        while (!cred.isEmpty() && !debt.isEmpty()) {
            Bal c = largest(cred);
            Bal d = largest(debt);
            long x = Math.min(c.v, d.v);
            out.add(new Transfer(d.id, c.id, x));
            c.v -= x;
            d.v -= x;
            if (c.v == 0) cred.remove(c);
            if (d.v == 0) debt.remove(d);
        }
        return out;
    }

    private static Bal largest(List<Bal> list) {
        return Collections.max(list, (a, b) -> a.v != b.v ? Long.compare(a.v, b.v) : Integer.compare(b.order, a.order));
    }
}
