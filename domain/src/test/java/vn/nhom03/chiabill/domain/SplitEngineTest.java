package vn.nhom03.chiabill.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import vn.nhom03.chiabill.domain.model.BillItem;
import vn.nhom03.chiabill.domain.model.BillSpec;
import vn.nhom03.chiabill.domain.model.ShareLine;
import vn.nhom03.chiabill.domain.model.SplitMode;
import vn.nhom03.chiabill.domain.model.SplitResult;
import vn.nhom03.chiabill.domain.split.SplitEngine;

class SplitEngineTest {
    private static final List<String> G = Fixtures.list("dat", "minh", "lan", "hung");

    @Test
    void chiaDeuBaNguoiLamTronMotDong() {
        BillSpec s = new BillSpec("dat", SplitMode.EQUAL, 100000, Fixtures.list("dat", "minh", "lan"), null, 0, 0, 1);
        SplitResult r = SplitEngine.split(s, G);
        assertEquals(33334, r.lineOf("dat").getAmount());
        assertEquals(33333, r.lineOf("minh").getAmount());
        assertEquals(33333, r.lineOf("lan").getAmount());
        assertEquals(100000, r.sumOfShares());
    }

    @Test
    void lamTronNghinPhanLeDonNguoiTra() {
        BillSpec s = new BillSpec("dat", SplitMode.EQUAL, 100000, Fixtures.list("dat", "minh", "lan"), null, 0, 0, 1000);
        SplitResult r = SplitEngine.split(s, G);
        assertEquals(33000, r.lineOf("minh").getAmount());
        assertEquals(33000, r.lineOf("lan").getAmount());
        assertEquals(34000, r.lineOf("dat").getAmount());
        assertTrue(r.isBalanced());
    }

    @Test
    void lauGaVat8PhanTram() {
        BillSpec s = new BillSpec("minh", SplitMode.EQUAL, 1_140_000, G, null, 8, 0, 1000);
        SplitResult r = SplitEngine.split(s, G);
        assertEquals(91_200, r.getVat());
        assertEquals(1_231_200, r.getTotal());
        ShareLine dat = r.lineOf("dat");
        assertEquals(285_000, dat.getBase());
        assertEquals(22_800, dat.getFee());
        assertEquals(308_000, dat.getAmount());
        assertEquals(200, dat.getRoundingAdj());
        assertEquals(307_200, r.lineOf("minh").getAmount());
        assertTrue(r.isBalanced());
    }

    @Test
    void chiaTheoMon() {
        List<BillItem> items = new ArrayList<>();
        items.add(new BillItem("Cà phê muối", 45000, Fixtures.list("dat")));
        items.add(new BillItem("Cà phê muối", 45000, Fixtures.list("lan")));
        items.add(new BillItem("Bạc xỉu", 40000, Fixtures.list("minh")));
        items.add(new BillItem("Trà atiso", 35000, Fixtures.list("hung")));
        items.add(new BillItem("Bánh tráng nướng", 60000, G));
        BillSpec s = new BillSpec("dat", SplitMode.ITEMIZED, 0, null, items, 0, 0, 1000);
        SplitResult r = SplitEngine.split(s, G);
        assertEquals(225_000, r.getTotal());
        assertEquals(60_000, r.lineOf("dat").getAmount());
        assertEquals(55_000, r.lineOf("minh").getAmount());
        assertEquals(60_000, r.lineOf("lan").getAmount());
        assertEquals(50_000, r.lineOf("hung").getAmount());
    }

    @Test
    void phuPhiPhanBoTheoTiLePhanGoc() {
        List<BillItem> items = new ArrayList<>();
        items.add(new BillItem("Bò", 300000, Fixtures.list("dat")));
        items.add(new BillItem("Rau", 100000, Fixtures.list("minh")));
        BillSpec s = new BillSpec("dat", SplitMode.ITEMIZED, 0, null, items, 10, 5, 1);
        SplitResult r = SplitEngine.split(s, G);
        assertEquals(40000, r.getVat());
        assertEquals(20000, r.getService());
        assertEquals(45000, r.lineOf("dat").getFee());
        assertEquals(15000, r.lineOf("minh").getFee());
        assertEquals(115000, r.lineOf("minh").getAmount());
    }

    @Test
    void monChuaGanThiKhongLuuDuoc() {
        List<BillItem> items = new ArrayList<>();
        items.add(new BillItem("Lẩu", 200000, Fixtures.list("dat")));
        items.add(new BillItem("Bia", 90000, new ArrayList<String>()));
        SplitResult r = SplitEngine.split(new BillSpec("dat", SplitMode.ITEMIZED, 0, null, items, 0, 0, 1), G);
        assertEquals(1, r.getUnassignedItems().size());
        assertEquals("Bia", r.getUnassignedItems().get(0));
        assertFalse(r.isSavable());
    }

    @Test
    void nguoiTraKhongDungGiThiLamTronXuong() {
        // Đạt trả hộ, không ăn. Làm tròn gần nhất sẽ khiến Đạt âm; engine phải chuyển sang làm tròn xuống.
        // 100.500 / 3 = 33.500 mỗi người; làm tròn gần nhất ra 34.000 × 3 = 102.000 > tổng.
        BillSpec s = new BillSpec("dat", SplitMode.EQUAL, 100_500, Fixtures.list("minh", "lan", "hung"), null, 0, 0, 1000);
        SplitResult r = SplitEngine.split(s, G);
        assertEquals(33_000, r.lineOf("minh").getAmount());
        assertEquals(1_500, r.lineOf("dat").getAmount());
        assertTrue(r.lineOf("dat").getAmount() >= 0);
        assertTrue(r.isBalanced());
    }

    @Test
    void motNguoiThiKhongAiNo() {
        SplitResult r = SplitEngine.split(new BillSpec("dat", SplitMode.EQUAL, 55000, Fixtures.list("dat"), null, 0, 0, 1000), G);
        assertEquals(1, r.getLines().size());
        assertEquals(55000, r.lineOf("dat").getAmount());
    }

    @Test
    void tongBangKhongThiKhongLuuDuoc() {
        SplitResult r = SplitEngine.split(new BillSpec("dat", SplitMode.EQUAL, 0, G, null, 8, 0, 1000), G);
        assertFalse(r.isSavable());
    }

    private static Map<String, Long> shares(Object... kv) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).longValue());
        return m;
    }

    @Test
    void soTienTuyChinhGiuNguyenTungNguoi() {
        BillSpec s = new BillSpec("dat", SplitMode.CUSTOM, 0, null, null, 0, 0, 1000,
                shares("dat", 120_000, "minh", 80_000, "lan", 50_000));
        SplitResult r = SplitEngine.split(s, G);
        assertNull(SplitEngine.check(s, G));
        assertEquals(250_000, r.getSubtotal());
        assertEquals(80_000, r.lineOf("minh").getAmount());
        assertEquals(50_000, r.lineOf("lan").getAmount());
        assertEquals(120_000, r.lineOf("dat").getAmount());
        assertTrue(r.isBalanced());
    }

    @Test
    void soTienTuyChinhCoVatPhanBoTheoTiLe() {
        BillSpec s = new BillSpec("dat", SplitMode.CUSTOM, 0, null, null, 10, 0, 1,
                shares("minh", 100_000, "lan", 300_000));
        SplitResult r = SplitEngine.split(s, G);
        assertEquals(40_000, r.getVat());
        assertEquals(110_000, r.lineOf("minh").getAmount());
        assertEquals(330_000, r.lineOf("lan").getAmount());
        assertEquals(0, r.lineOf("dat").getAmount());
        assertEquals(r.getTotal(), r.sumOfShares());
    }

    @Test
    void phanTramChiaDungTiLe() {
        BillSpec s = new BillSpec("minh", SplitMode.PERCENT, 1_000_000, null, null, 0, 0, 1,
                shares("dat", 5000, "minh", 3000, "lan", 2000));
        assertNull(SplitEngine.check(s, G));
        SplitResult r = SplitEngine.split(s, G);
        assertEquals(500_000, r.lineOf("dat").getAmount());
        assertEquals(300_000, r.lineOf("minh").getAmount());
        assertEquals(200_000, r.lineOf("lan").getAmount());
    }

    @Test
    void phanTramLeKhongLechMotDong() {
        BillSpec s = new BillSpec("dat", SplitMode.PERCENT, 100_000, null, null, 0, 0, 1,
                shares("dat", 3333, "minh", 3333, "lan", 3334));
        SplitResult r = SplitEngine.split(s, G);
        assertEquals(100_000, r.sumOfShares());
        assertEquals(33_340, r.lineOf("lan").getAmount());
    }

    @Test
    void phanTramKhongDu100ThiBaoLoi() {
        BillSpec s = new BillSpec("dat", SplitMode.PERCENT, 100_000, null, null, 0, 0, 1,
                shares("dat", 5000, "minh", 2550));
        String err = SplitEngine.check(s, G);
        assertNotNull(err);
        assertTrue(err.contains("75,5%"), err);
    }

    @Test
    void docPhanTramNguoiDungGo() {
        assertEquals(1250, SplitEngine.parsePercent("12,5"));
        assertEquals(1250, SplitEngine.parsePercent("12.50"));
        assertEquals(3333, SplitEngine.parsePercent("33,33%"));
        assertEquals(10_000, SplitEngine.parsePercent("100"));
        assertEquals(50, SplitEngine.parsePercent(",5"));
        assertEquals(0, SplitEngine.parsePercent(""));
        assertEquals(-1, SplitEngine.parsePercent("100,01"));
        assertEquals(-1, SplitEngine.parsePercent("1,234"));
        assertEquals(-1, SplitEngine.parsePercent("abc"));
        assertEquals("12,5%", SplitEngine.formatPercent(1250));
        assertEquals("33,33%", SplitEngine.formatPercent(3333));
        assertEquals("7,05%", SplitEngine.formatPercent(705));
    }

    @Test
    void tuyChinhTrongThiBaoLoi() {
        BillSpec s = new BillSpec("dat", SplitMode.CUSTOM, 0, null, null, 0, 0, 1, shares());
        assertNotNull(SplitEngine.check(s, G));
    }

    @Test
    void khongChoPhanAm() {
        try {
            new BillSpec("dat", SplitMode.CUSTOM, 0, null, null, 0, 0, 1, shares("minh", -1));
            assertTrue(false, "phải ném lỗi");
        } catch (IllegalArgumentException expected) {
            // đúng
        }
    }

    @Test
    void property_10000HoaDonKhongLechMotDong() {
        Random rnd = new Random(20260927L);
        for (int k = 0; k < 10000; k++) {
            List<String> members = Fixtures.members(2 + rnd.nextInt(14));
            BillSpec s = Fixtures.randomBill(rnd, members);
            SplitResult r = SplitEngine.split(s, members);
            assertEquals(r.getTotal(), r.sumOfShares(), "tổng lệch ở case " + k);
            for (ShareLine l : r.getLines()) {
                assertTrue(l.getAmount() >= 0, "phần âm ở case " + k);
                if (!l.getMemberId().equals(s.getPayerId()) && s.getRounding() > 1) {
                    assertEquals(0, l.getAmount() % s.getRounding(), "người nợ chưa tròn ở case " + k);
                }
                assertEquals(l.getBase() + l.getFee() + l.getRoundingAdj(), l.getAmount(), "breakdown sai ở case " + k);
                assertTrue(Math.abs(l.getRoundingAdj()) < Math.max(1, s.getRounding()) * (long) r.getLines().size(),
                        "làm tròn quá lớn ở case " + k);
            }
            SplitResult again = SplitEngine.split(s, members);
            for (int i = 0; i < r.getLines().size(); i++) {
                assertEquals(r.getLines().get(i).getAmount(), again.getLines().get(i).getAmount(), "không tất định ở case " + k);
            }
        }
    }
}
