package vn.nhom03.chiabill.domain.split;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chia một số nguyên theo trọng số bằng phương pháp phần dư lớn nhất (largest remainder).
 * Tổng kết quả luôn bằng đúng total. Hoà phần dư thì ưu tiên chỉ số nhỏ hơn, nên kết quả tất định.
 */
public final class Allocator {
    private Allocator() {}

    public static long[] allocate(long total, long[] weights) {
        if (total < 0) throw new IllegalArgumentException("total < 0");
        long[] out = new long[weights.length];
        long sumW = 0;
        for (long w : weights) {
            if (w < 0) throw new IllegalArgumentException("weight < 0");
            sumW = Math.addExact(sumW, w);
        }
        if (sumW == 0 || total == 0) return out;

        long assigned = 0;
        final long[] rem = new long[weights.length];
        for (int i = 0; i < weights.length; i++) {
            long p = Math.multiplyExact(total, weights[i]);
            out[i] = p / sumW;
            rem[i] = p % sumW;
            assigned += out[i];
        }
        long left = total - assigned; // 0 <= left < số phần tử có trọng số > 0
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) if (weights[i] > 0) order.add(i);
        Collections.sort(order, (a, b) -> rem[a] != rem[b] ? Long.compare(rem[b], rem[a]) : Integer.compare(a, b));
        for (int k = 0; k < left; k++) out[order.get(k)]++;
        return out;
    }

    /** Chia đều cho n phần. */
    public static long[] equal(long total, int n) {
        long[] w = new long[n];
        for (int i = 0; i < n; i++) w[i] = 1;
        return allocate(total, w);
    }
}
