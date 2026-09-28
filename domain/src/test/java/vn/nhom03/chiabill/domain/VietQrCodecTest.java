package vn.nhom03.chiabill.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import vn.nhom03.chiabill.domain.qr.Crc16;
import vn.nhom03.chiabill.domain.qr.PaymentTarget;
import vn.nhom03.chiabill.domain.qr.QrError;
import vn.nhom03.chiabill.domain.qr.QrParseResult;
import vn.nhom03.chiabill.domain.qr.Tlv;
import vn.nhom03.chiabill.domain.qr.TransferContent;
import vn.nhom03.chiabill.domain.qr.VietQrCodec;

class VietQrCodecTest {
    private static final PaymentTarget VCB = new PaymentTarget("970436", "1012345678", "TRAN TUAN DAT");

    @Test
    void crcKhopVectorChuan() {
        assertEquals("29B1", Crc16.ccittFalseHex("123456789"));
    }

    @Test
    void sinhQrDongDungCauTruc() {
        String p = VietQrCodec.encode(VCB, 308000, "CB B1 DAT");
        assertEquals("00020101021238540010A00000072701240006970436011010123456780208QRIBFTTA530370454063080005802VN62130809CB B1 DAT6304"
                + p.substring(p.length() - 4), p);
        assertTrue(p.startsWith("000201010212"));
    }

    @Test
    void qrTinhKhongCoSoTien() {
        String p = VietQrCodec.encode(VCB, 0, "");
        assertTrue(p.startsWith("000201010211"));
        assertFalse(p.contains("5406"));
        QrParseResult r = VietQrCodec.parse(p);
        assertTrue(r.isOk());
        assertFalse(r.isDynamic());
        assertEquals(0, r.getAmount());
    }

    @Test
    void docLaiRaDungDuLieu() {
        QrParseResult r = VietQrCodec.parse(VietQrCodec.encode(VCB, 1_231_200, "CB B1 DAT"));
        assertTrue(r.isOk());
        assertEquals("970436", r.getTarget().getBankBin());
        assertEquals("1012345678", r.getTarget().getAccountNo());
        assertEquals(1_231_200, r.getAmount());
        assertEquals("CB B1 DAT", r.getContent());
        assertTrue(r.isDynamic());
    }

    @Test
    void docQrCoTenNguoiNhanVaXuongDong() {
        // Dựng QR như app ngân hàng: có trường 59 tên, có xuống dòng thừa ở cuối.
        String body = Tlv.field("00", "01") + Tlv.field("01", "11")
                + Tlv.field("38", Tlv.field("00", "A000000727") + Tlv.field("01", Tlv.field("00", "970422") + Tlv.field("01", "0987654321")) + Tlv.field("02", "QRIBFTTA"))
                + Tlv.field("53", "704") + Tlv.field("58", "VN") + Tlv.field("59", "NGUYEN VAN MINH") + "6304";
        String p = body + Crc16.ccittFalseHex(body) + "\n";
        QrParseResult r = VietQrCodec.parse(p);
        assertTrue(r.isOk());
        assertEquals("NGUYEN VAN MINH", r.getTarget().getAccountName());
        assertEquals("970422", r.getTarget().getBankBin());
    }

    @Test
    void crcSaiBiTuChoi() {
        String p = VietQrCodec.encode(VCB, 50000, "");
        String broken = p.substring(0, p.length() - 1) + (p.endsWith("A") ? "B" : "A");
        QrParseResult r = VietQrCodec.parse(broken);
        assertFalse(r.isOk());
        assertEquals(QrError.BAD_CRC, r.getError());
    }

    @Test
    void suaSoTienMaKhongTinhLaiCrcBiPhatHien() {
        String p = VietQrCodec.encode(VCB, 50000, "");
        String tampered = p.replace("540550000", "540590000");
        assertEquals(QrError.BAD_CRC, VietQrCodec.parse(tampered).getError());
    }

    @Test
    void khongPhaiEmv() {
        assertEquals(QrError.NOT_EMV, VietQrCodec.parse("https://example.com").getError());
        assertEquals(QrError.NOT_EMV, VietQrCodec.parse("").getError());
        assertEquals(QrError.NOT_EMV, VietQrCodec.parse(null).getError());
    }

    @Test
    void biCat() {
        String p = VietQrCodec.encode(VCB, 50000, "");
        assertEquals(QrError.TRUNCATED, VietQrCodec.parse(p.substring(0, p.length() - 10)).getError());
    }

    @Test
    void guidKhacNapasLaKhongPhaiVietQr() {
        String body = Tlv.field("00", "01") + Tlv.field("01", "11")
                + Tlv.field("38", Tlv.field("00", "A000000999") + Tlv.field("01", "abc"))
                + Tlv.field("53", "704") + Tlv.field("58", "VN") + "6304";
        QrParseResult r = VietQrCodec.parse(body + Crc16.ccittFalseHex(body));
        assertEquals(QrError.NOT_VIETQR, r.getError());
    }

    @Test
    void dichVuKhongHoTro() {
        String body = Tlv.field("00", "01") + Tlv.field("01", "11")
                + Tlv.field("38", Tlv.field("00", "A000000727") + Tlv.field("01", Tlv.field("00", "970436") + Tlv.field("01", "123456")) + Tlv.field("02", "QRPUSH"))
                + Tlv.field("53", "704") + Tlv.field("58", "VN") + "6304";
        assertEquals(QrError.UNSUPPORTED_SERVICE, VietQrCodec.parse(body + Crc16.ccittFalseHex(body)).getError());
    }

    @Test
    void noiDungBoDauVaKyTuLa() {
        assertEquals("CB LAU GA LA E DAT", TransferContent.sanitize("cb lẩu gà lá é, Đạt!"));
        assertEquals("CB B12 HUNG", TransferContent.forDebt("b12", "Hùng"));
        assertTrue(TransferContent.sanitize("Một nội dung rất rất dài vượt quá giới hạn").length() <= VietQrCodec.MAX_CONTENT);
    }

    @Test
    void anSoTaiKhoan() {
        assertEquals("101•••678", VCB.maskedAccount());
    }

    @Test
    void property_maHoaRoiDocLaiLuonKhop() {
        Random r = new Random(7);
        String[] bins = {"970436", "970407", "970422", "970418", "970415", "970416"};
        for (int k = 0; k < 5000; k++) {
            StringBuilder acc = new StringBuilder();
            int len = 6 + r.nextInt(14);
            for (int i = 0; i < len; i++) acc.append((char) ('0' + r.nextInt(10)));
            PaymentTarget t = new PaymentTarget(bins[r.nextInt(bins.length)], acc.toString(), "");
            long amount = r.nextInt(3) == 0 ? 0 : 1 + r.nextInt(999_999_999);
            String content = r.nextBoolean() ? "" : TransferContent.forDebt("b" + r.nextInt(99999), "Người " + k);
            String p = VietQrCodec.encode(t, amount, content);
            QrParseResult res = VietQrCodec.parse(p);
            assertTrue(res.isOk(), "không đọc lại được ở case " + k + ": " + p);
            assertEquals(t.getBankBin(), res.getTarget().getBankBin());
            assertEquals(t.getAccountNo(), res.getTarget().getAccountNo());
            assertEquals(amount, res.getAmount());
            assertEquals(content, res.getContent());
            assertEquals(Crc16.ccittFalseHex(p.substring(0, p.length() - 4)), p.substring(p.length() - 4));
        }
    }
}
