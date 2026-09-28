package vn.nhom03.chiabill.data.repo;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import vn.nhom03.chiabill.data.db.AppDao;
import vn.nhom03.chiabill.data.db.BillEntity;
import vn.nhom03.chiabill.data.db.BillItemEntity;
import vn.nhom03.chiabill.data.db.DebtStatusEntity;
import vn.nhom03.chiabill.data.db.GroupEntity;
import vn.nhom03.chiabill.data.db.InboxEntity;
import vn.nhom03.chiabill.data.db.MemberEntity;
import vn.nhom03.chiabill.data.db.UserEntity;
import vn.nhom03.chiabill.domain.ledger.PaymentStateMachine;
import vn.nhom03.chiabill.domain.model.Bill;
import vn.nhom03.chiabill.domain.model.BillItem;
import vn.nhom03.chiabill.domain.model.Debt;
import vn.nhom03.chiabill.domain.model.DebtStatus;
import vn.nhom03.chiabill.domain.model.Money;
import vn.nhom03.chiabill.domain.model.ShareLine;
import vn.nhom03.chiabill.domain.model.SplitMode;
import vn.nhom03.chiabill.domain.model.SplitResult;
import vn.nhom03.chiabill.domain.qr.PaymentTarget;
import vn.nhom03.chiabill.domain.split.SplitEngine;
import vn.nhom03.chiabill.domain.user.InviteCode;
import vn.nhom03.chiabill.domain.user.PhoneNumber;

/**
 * Repository chạy hoàn toàn trên máy (Room). Mọi tài khoản demo dùng chung một CSDL, nên các tài khoản
 * "tương tác" với nhau thật: hoá đơn Minh tạo thì Đạt thấy ngay khi đổi sang tài khoản Đạt.
 * Mọi ghi chạy trên một luồng nền duy nhất nên các thao tác không chen nhau.
 */
public final class LocalLedgerRepository implements LedgerRepository {
    public static final int PALETTE_SIZE = 6;

    private final AppDao dao;
    private final EventSink sink;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MediatorLiveData<AppSnapshot> snapshot = new MediatorLiveData<>();

    private List<UserEntity> users;
    private List<GroupEntity> groups;
    private List<MemberEntity> members;
    private List<BillEntity> bills;
    private List<BillItemEntity> items;
    private List<DebtStatusEntity> statuses;
    private List<InboxEntity> inbox;

    public LocalLedgerRepository(AppDao dao, EventSink sink) {
        this.dao = dao;
        this.sink = sink;
        snapshot.addSource(dao.users(), v -> { users = v; rebuild(); });
        snapshot.addSource(dao.groups(), v -> { groups = v; rebuild(); });
        snapshot.addSource(dao.members(), v -> { members = v; rebuild(); });
        snapshot.addSource(dao.bills(), v -> { bills = v; rebuild(); });
        snapshot.addSource(dao.billItems(), v -> { items = v; rebuild(); });
        snapshot.addSource(dao.debtStatuses(), v -> { statuses = v; rebuild(); });
        snapshot.addSource(dao.inbox(), v -> { inbox = v; rebuild(); });
    }

    private void rebuild() {
        if (users == null || groups == null || members == null || bills == null || items == null
                || statuses == null || inbox == null) return;
        snapshot.setValue(new AppSnapshot(users, groups, members, bills, items, statuses, inbox));
    }

    @Override
    public LiveData<AppSnapshot> snapshot() {
        return snapshot;
    }

    private void onMain(Runnable r) {
        if (r != null) main.post(r);
    }

    private <T> void onMain(Callback<T> cb, T value) {
        if (cb != null) main.post(() -> cb.onResult(value));
    }

    // ---------------------------------------------------------------- demo

    @Override
    public void ensureSeeded(Runnable done) {
        io.execute(() -> {
            if (dao.userCount() == 0) DemoSeeder.seed(dao);
            onMain(done);
        });
    }

    @Override
    public void resetDemo(Runnable done) {
        io.execute(() -> {
            dao.clearAll();
            DemoSeeder.seed(dao);
            onMain(done);
        });
    }

    // ---------------------------------------------------------------- tài khoản

    @Override
    public void createAccount(String name, String phone, Callback<String> onId, Callback<String> onError) {
        io.execute(() -> {
            String n = name == null ? "" : name.trim();
            if (n.isEmpty()) { onMain(onError, "Cần nhập tên."); return; }
            String p = PhoneNumber.normalize(phone);
            if (p == null) { onMain(onError, "Số điện thoại không hợp lệ (10 số, bắt đầu bằng 03, 05, 07, 08 hoặc 09)."); return; }
            UserEntity existing = dao.userByPhoneNow(p);
            if (existing != null) {
                onMain(onError, "Số " + PhoneNumber.format(p) + " đã có tài khoản (" + existing.name + ").");
                return;
            }
            UserEntity u = new UserEntity();
            u.id = UUID.randomUUID().toString();
            u.name = n;
            u.phone = p;
            u.colorIndex = dao.userCount() % PALETTE_SIZE;
            u.createdAt = System.currentTimeMillis();
            dao.upsertUser(u);
            onMain(onId, u.id);
        });
    }

    @Override
    public void findUserByPhone(String rawPhone, Callback<UserEntity> onResult, Callback<String> onError) {
        io.execute(() -> {
            String p = PhoneNumber.normalize(rawPhone);
            if (p == null) {
                onMain(onError, "Số điện thoại không hợp lệ. Nhập đủ 10 số, vd 0904 444 444.");
                return;
            }
            onMain(onResult, dao.userByPhoneNow(p));
        });
    }

    @Override
    public void addMember(String groupId, String userId, String actorId, Runnable done, Callback<String> onError) {
        io.execute(() -> {
            GroupEntity g = dao.groupNow(groupId);
            if (g == null) { onMain(onError, "Nhóm không còn."); return; }
            List<String> ids = activeMemberIds(groupId);
            if (!ids.contains(actorId)) { onMain(onError, "Bạn không ở trong nhóm này."); return; }
            if (ids.contains(userId)) { onMain(onError, nameOf(userId) + " đã ở trong nhóm."); return; }
            UserEntity u = dao.userNow(userId);
            if (u == null) { onMain(onError, "Tài khoản không còn."); return; }
            putMember(groupId, userId);
            notifyUser(userId, nameOf(actorId) + " đã thêm bạn vào nhóm “" + g.name + "”.", null, null);
            onMain(done);
        });
    }

    @Override
    public void joinByInviteCode(String rawCode, String userId, Callback<String> onGroupId, Callback<String> onError) {
        io.execute(() -> {
            String code = InviteCode.normalize(rawCode);
            if (code == null) { onMain(onError, "Mã mời gồm 6 ký tự, vd DAL-AT7."); return; }
            GroupEntity g = dao.groupByInviteNow(code);
            if (g == null) { onMain(onError, "Không có nhóm nào dùng mã " + InviteCode.format(code) + "."); return; }
            UserEntity u = dao.userNow(userId);
            if (u == null || u.guest) { onMain(onError, "Tài khoản không hợp lệ."); return; }
            if (activeMemberIds(g.id).contains(userId)) { onMain(onGroupId, g.id); return; }
            putMember(g.id, userId);
            for (String id : activeMemberIds(g.id)) {
                if (!id.equals(userId)) notifyUser(id, u.name + " đã vào nhóm “" + g.name + "” bằng mã mời.", null, null);
            }
            onMain(onGroupId, g.id);
        });
    }

    @Override
    public void removeMember(String groupId, String userId, String actorId, Runnable done, Callback<String> onError) {
        io.execute(() -> {
            GroupEntity g = dao.groupNow(groupId);
            if (g == null) { onMain(onError, "Nhóm không còn."); return; }
            if (!actorId.equals(g.createdBy)) { onMain(onError, "Chỉ người tạo nhóm được xoá thành viên."); return; }
            if (actorId.equals(userId)) { onMain(onError, "Muốn ra khỏi nhóm thì dùng “Rời nhóm”."); return; }
            if (!activeMemberIds(groupId).contains(userId)) { onMain(done); return; }
            String open = openDebtReason(groupId, userId);
            if (open != null) { onMain(onError, nameOf(userId) + " " + open + ", chưa xoá được."); return; }
            dao.setMemberActive(groupId, userId, false);
            notifyUser(userId, nameOf(actorId) + " đã xoá bạn khỏi nhóm “" + g.name + "”.", null, null);
            onMain(done);
        });
    }

    @Override
    public void leaveGroup(String groupId, String userId, Runnable done, Callback<String> onError) {
        io.execute(() -> {
            GroupEntity g = dao.groupNow(groupId);
            if (g == null) { onMain(done); return; }
            String open = openDebtReason(groupId, userId);
            if (open != null) { onMain(onError, "Bạn " + open + ". Tất toán xong rồi mới rời nhóm được."); return; }
            dao.setMemberActive(groupId, userId, false);
            if (userId.equals(g.createdBy)) {
                // Người tạo rời nhóm: chuyển quyền quản lý cho tài khoản (không phải khách) còn lại đầu tiên;
                // nhóm chỉ còn khách thì giao cho người đầu tiên còn lại.
                List<String> left = activeMemberIds(groupId);
                String next = left.isEmpty() ? null : left.get(0);
                for (String id : left) {
                    UserEntity u = dao.userNow(id);
                    if (u != null && !u.guest) { next = id; break; }
                }
                if (next != null) { g.createdBy = next; dao.upsertGroup(g); }
            }
            String name = nameOf(userId);
            for (String id : activeMemberIds(groupId)) notifyUser(id, name + " đã rời nhóm “" + g.name + "”.", null, null);
            onMain(done);
        });
    }

    @Override
    public void renameUser(String userId, String name, Runnable done, Callback<String> onError) {
        io.execute(() -> {
            String n = name == null ? "" : name.trim();
            if (n.isEmpty()) { onMain(onError, "Tên không được để trống."); return; }
            if (n.length() > 40) { onMain(onError, "Tên tối đa 40 ký tự."); return; }
            UserEntity u = dao.userNow(userId);
            if (u == null) { onMain(onError, "Tài khoản không còn."); return; }
            u.name = n;
            dao.upsertUser(u);
            onMain(done);
        });
    }

    @Override
    public void setPaymentTarget(String userId, PaymentTarget t, Runnable done) {
        io.execute(() -> {
            UserEntity u = dao.userNow(userId);
            if (u != null) {
                u.bankBin = t.getBankBin();
                u.accountNo = t.getAccountNo();
                u.accountName = t.getAccountName();
                u.qrUpdatedAt = System.currentTimeMillis();
                dao.upsertUser(u);
            }
            onMain(done);
        });
    }

    @Override
    public void clearPaymentTarget(String userId, Runnable done) {
        io.execute(() -> {
            UserEntity u = dao.userNow(userId);
            if (u != null) {
                u.bankBin = null;
                u.accountNo = null;
                u.accountName = null;
                u.qrUpdatedAt = 0;
                dao.upsertUser(u);
            }
            onMain(done);
        });
    }

    // ---------------------------------------------------------------- nhóm

    @Override
    public void createGroup(String name, String creatorId, List<String> memberIds, List<String> guestNames, Callback<String> onId) {
        io.execute(() -> {
            long now = System.currentTimeMillis();
            GroupEntity g = new GroupEntity();
            g.id = UUID.randomUUID().toString();
            g.name = name.trim();
            g.createdBy = creatorId;
            g.createdAt = now;
            g.inviteCode = newInviteCode();
            dao.upsertGroup(g);

            Set<String> ids = new LinkedHashSet<>();
            ids.add(creatorId);
            ids.addAll(memberIds);
            int base = dao.userCount();
            for (String guest : guestNames) {
                if (guest.trim().isEmpty()) continue;
                UserEntity u = new UserEntity();
                u.id = "guest-" + UUID.randomUUID();
                u.name = guest.trim();
                u.guest = true;
                u.colorIndex = (base++) % PALETTE_SIZE;
                u.createdAt = now;
                dao.upsertUser(u);
                ids.add(u.id);
            }
            List<MemberEntity> rows = new ArrayList<>();
            int pos = 0;
            for (String id : ids) rows.add(new MemberEntity(g.id, id, pos++));
            dao.upsertMembers(rows);
            String creatorName = nameOf(creatorId);
            for (String id : ids) {
                if (!id.equals(creatorId)) notifyUser(id, creatorName + " đã thêm bạn vào nhóm “" + g.name + "”.", null, null);
            }
            onMain(onId, g.id);
        });
    }

    // ---------------------------------------------------------------- hoá đơn

    @Override
    public void saveBill(BillInput in, String actorId, Callback<String> onId, Callback<String> onError) {
        io.execute(() -> {
            if (!activeMemberIds(in.groupId).contains(actorId)) { onMain(onError, "Bạn không còn ở trong nhóm này."); return; }
            List<String> order = memberIds(in.groupId);
            String invalid = SplitEngine.check(in.spec, order);
            if (invalid != null) { onMain(onError, invalid); return; }
            SplitResult result = SplitEngine.split(in.spec, order);
            if (!result.isSavable()) {
                onMain(onError, result.getUnassignedItems().isEmpty()
                        ? "Hoá đơn chưa có số tiền."
                        : "Còn món chưa gán: " + String.join(", ", result.getUnassignedItems()));
                return;
            }
            long now = System.currentTimeMillis();
            BillEntity old = in.id == null ? null : dao.billNow(in.id);
            if (old != null && !actorId.equals(old.createdBy) && !actorId.equals(old.payerId)) {
                onMain(onError, "Chỉ người tạo hoặc người trả được sửa hoá đơn này.");
                return;
            }
            if (old == null) {
                // Hoá đơn mới chỉ gồm thành viên hiện tại (sửa hoá đơn cũ thì người đã rời vẫn giữ phần của mình)
                List<String> active = activeMemberIds(in.groupId);
                for (ShareLine l : result.getLines()) {
                    if (!active.contains(l.getMemberId()) && (l.getAmount() > 0 || l.getMemberId().equals(in.spec.getPayerId()))) {
                        onMain(onError, nameOf(l.getMemberId()) + " không còn ở trong nhóm.");
                        return;
                    }
                }
            }
            Map<String, Long> oldAmounts = new HashMap<>();
            String oldPayer = null;
            if (old != null) {
                oldPayer = old.payerId;
                SplitResult prev = SplitEngine.split(Mappers.toBill(old, dao.itemsNow(old.id)).getSpec(), order);
                for (ShareLine l : prev.getLines()) oldAmounts.put(l.getMemberId(), l.getAmount());
            }

            BillEntity b = new BillEntity();
            b.id = old != null ? old.id : UUID.randomUUID().toString();
            b.groupId = in.groupId;
            b.title = in.title.trim().isEmpty() ? "Hoá đơn" : in.title.trim();
            b.payerId = in.spec.getPayerId();
            b.mode = in.spec.getMode().name();
            SplitMode mode = in.spec.getMode();
            b.subtotal = mode == SplitMode.EQUAL || mode == SplitMode.PERCENT ? in.spec.getSubtotal() : 0;
            b.participantsCsv = mode == SplitMode.EQUAL ? Mappers.csv(in.spec.getParticipants()) : "";
            b.sharesCsv = mode == SplitMode.CUSTOM || mode == SplitMode.PERCENT ? Mappers.shares(in.spec.getShares()) : "";
            b.category = in.category.name();
            b.vatPercent = in.spec.getVatPercent();
            b.servicePercent = in.spec.getServicePercent();
            b.rounding = in.spec.getRounding();
            b.createdBy = old != null ? old.createdBy : actorId;
            b.createdAt = in.date > 0 ? in.date : (old != null ? old.createdAt : now);
            b.updatedAt = now;

            List<BillItemEntity> rows = new ArrayList<>();
            if (in.spec.getMode() == SplitMode.ITEMIZED) {
                int pos = 0;
                for (BillItem it : in.spec.getItems()) {
                    if (it.getPrice() <= 0) continue;
                    BillItemEntity e = new BillItemEntity();
                    e.billId = b.id;
                    e.position = pos++;
                    e.name = it.getName().trim();
                    e.price = it.getPrice();
                    e.consumersCsv = Mappers.csv(it.getConsumers());
                    rows.add(e);
                }
            }
            dao.replaceBill(b, rows);

            // Sửa hoá đơn: khoản đã xác nhận giữ nguyên; khoản đổi số tiền hoặc đổi người nhận thì quay về PENDING.
            if (old != null) {
                boolean payerChanged = !b.payerId.equals(oldPayer);
                for (DebtStatusEntity s : dao.statusesOfBillNow(b.id)) {
                    if (DebtStatus.CONFIRMED.name().equals(s.status)) continue;
                    String debtor = s.debtKey.substring(s.debtKey.indexOf(':') + 1);
                    ShareLine now2 = result.lineOf(debtor);
                    Long before = oldAmounts.get(debtor);
                    if (payerChanged || now2 == null || before == null || before != now2.getAmount()) dao.deleteStatus(s.debtKey);
                }
            }

            String actorName = nameOf(actorId);
            for (ShareLine l : result.getLines()) {
                if (l.getMemberId().equals(b.payerId) || l.getMemberId().equals(actorId) || l.getAmount() <= 0) continue;
                Long before = oldAmounts.get(l.getMemberId());
                if (old != null && before != null && before == l.getAmount() && b.payerId.equals(oldPayer)) continue;
                String msg = old == null
                        ? actorName + " thêm hoá đơn “" + b.title + "”: phần của bạn " + Money.format(l.getAmount()) + ", trả cho " + nameOf(b.payerId) + "."
                        : actorName + " sửa hoá đơn “" + b.title + "”: phần của bạn giờ là " + Money.format(l.getAmount()) + ".";
                notifyUser(l.getMemberId(), msg, b.id, Debt.keyOf(b.id, l.getMemberId()));
            }
            onMain(onId, b.id);
        });
    }

    @Override
    public void deleteBill(String billId, String actorId, Runnable done, Callback<String> onError) {
        io.execute(() -> {
            BillEntity b = dao.billNow(billId);
            if (b == null) { onMain(done); return; }
            if (!actorId.equals(b.createdBy) && !actorId.equals(b.payerId)) {
                onMain(onError, "Chỉ người tạo hoặc người trả được xoá hoá đơn này.");
                return;
            }
            for (DebtStatusEntity s : dao.statusesOfBillNow(billId)) {
                if (DebtStatus.CONFIRMED.name().equals(s.status)) {
                    onMain(onError, "Hoá đơn đã có khoản được xác nhận thanh toán, không xoá được.");
                    return;
                }
            }
            dao.deleteBill(billId);
            onMain(done);
        });
    }

    // ---------------------------------------------------------------- khoản nợ

    @Override
    public void applyDebtAction(String debtKey, PaymentStateMachine.Action action, String actorId, Runnable done, Callback<String> onError) {
        io.execute(() -> {
            int sep = debtKey.indexOf(':');
            if (sep < 0) { onMain(onError, "Khoản nợ không hợp lệ."); return; }
            String billId = debtKey.substring(0, sep);
            String debtorId = debtKey.substring(sep + 1);
            BillEntity be = dao.billNow(billId);
            if (be == null) { onMain(onError, "Hoá đơn không còn."); return; }
            Bill bill = Mappers.toBill(be, dao.itemsNow(billId));
            ShareLine line = SplitEngine.split(bill.getSpec(), memberIds(be.groupId)).lineOf(debtorId);
            if (line == null || debtorId.equals(be.payerId) || line.getAmount() <= 0) {
                onMain(onError, "Khoản nợ không còn.");
                return;
            }
            DebtStatusEntity cur = dao.statusNow(debtKey);
            DebtStatus from = DebtStatus.PENDING;
            if (cur != null) {
                try {
                    from = DebtStatus.valueOf(cur.status);
                } catch (IllegalArgumentException ignored) {
                    // trạng thái lạ trong DB → coi như PENDING, giống AppSnapshot
                }
            }
            PaymentStateMachine.Role role = PaymentStateMachine.roleOf(actorId, debtorId, be.payerId);
            if (!PaymentStateMachine.canApply(from, action, role)) {
                onMain(onError, "Không thực hiện được thao tác này ở trạng thái hiện tại.");
                return;
            }
            DebtStatusEntity s = new DebtStatusEntity();
            s.debtKey = debtKey;
            s.billId = billId;
            s.status = PaymentStateMachine.apply(from, action, role).name();
            s.updatedAt = System.currentTimeMillis();
            dao.upsertStatus(s);

            String amount = Money.format(line.getAmount());
            String debtorName = nameOf(debtorId);
            String creditorName = nameOf(be.payerId);
            switch (action) {
                case MARK_PAID:
                    notifyUser(be.payerId, debtorName + " báo đã chuyển " + amount + " cho “" + be.title + "”. Mở để xác nhận.", billId, debtKey);
                    break;
                case CONFIRM:
                    notifyUser(debtorId, creditorName + " đã xác nhận nhận " + amount + " (“" + be.title + "”). Xong khoản này.", billId, debtKey);
                    break;
                case DISPUTE:
                    notifyUser(debtorId, creditorName + " báo chưa nhận được " + amount + " (“" + be.title + "”). Kiểm tra lại giao dịch.", billId, debtKey);
                    break;
                default:
                    break;
            }
            onMain(done);
        });
    }

    @Override
    public void sendReminder(String recipientId, String fromId, String message, String billId, String debtKey) {
        io.execute(() -> notifyUser(recipientId, message, billId, debtKey));
    }

    // ---------------------------------------------------------------- nội bộ

    /** Chạy trên luồng io. Khách không có app nên không nhận thông báo trong app. */
    private void notifyUser(String recipientId, String message, String billId, String debtKey) {
        UserEntity u = dao.userNow(recipientId);
        if (u == null || u.guest) return;
        InboxEntity e = new InboxEntity();
        e.recipientId = recipientId;
        e.message = message;
        e.billId = billId;
        e.debtKey = debtKey;
        e.createdAt = System.currentTimeMillis();
        dao.insertInbox(e);
        if (sink != null) {
            final String name = u.name;
            main.post(() -> sink.onMessage(recipientId, name, message, billId, debtKey));
        }
    }

    /** Thêm mới hoặc bật lại một người đã rời (giữ vị trí cũ để hoá đơn cũ chia như trước). Chạy trên io. */
    private void putMember(String groupId, String userId) {
        MemberEntity existing = dao.memberNow(groupId, userId);
        if (existing != null) {
            dao.setMemberActive(groupId, userId, true);
            return;
        }
        List<MemberEntity> row = new ArrayList<>();
        row.add(new MemberEntity(groupId, userId, dao.maxPositionNow(groupId) + 1));
        dao.upsertMembers(row);
    }

    /** Sinh mã mời chưa ai dùng. Chạy trên io. */
    private String newInviteCode() {
        while (true) {
            String c = InviteCode.generate(random);
            if (dao.groupByInviteNow(c) == null) return c;
        }
    }

    /**
     * Lý do người này chưa rời/xoá được khỏi nhóm ("còn nợ …", "còn được nợ …"), hoặc null nếu mọi khoản
     * liên quan đã xác nhận xong. Chạy trên io.
     */
    private String openDebtReason(String groupId, String userId) {
        List<String> order = memberIds(groupId);
        Map<String, String> status = new HashMap<>();
        for (DebtStatusEntity s : dao.statusesOfGroupNow(groupId)) status.put(s.debtKey, s.status);
        long owes = 0, owed = 0;
        for (BillEntity be : dao.billsOfGroupNow(groupId)) {
            SplitResult r;
            try {
                r = SplitEngine.split(Mappers.toBill(be, dao.itemsNow(be.id)).getSpec(), order);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            for (ShareLine l : r.getLines()) {
                if (l.getMemberId().equals(be.payerId) || l.getAmount() <= 0) continue;
                if (DebtStatus.CONFIRMED.name().equals(status.get(Debt.keyOf(be.id, l.getMemberId())))) continue;
                if (l.getMemberId().equals(userId)) owes += l.getAmount();
                if (be.payerId.equals(userId)) owed += l.getAmount();
            }
        }
        if (owes > 0) return "còn nợ " + Money.format(owes);
        if (owed > 0) return "còn chờ người khác trả " + Money.format(owed);
        return null;
    }

    private List<String> activeMemberIds(String groupId) {
        List<String> ids = new ArrayList<>();
        for (MemberEntity m : dao.membersNow(groupId)) if (m.active) ids.add(m.userId);
        return ids;
    }

    /** Mọi người từng ở nhóm, theo vị trí: thứ tự chia hoá đơn. */
    private List<String> memberIds(String groupId) {
        List<String> ids = new ArrayList<>();
        for (MemberEntity m : dao.membersNow(groupId)) ids.add(m.userId);
        return ids;
    }

    private String nameOf(String userId) {
        UserEntity u = dao.userNow(userId);
        return u == null ? "?" : u.name;
    }
}
