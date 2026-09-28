package vn.nhom03.chiabill.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

import org.junit.jupiter.api.Test;

import vn.nhom03.chiabill.domain.split.Allocator;

class AllocatorTest {

    @Test
    void chiaDeuPhanDuVeNguoiDau() {
        assertArrayEquals(new long[]{33334, 33333, 33333}, Allocator.equal(100000, 3));
    }

    @Test
    void theoTrongSoLayPhanDuLonNhat() {
        // 100 theo 1:1:1 và thêm phần dư theo thập phân lớn nhất
        assertArrayEquals(new long[]{50, 17, 33}, Allocator.allocate(100, new long[]{3, 1, 2}));
    }

    @Test
    void trongSoBangKhongKhongNhanTien() {
        assertArrayEquals(new long[]{0, 10, 0}, Allocator.allocate(10, new long[]{0, 5, 0}));
    }

    @Test
    void tongBangKhongHoacTrongSoRong() {
        assertArrayEquals(new long[]{0, 0}, Allocator.allocate(0, new long[]{1, 1}));
        assertArrayEquals(new long[]{0, 0}, Allocator.allocate(99, new long[]{0, 0}));
    }

    @Test
    void tuChoiSoAm() {
        assertThrows(IllegalArgumentException.class, () -> Allocator.allocate(-1, new long[]{1}));
        assertThrows(IllegalArgumentException.class, () -> Allocator.allocate(1, new long[]{-1}));
    }

    @Test
    void property_tongLuonKhopVaTatDinh() {
        Random r = new Random(42);
        for (int k = 0; k < 20000; k++) {
            int n = 1 + r.nextInt(15);
            long[] w = new long[n];
            for (int i = 0; i < n; i++) w[i] = r.nextInt(4) == 0 ? 0 : r.nextInt(2_000_000);
            long total = r.nextInt(50_000_000);
            long[] a = Allocator.allocate(total, w);
            long sumW = 0, sum = 0;
            for (int i = 0; i < n; i++) { sumW += w[i]; sum += a[i]; }
            assertEquals(sumW == 0 ? 0 : total, sum, "tổng lệch ở case " + k);
            assertArrayEquals(a, Allocator.allocate(total, w), "không tất định ở case " + k);
            for (int i = 0; i < n; i++) {
                if (w[i] == 0) assertEquals(0, a[i]);
                // mỗi phần lệch khỏi tỉ lệ đúng không quá 1 đồng
                if (sumW > 0) {
                    double exact = (double) total * w[i] / sumW;
                    assertEquals(true, Math.abs(a[i] - exact) < 1.0 + 1e-6, "lệch tỉ lệ ở case " + k);
                }
            }
        }
    }
}
