package vn.nhom03.chiabill.data.repo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import vn.nhom03.chiabill.data.db.BillEntity;
import vn.nhom03.chiabill.data.db.BillItemEntity;
import vn.nhom03.chiabill.data.db.DebtStatusEntity;
import vn.nhom03.chiabill.data.db.GroupEntity;
import vn.nhom03.chiabill.data.db.InboxEntity;
import vn.nhom03.chiabill.data.db.MemberEntity;
import vn.nhom03.chiabill.data.db.UserEntity;
import vn.nhom03.chiabill.domain.ledger.Ledger;
import vn.nhom03.chiabill.domain.model.Bill;
import vn.nhom03.chiabill.domain.model.DebtStatus;
import vn.nhom03.chiabill.domain.qr.PaymentTarget;

/** Ảnh chụp bất biến của toàn bộ dữ liệu tại một thời điểm, kèm sổ nợ đã tính. UI chỉ đọc từ đây. */
public final class AppSnapshot {
    private final Map<String, UserEntity> users = new LinkedHashMap<>();
    private final List<GroupEntity> groups;
    /** Mọi thành viên từng ở nhóm, theo thứ tự: dùng để chia hoá đơn. */
    private final Map<String, List<String>> allMembers = new HashMap<>();
    /** Thành viên hiện tại: dùng cho giao diện, quyền và hoá đơn mới. */
    private final Map<String, List<String>> members = new HashMap<>();
    private final Map<String, BillEntity> billEntities = new HashMap<>();
    private final List<InboxEntity> inbox;
    private final Ledger ledger;

    AppSnapshot(List<UserEntity> users, List<GroupEntity> groups, List<MemberEntity> members, List<BillEntity> bills,
                List<BillItemEntity> items, List<DebtStatusEntity> statuses, List<InboxEntity> inbox) {
        for (UserEntity u : users) this.users.put(u.id, u);
        this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
        for (MemberEntity m : members) {
            List<String> all = this.allMembers.get(m.groupId);
            if (all == null) { all = new ArrayList<>(); this.allMembers.put(m.groupId, all); }
            all.add(m.userId);
            if (!m.active) continue;
            List<String> l = this.members.get(m.groupId);
            if (l == null) { l = new ArrayList<>(); this.members.put(m.groupId, l); }
            l.add(m.userId);
        }
        Map<String, List<BillItemEntity>> itemsByBill = new HashMap<>();
        for (BillItemEntity it : items) {
            List<BillItemEntity> l = itemsByBill.get(it.billId);
            if (l == null) { l = new ArrayList<>(); itemsByBill.put(it.billId, l); }
            l.add(it);
        }
        List<Bill> domainBills = new ArrayList<>();
        for (BillEntity b : bills) {
            billEntities.put(b.id, b);
            try {
                domainBills.add(Mappers.toBill(b, itemsByBill.get(b.id)));
            } catch (IllegalArgumentException ex) {
                // Dữ liệu hỏng thì bỏ qua hoá đơn đó thay vì làm sập cả app.
            }
        }
        Map<String, DebtStatus> st = new HashMap<>();
        for (DebtStatusEntity s : statuses) {
            try {
                st.put(s.debtKey, DebtStatus.valueOf(s.status));
            } catch (IllegalArgumentException ignored) {
                // trạng thái lạ → coi như PENDING
            }
        }
        this.inbox = Collections.unmodifiableList(new ArrayList<>(inbox));
        this.ledger = new Ledger(this.allMembers, domainBills, st);
    }

    public Ledger ledger() { return ledger; }

    public UserEntity user(String id) { return users.get(id); }

    public String name(String id) {
        UserEntity u = users.get(id);
        return u == null ? "?" : u.name;
    }

    public int colorIndex(String id) {
        UserEntity u = users.get(id);
        return u == null ? 0 : u.colorIndex;
    }

    public List<UserEntity> accounts() {
        List<UserEntity> out = new ArrayList<>();
        for (UserEntity u : users.values()) if (!u.guest) out.add(u);
        return out;
    }

    public PaymentTarget paymentTarget(String userId) { return Mappers.toTarget(users.get(userId)); }

    public List<GroupEntity> groupsOf(String userId) {
        List<GroupEntity> out = new ArrayList<>();
        for (GroupEntity g : groups) if (membersOf(g.id).contains(userId)) out.add(g);
        return out;
    }

    public GroupEntity group(String id) {
        for (GroupEntity g : groups) if (g.id.equals(id)) return g;
        return null;
    }

    /** Thành viên hiện tại của nhóm. */
    public List<String> membersOf(String groupId) {
        List<String> l = members.get(groupId);
        return l == null ? Collections.<String>emptyList() : Collections.unmodifiableList(l);
    }

    /** Mọi người từng ở nhóm, kể cả đã rời: thứ tự chia hoá đơn. */
    public List<String> allMembersOf(String groupId) {
        List<String> l = allMembers.get(groupId);
        return l == null ? Collections.<String>emptyList() : Collections.unmodifiableList(l);
    }

    public BillEntity billEntity(String billId) { return billEntities.get(billId); }

    public List<InboxEntity> inboxOf(String userId) {
        List<InboxEntity> out = new ArrayList<>();
        for (InboxEntity e : inbox) if (e.recipientId.equals(userId)) out.add(e);
        return out;
    }
}
