package vn.nhom03.chiabill.data.repo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import vn.nhom03.chiabill.data.db.AppDao;
import vn.nhom03.chiabill.data.db.BillEntity;
import vn.nhom03.chiabill.data.db.BillItemEntity;
import vn.nhom03.chiabill.data.db.DebtStatusEntity;
import vn.nhom03.chiabill.data.db.GroupEntity;
import vn.nhom03.chiabill.data.db.InboxEntity;
import vn.nhom03.chiabill.data.db.MemberEntity;
import vn.nhom03.chiabill.data.db.UserEntity;

/**
 * Dữ liệu demo: 4 tài khoản có số điện thoại (Đạt, Minh, Lan, Tuấn), 1 khách (Hùng), 2 nhóm, 6 hoá đơn
 * (đủ 4 cách chia: đều, theo món, theo %, số tiền tự nhập).
 * Tuấn chưa ở nhóm nào: dùng để demo luồng tìm theo số điện thoại rồi thêm vào nhóm, hoặc tự vào bằng mã mời.
 * Mã mời cố định để dễ demo: nhóm Đà Lạt DAL-AT7, nhóm phòng lab LAB-234.
 * Số điện thoại và số tài khoản ngân hàng đều là GIẢ. Muốn thử quét bằng app ngân hàng thật, vào tab Tôi và khai STK của mình.
 */
final class DemoSeeder {
    static final String DAT = "dat", MINH = "minh", LAN = "lan", HUNG = "hung", TUAN = "tuan";
    private static final long DAY = 24L * 3600 * 1000;

    private DemoSeeder() {}

    static void seed(AppDao dao) {
        long now = System.currentTimeMillis();
        dao.upsertUser(user(DAT, "Đạt", "0901111111", 0, false, "970436", "1012345678", "TRAN TUAN DAT", now - 30 * DAY));
        dao.upsertUser(user(MINH, "Minh", "0902222222", 1, false, "970407", "19033388812345", "NGUYEN VAN MINH", now - 29 * DAY));
        dao.upsertUser(user(LAN, "Lan", "0903333333", 2, false, null, null, null, now - 28 * DAY));
        dao.upsertUser(user(HUNG, "Hùng", null, 3, true, null, null, null, now - 27 * DAY));
        dao.upsertUser(user(TUAN, "Tuấn", "0904444444", 4, false, "970422", "0888777666", "LE MINH TUAN", now - 5 * DAY));

        dao.upsertGroup(group("g1", "Đà Lạt cuối tuần", MINH, "DALAT7", now - 3 * DAY));
        dao.upsertGroup(group("g2", "Ăn trưa phòng lab", DAT, "LAB234", now - 10 * DAY));
        dao.upsertMembers(Arrays.asList(
                new MemberEntity("g1", DAT, 0), new MemberEntity("g1", MINH, 1),
                new MemberEntity("g1", LAN, 2), new MemberEntity("g1", HUNG, 3),
                new MemberEntity("g2", DAT, 0), new MemberEntity("g2", MINH, 1), new MemberEntity("g2", LAN, 2)));

        dao.replaceBill(equal("b-lau", "g1", "Lẩu gà lá é", MINH, 1_140_000, "dat,minh,lan,hung", 8, 0, now - 2 * DAY),
                new ArrayList<BillItemEntity>());

        // Homestay chia theo %: phòng đôi của Đạt và Minh to hơn
        BillEntity home = equal("b-home", "g1", "Homestay 2 đêm", DAT, 1_600_000, "", 0, 0, now - 3 * DAY + 3600_000);
        home.mode = "PERCENT";
        home.sharesCsv = "dat:3000,minh:3000,lan:2000,hung:2000";
        home.category = "LUU_TRU";
        dao.replaceBill(home, new ArrayList<BillItemEntity>());

        BillEntity cafe = equal("b-cafe", "g1", "Cà phê chợ đêm", DAT, 0, "", 0, 0, now - 2 * DAY + 3600_000);
        cafe.mode = "ITEMIZED";
        List<BillItemEntity> items = new ArrayList<>();
        items.add(item("b-cafe", 0, "Cà phê muối", 45_000, "dat"));
        items.add(item("b-cafe", 1, "Cà phê muối", 45_000, "lan"));
        items.add(item("b-cafe", 2, "Bạc xỉu", 40_000, "minh"));
        items.add(item("b-cafe", 3, "Trà atiso", 35_000, "hung"));
        items.add(item("b-cafe", 4, "Bánh tráng nướng (chia)", 60_000, "dat,minh,lan,hung"));
        dao.replaceBill(cafe, items);

        BillEntity xang = equal("b-xang", "g1", "Xăng + gửi xe", LAN, 420_000, "dat,minh,lan,hung", 0, 0, now - DAY);
        xang.category = "DI_LAI";
        dao.replaceBill(xang, new ArrayList<BillItemEntity>());
        dao.replaceBill(equal("b-com", "g2", "Cơm tấm trưa thứ Sáu", DAT, 165_000, "dat,minh,lan", 0, 0, now - 9 * DAY),
                new ArrayList<BillItemEntity>());

        // Trà sữa: mỗi người một số tiền tự nhập
        BillEntity tra = equal("b-tra", "g2", "Trà sữa chiều", MINH, 0, "", 0, 0, now - 8 * DAY);
        tra.mode = "CUSTOM";
        tra.sharesCsv = "dat:35000,minh:42000,lan:29000";
        tra.rounding = 1;
        dao.replaceBill(tra, new ArrayList<BillItemEntity>());

        dao.upsertStatus(status("b-lau:lan", "b-lau", "MARKED_PAID", now - DAY));
        dao.upsertStatus(status("b-xang:dat", "b-xang", "CONFIRMED", now - 3600_000));
        dao.upsertStatus(status("b-com:minh", "b-com", "CONFIRMED", now - 8 * DAY));
        dao.upsertStatus(status("b-tra:dat", "b-tra", "CONFIRMED", now - 7 * DAY));

        dao.insertInbox(inbox(MINH, "Lan báo đã chuyển 308.000đ cho “Lẩu gà lá é”. Mở để xác nhận.", "b-lau", "b-lau:lan", now - DAY));
        dao.insertInbox(inbox(DAT, "Minh thêm hoá đơn “Lẩu gà lá é”: phần của bạn 308.000đ, trả cho Minh.", "b-lau", "b-lau:dat", now - 2 * DAY));
    }

    private static UserEntity user(String id, String name, String phone, int color, boolean guest, String bin, String acc, String holder, long at) {
        UserEntity u = new UserEntity();
        u.id = id;
        u.name = name;
        u.phone = phone;
        u.colorIndex = color;
        u.guest = guest;
        u.bankBin = bin;
        u.accountNo = acc;
        u.accountName = holder;
        u.qrUpdatedAt = bin == null ? 0 : at;
        u.createdAt = at;
        return u;
    }

    private static GroupEntity group(String id, String name, String by, String invite, long at) {
        GroupEntity g = new GroupEntity();
        g.id = id;
        g.name = name;
        g.inviteCode = invite;
        g.createdBy = by;
        g.createdAt = at;
        return g;
    }

    private static BillEntity equal(String id, String groupId, String title, String payer, long subtotal, String participants,
                                    int vat, int svc, long at) {
        BillEntity b = new BillEntity();
        b.id = id;
        b.groupId = groupId;
        b.title = title;
        b.payerId = payer;
        b.mode = "EQUAL";
        b.subtotal = subtotal;
        b.participantsCsv = participants;
        b.vatPercent = vat;
        b.servicePercent = svc;
        b.rounding = 1000;
        b.category = "AN_UONG";
        b.createdBy = payer;
        b.createdAt = at;
        b.updatedAt = at;
        return b;
    }

    private static BillItemEntity item(String billId, int pos, String name, long price, String who) {
        BillItemEntity e = new BillItemEntity();
        e.billId = billId;
        e.position = pos;
        e.name = name;
        e.price = price;
        e.consumersCsv = who;
        return e;
    }

    private static DebtStatusEntity status(String key, String billId, String st, long at) {
        DebtStatusEntity s = new DebtStatusEntity();
        s.debtKey = key;
        s.billId = billId;
        s.status = st;
        s.updatedAt = at;
        return s;
    }

    private static InboxEntity inbox(String to, String msg, String billId, String key, long at) {
        InboxEntity e = new InboxEntity();
        e.recipientId = to;
        e.message = msg;
        e.billId = billId;
        e.debtKey = key;
        e.createdAt = at;
        return e;
    }
}
