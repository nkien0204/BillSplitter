package vn.nhom03.chiabill.domain.ledger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import vn.nhom03.chiabill.domain.model.Bill;
import vn.nhom03.chiabill.domain.model.Debt;
import vn.nhom03.chiabill.domain.model.DebtStatus;
import vn.nhom03.chiabill.domain.model.ShareLine;
import vn.nhom03.chiabill.domain.model.SplitResult;
import vn.nhom03.chiabill.domain.model.Transfer;
import vn.nhom03.chiabill.domain.split.SplitEngine;

/**
 * Sổ nợ của toàn bộ dữ liệu: từ hoá đơn + trạng thái khoản nợ suy ra khoản nợ, số dư, tất toán.
 * Bất biến, dựng lại mỗi khi dữ liệu đổi (dữ liệu của app nhỏ nên không cần tăng dần).
 */
public final class Ledger {
    private final Map<String, List<String>> groupMembers;
    private final List<Bill> bills;
    private final Map<String, DebtStatus> statuses;
    private final Map<String, SplitResult> splits = new HashMap<>();
    private final List<Debt> debts = new ArrayList<>();

    /**
     * @param groupMembers groupId → thành viên theo thứ tự
     * @param bills        mọi hoá đơn
     * @param statuses     khoá Debt.keyOf → trạng thái; thiếu = PENDING
     */
    public Ledger(Map<String, List<String>> groupMembers, List<Bill> bills, Map<String, DebtStatus> statuses) {
        this.groupMembers = groupMembers;
        this.bills = Collections.unmodifiableList(new ArrayList<>(bills));
        this.statuses = statuses;
        for (Bill b : this.bills) {
            List<String> order = groupMembers.get(b.getGroupId());
            if (order == null) order = Collections.emptyList();
            SplitResult r = SplitEngine.split(b.getSpec(), order);
            splits.put(b.getId(), r);
            for (ShareLine l : r.getLines()) {
                if (l.getMemberId().equals(b.getPayerId()) || l.getAmount() <= 0) continue;
                DebtStatus st = statuses.get(Debt.keyOf(b.getId(), l.getMemberId()));
                debts.add(new Debt(b.getId(), b.getGroupId(), l.getMemberId(), b.getPayerId(), l.getAmount(),
                        st == null ? DebtStatus.PENDING : st));
            }
        }
    }

    public List<Bill> bills() { return bills; }

    public SplitResult splitOf(String billId) { return splits.get(billId); }

    public Bill bill(String billId) {
        for (Bill b : bills) if (b.getId().equals(billId)) return b;
        return null;
    }

    public List<Bill> billsOfGroup(String groupId) {
        List<Bill> out = new ArrayList<>();
        for (Bill b : bills) if (b.getGroupId().equals(groupId)) out.add(b);
        return out;
    }

    public List<Debt> debts() { return Collections.unmodifiableList(debts); }

    public Debt debt(String key) {
        for (Debt d : debts) if (d.getKey().equals(key)) return d;
        return null;
    }

    public List<Debt> debtsOfGroup(String groupId) {
        List<Debt> out = new ArrayList<>();
        for (Debt d : debts) if (d.getGroupId().equals(groupId)) out.add(d);
        return out;
    }

    public List<Debt> debtsOfBill(String billId) {
        List<Debt> out = new ArrayList<>();
        for (Debt d : debts) if (d.getBillId().equals(billId)) out.add(d);
        return out;
    }

    /** Số tiền người khác còn nợ userId (chưa xác nhận), trong nhóm hoặc toàn bộ (groupId = null). */
    public long owedTo(String userId, String groupId) {
        long s = 0;
        for (Debt d : debts) {
            if (!d.getStatus().isOpen() || !d.getCreditorId().equals(userId)) continue;
            if (groupId == null || d.getGroupId().equals(groupId)) s += d.getAmount();
        }
        return s;
    }

    /** Số tiền userId còn nợ người khác (chưa xác nhận). */
    public long owedBy(String userId, String groupId) {
        long s = 0;
        for (Debt d : debts) {
            if (!d.getStatus().isOpen() || !d.getDebtorId().equals(userId)) continue;
            if (groupId == null || d.getGroupId().equals(groupId)) s += d.getAmount();
        }
        return s;
    }

    /** Số dư ròng mỗi thành viên nhóm trên các khoản chưa xác nhận. */
    public Map<String, Long> netBalances(String groupId) {
        Map<String, Long> net = new LinkedHashMap<>();
        List<String> order = groupMembers.get(groupId);
        if (order != null) for (String id : order) net.put(id, 0L);
        for (Debt d : debtsOfGroup(groupId)) {
            if (!d.getStatus().isOpen()) continue;
            net.put(d.getCreditorId(), net.getOrDefault(d.getCreditorId(), 0L) + d.getAmount());
            net.put(d.getDebtorId(), net.getOrDefault(d.getDebtorId(), 0L) - d.getAmount());
        }
        return net;
    }

    public List<Transfer> settlement(String groupId) {
        return DebtSimplifier.simplify(netBalances(groupId));
    }

    public int openDebtCount(String groupId) {
        int n = 0;
        for (Debt d : debtsOfGroup(groupId)) if (d.getStatus().isOpen()) n++;
        return n;
    }
}
