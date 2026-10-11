package vn.nhom03.chiabill.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.nhom03.chiabill.domain.receipt.ReceiptParser;
import vn.nhom03.chiabill.domain.receipt.ReceiptParser.Fragment;
import vn.nhom03.chiabill.domain.receipt.ReceiptParser.Result;

class ReceiptParserTest {

    @Test
    void docSoTienNhieuKieuViet() {
        assertEquals(150000, ReceiptParser.parseAmount("150.000"));
        assertEquals(1250000, ReceiptParser.parseAmount("1,250,000"));
        assertEquals(150000, ReceiptParser.parseAmount("150.000,00"));
        assertEquals(150000, ReceiptParser.parseAmount("150k"));
        assertEquals(1500000, ReceiptParser.parseAmount("1.5tr"));
        assertEquals(98000, ReceiptParser.parseAmount("98000"));
    }

    @Test
    void boQuaNgayGioSoLuongVaSoDienThoai() {
        assertEquals(List.of(), ReceiptParser.amountsIn("Ngày 11/10/2026 19:45"));
        assertEquals(List.of(), ReceiptParser.amountsIn("ĐT: 0901234567"));
        assertEquals(List.of(), ReceiptParser.amountsIn("Bàn 12  SL 3"));
        assertEquals(List.of(), ReceiptParser.amountsIn("HD00012345"));
        assertEquals(List.of(45000L, 90000L), ReceiptParser.amountsIn("Bia Tiger 2 x 45.000 90.000đ"));
    }

    @Test
    void hoaDonQuanLau_tongTheoTuKhoaVaDanhSachMon() {
        Result r = ReceiptParser.parse(Arrays.asList(
                "LẨU NẤM ASHIMA",
                "35 Nguyễn Văn Cừ, Q.5",
                "HOÁ ĐƠN THANH TOÁN",
                "Ngày: 11/10/2026  Giờ: 20:15",
                "Lẩu nấm thập cẩm 1 350.000",
                "Bò Mỹ 2 x 120.000 240.000",
                "Nước suối 3 x 10.000 30.000",
                "Tạm tính 620.000",
                "VAT 8% 49.600",
                "Tổng thanh toán 669.600",
                "Tiền khách đưa 700.000",
                "Tiền thừa 30.400"));
        assertEquals(669600, r.total);
        assertTrue(r.totalFromKeyword);
        assertEquals("LẨU NẤM ASHIMA", r.title);
        assertEquals(3, r.items.size());
        assertEquals("Lẩu nấm thập cẩm", r.items.get(0).name);
        assertEquals(240000, r.items.get(1).price);
        // có VAT nên tổng món khác tổng phải trả: app chỉ điền tổng, không điền món
        assertFalse(r.itemsMatchTotal);
    }

    @Test
    void hoaDonCafe_khongDauVaTotalTiengAnh() {
        Result r = ReceiptParser.parse(Arrays.asList(
                "THE COFFEE HOUSE",
                "Ca phe sua da 39,000",
                "Tra dao cam sa 49,000",
                "Banh croissant 35,000",
                "TOTAL 123,000",
                "CASH 200,000",
                "CHANGE 77,000"));
        assertEquals(123000, r.total);
        assertEquals(3, r.items.size());
        assertTrue(r.itemsMatchTotal);
    }

    @Test
    void nhanVaSoBiTachHaiDong() {
        Result r = ReceiptParser.parse(Arrays.asList(
                "Quán Ốc Đào",
                "Ốc hương",
                "180.000",
                "Nghêu hấp",
                "95.000",
                "Khách phải trả",
                "275.000"));
        assertEquals(275000, r.total);
        assertEquals(2, r.items.size());
        assertEquals("Ốc hương", r.items.get(0).name);
        assertTrue(r.itemsMatchTotal);
    }

    @Test
    void khongCoTuKhoa_layTienLonNhatNhungBoTienKhachDua() {
        Result r = ReceiptParser.parse(Arrays.asList(
                "Tạp hoá Minh",
                "Mì gói 35.000",
                "Trứng 42.000",
                "77.000",
                "Tiền mặt 100.000"));
        assertEquals(77000, r.total);
        assertFalse(r.totalFromKeyword);
    }

    @Test
    void congTyKhongPhaiDongTong() {
        Result r = ReceiptParser.parse(Arrays.asList(
                "Công ty TNHH Pizza 4P 0312345678",
                "Pizza 4 cheese 1 268.000",
                "Cộng 268.000"));
        assertEquals(268000, r.total);
        assertEquals(1, r.items.size());
    }

    @Test
    void anhKhongCoChuSo_tongBangKhong() {
        Result r = ReceiptParser.parse(Arrays.asList("Cảm ơn quý khách", "Hẹn gặp lại"));
        assertEquals(0, r.total);
        assertTrue(r.items.isEmpty());
    }

    @Test
    void ghepManhCungHangThanhMotDong() {
        List<Fragment> f = new ArrayList<>();
        f.add(new Fragment("90.000", 600, 205, 700, 235));   // giá ở cột phải, lệch vài px
        f.add(new Fragment("Bia Tiger", 20, 200, 200, 230));
        f.add(new Fragment("Tổng", 20, 300, 120, 330));
        f.add(new Fragment("90.000", 600, 302, 700, 332));
        f.add(new Fragment("PHỞ HÀ", 200, 100, 400, 140));
        assertEquals(Arrays.asList("PHỞ HÀ", "Bia Tiger 90.000", "Tổng 90.000"), ReceiptParser.rows(f));
        Result r = ReceiptParser.parse(ReceiptParser.rows(f));
        assertEquals(90000, r.total);
        assertEquals("PHỞ HÀ", r.title);
        assertTrue(r.itemsMatchTotal);
    }
}
