package vn.nhom03.chiabill.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import vn.nhom03.chiabill.domain.user.PhoneNumber;

class PhoneNumberTest {

    @Test
    void cacCachGoCungMotSoRaCungKetQua() {
        String want = "0912345678";
        assertEquals(want, PhoneNumber.normalize("0912345678"));
        assertEquals(want, PhoneNumber.normalize("0912 345 678"));
        assertEquals(want, PhoneNumber.normalize("0912.345.678"));
        assertEquals(want, PhoneNumber.normalize("0912-345-678"));
        assertEquals(want, PhoneNumber.normalize("+84912345678"));
        assertEquals(want, PhoneNumber.normalize("+84 912 345 678"));
        assertEquals(want, PhoneNumber.normalize("84912345678"));
        assertEquals(want, PhoneNumber.normalize("  (0912) 345 678 "));
    }

    @Test
    void tuChoiSoKhongHopLe() {
        assertEquals(null, PhoneNumber.normalize(null));
        assertEquals(null, PhoneNumber.normalize(""));
        assertEquals(null, PhoneNumber.normalize("091234567"));      // 9 số
        assertEquals(null, PhoneNumber.normalize("09123456789"));    // 11 số
        assertEquals(null, PhoneNumber.normalize("0212345678"));     // đầu 02 là cố định
        assertEquals(null, PhoneNumber.normalize("1912345678"));
        assertEquals(null, PhoneNumber.normalize("0912abc678"));
        assertEquals(null, PhoneNumber.normalize("09+12345678"));
        assertFalse(PhoneNumber.isValid("123"));
        assertTrue(PhoneNumber.isValid("0388 000 111"));
    }

    @Test
    void dinhDangVaAn() {
        assertEquals("0912 345 678", PhoneNumber.format("0912345678"));
        assertEquals("0912 ••• 678", PhoneNumber.masked("0912345678"));
    }

    @Test
    void property_chuanHoaLaLuyDang() {
        Random r = new Random(84);
        char[] nets = {'3', '5', '7', '8', '9'};
        for (int k = 0; k < 5000; k++) {
            StringBuilder s = new StringBuilder("0").append(nets[r.nextInt(nets.length)]);
            for (int i = 0; i < 8; i++) s.append((char) ('0' + r.nextInt(10)));
            String n = s.toString();
            assertEquals(n, PhoneNumber.normalize(n));
            assertEquals(n, PhoneNumber.normalize(PhoneNumber.normalize(n)));
            assertEquals(n, PhoneNumber.normalize(PhoneNumber.format(n)));
            assertEquals(n, PhoneNumber.normalize("+84" + n.substring(1)));
        }
    }
}
