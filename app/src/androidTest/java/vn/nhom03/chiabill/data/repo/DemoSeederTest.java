package vn.nhom03.chiabill.data.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import vn.nhom03.chiabill.data.db.AppDao;
import vn.nhom03.chiabill.data.db.AppDatabase;
import vn.nhom03.chiabill.data.db.BillEntity;
import vn.nhom03.chiabill.data.db.MemberEntity;
import vn.nhom03.chiabill.domain.model.Bill;
import vn.nhom03.chiabill.domain.model.SplitResult;
import vn.nhom03.chiabill.domain.split.SplitEngine;

/** Room thật (in-memory) + dữ liệu demo + SplitEngine: kiểm cả đường đi từ DB tới con số. */
@RunWith(AndroidJUnit4.class)
public class DemoSeederTest {
    private AppDatabase db;
    private AppDao dao;

    @Before
    public void setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.dao();
        DemoSeeder.seed(dao);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private SplitResult split(String billId, String groupId) {
        BillEntity e = dao.billNow(billId);
        assertNotNull(e);
        Bill bill = Mappers.toBill(e, dao.itemsNow(billId));
        List<String> order = new ArrayList<>();
        for (MemberEntity m : dao.membersNow(groupId)) order.add(m.userId);
        return SplitEngine.split(bill.getSpec(), order);
    }

    @Test
    public void seedTaoDuTaiKhoan() {
        assertEquals(5, dao.userCount());
        assertTrue(dao.userNow("hung").guest);
        assertTrue(dao.userNow("minh").hasPaymentProfile());
    }

    @Test
    public void timTheoSoDienThoaiChiRaDungMotNguoi() {
        assertEquals("tuan", dao.userByPhoneNow("0904444444").id);
        assertEquals(null, dao.userByPhoneNow("0909999999"));
        assertEquals(null, dao.userByPhoneNow("0904 444 444")); // DAO nhận số đã chuẩn hoá; repository lo chuẩn hoá
        assertEquals(0, dao.membersNow("g1").size() - 4); // Tuấn chưa ở nhóm nào
        assertEquals(3, dao.maxPositionNow("g1"));
    }

    @Test
    public void maMoiVaCheDoChiaMoi() {
        assertEquals("g1", dao.groupByInviteNow("DALAT7").id);
        assertEquals(null, dao.groupByInviteNow("ZZZZZZ"));
        SplitResult home = split("b-home", "g1");
        assertEquals(480_000, home.lineOf("minh").getAmount());
        assertEquals(320_000, home.lineOf("hung").getAmount());
        SplitResult tra = split("b-tra", "g2");
        assertEquals(106_000, tra.getTotal());
        assertEquals(29_000, tra.lineOf("lan").getAmount());
        assertEquals("LUU_TRU", dao.billNow("b-home").category);
    }

    @Test
    public void lauGaQuaRoomVanDungSo() {
        SplitResult r = split("b-lau", "g1");
        assertEquals(1_231_200, r.getTotal());
        assertEquals(308_000, r.lineOf("dat").getAmount());
        assertEquals(307_200, r.lineOf("minh").getAmount());
        assertTrue(r.isBalanced());
    }

    @Test
    public void caPheTheoMonQuaRoom() {
        SplitResult r = split("b-cafe", "g1");
        assertEquals(225_000, r.getTotal());
        assertEquals(50_000, r.lineOf("hung").getAmount());
        assertTrue(r.isBalanced());
    }

    @Test
    public void xoaHoaDonXoaCaMonVaTrangThai() {
        dao.deleteBill("b-lau");
        assertEquals(null, dao.billNow("b-lau"));
        assertEquals(0, dao.statusesOfBillNow("b-lau").size());
    }
}
