package vn.nhom03.chiabill.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import vn.nhom03.chiabill.domain.user.InviteCode;

class InviteCodeTest {
    @Test
    void goThuongVaGachNoiDeuDuoc() {
        assertEquals("DALAT7", InviteCode.normalize("dal-at7"));
        assertEquals("DALAT7", InviteCode.normalize(" DAL AT7 "));
        assertEquals("DAL-AT7", InviteCode.format("DALAT7"));
    }

    @Test
    void tuChoiMaSai() {
        assertEquals(null, InviteCode.normalize("DALAT"));
        assertEquals(null, InviteCode.normalize("DALAT77"));
        assertEquals(null, InviteCode.normalize("DAL0T7")); // số 0 không có trong bảng chữ
        assertEquals(null, InviteCode.normalize(null));
    }

    @Test
    void property_maSinhRaLuonHopLe() {
        Random r = new Random(7);
        for (int i = 0; i < 5000; i++) {
            String c = InviteCode.generate(r);
            assertEquals(c, InviteCode.normalize(c));
            assertEquals(c, InviteCode.normalize(InviteCode.format(c).toLowerCase()));
            assertTrue(c.length() == InviteCode.LENGTH);
        }
    }
}
