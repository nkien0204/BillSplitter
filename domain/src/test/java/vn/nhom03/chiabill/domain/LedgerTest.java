package vn.nhom03.chiabill.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import vn.nhom03.chiabill.domain.ledger.DebtSimplifier;
import vn.nhom03.chiabill.domain.ledger.Ledger;
import vn.nhom03.chiabill.domain.ledger.PaymentStateMachine;
import vn.nhom03.chiabill.domain.ledger.PaymentStateMachine.Action;
import vn.nhom03.chiabill.domain.ledger.PaymentStateMachine.Role;
import vn.nhom03.chiabill.domain.model.Bill;
import vn.nhom03.chiabill.domain.model.BillSpec;
import vn.nhom03.chiabill.domain.model.Debt;
import vn.nhom03.chiabill.domain.model.DebtStatus;
import vn.nhom03.chiabill.domain.model.SplitMode;
import vn.nhom03.chiabill.domain.model.Transfer;

class LedgerTest {
    private static final List<String> G = Fixtures.list("dat", "minh", "lan", "hung");

    private static Ledger demo(Map<String, DebtStatus> st) {
        Map<String, List<String>> gm = new HashMap<>();
        gm.put("g1", G);
        List<Bill> bills = new ArrayList<>();
        bills.add(new Bill("b1", "g1", "Lẩu", 1, "minh", new BillSpec("minh", SplitMode.EQUAL, 1_140_000, G, null, 8, 0, 1000)));
        bills.add(new Bill("b3", "g1", "Xăng", 2, "lan", new BillSpec("lan", SplitMode.EQUAL, 420_000, G, null, 0, 0, 1000)));
        return new Ledger(gm, bills, st);
    }

    @Test
    void sinhKhoanNoTuHoaDon() {
        Ledger l = demo(new HashMap<String, DebtStatus>());
        assertEquals(6, l.debts().size());
        Debt d = l.debt(Debt.keyOf("b1", "dat"));
        assertEquals("minh", d.getCreditorId());
        assertEquals(308_000, d.getAmount());
        assertEquals(DebtStatus.PENDING, d.getStatus());
        assertEquals(308_000 + 105_000, l.owedBy("dat", "g1"));
    }

    @Test
    void khoanDaXacNhanKhongTinhVaoSoDu() {
        Map<String, DebtStatus> st = new HashMap<>();
        st.put(Debt.keyOf("b3", "dat"), DebtStatus.CONFIRMED);
        Ledger l = demo(st);
        assertEquals(308_000, l.owedBy("dat", "g1"));
    }

    @Test
    void tatToanGomVeItGiaoDich() {
        Ledger l = demo(new HashMap<String, DebtStatus>());
        List<Transfer> t = l.settlement("g1");
        assertTrue(t.size() <= 3, "6 khoản nợ phải gộp còn ≤ 3 lần chuyển");
        Map<String, Long> net = new LinkedHashMap<>(l.netBalances("g1"));
        for (Transfer x : t) {
            net.put(x.getFromId(), net.get(x.getFromId()) + x.getAmount());
            net.put(x.getToId(), net.get(x.getToId()) - x.getAmount());
        }
        for (long v : net.values()) assertEquals(0, v);
    }

    @Test
    void vongTronTrietTieu() {
        Map<String, Long> net = new LinkedHashMap<>();
        net.put("a", 0L); net.put("b", 0L); net.put("c", 0L);
        assertEquals(0, DebtSimplifier.simplify(net).size());
    }

    @Test
    void tongSoDuKhacKhongBiTuChoi() {
        Map<String, Long> net = new LinkedHashMap<>();
        net.put("a", 10L); net.put("b", -9L);
        assertThrows(IllegalArgumentException.class, () -> DebtSimplifier.simplify(net));
    }

    @Test
    void property_toiGianNoLuonVeKhong() {
        Random r = new Random(99);
        for (int k = 0; k < 5000; k++) {
            int n = 2 + r.nextInt(12);
            Map<String, Long> net = new LinkedHashMap<>();
            long sum = 0;
            for (int i = 0; i < n - 1; i++) {
                long v = r.nextInt(5) == 0 ? 0 : (long) (r.nextInt(2_000_000) - 1_000_000);
                net.put("u" + i, v);
                sum += v;
            }
            net.put("u" + (n - 1), -sum);
            int nonZero = 0;
            for (long v : net.values()) if (v != 0) nonZero++;
            List<Transfer> t = DebtSimplifier.simplify(net);
            assertTrue(t.size() <= Math.max(0, nonZero - 1), "quá nhiều giao dịch ở case " + k);
            Map<String, Long> left = new LinkedHashMap<>(net);
            for (Transfer x : t) {
                assertTrue(x.getAmount() > 0);
                left.put(x.getFromId(), left.get(x.getFromId()) + x.getAmount());
                left.put(x.getToId(), left.get(x.getToId()) - x.getAmount());
            }
            for (long v : left.values()) assertEquals(0, v, "chưa về 0 ở case " + k);
        }
    }

    @Test
    void mayTrangThai() {
        assertTrue(PaymentStateMachine.canApply(DebtStatus.PENDING, Action.MARK_PAID, Role.DEBTOR));
        assertFalse(PaymentStateMachine.canApply(DebtStatus.PENDING, Action.MARK_PAID, Role.CREDITOR));
        assertFalse(PaymentStateMachine.canApply(DebtStatus.MARKED_PAID, Action.CONFIRM, Role.DEBTOR));
        assertTrue(PaymentStateMachine.canApply(DebtStatus.MARKED_PAID, Action.CONFIRM, Role.CREDITOR));
        assertTrue(PaymentStateMachine.canApply(DebtStatus.MARKED_PAID, Action.DISPUTE, Role.CREDITOR));
        assertFalse(PaymentStateMachine.canApply(DebtStatus.PENDING, Action.DISPUTE, Role.CREDITOR));
        assertTrue(PaymentStateMachine.canApply(DebtStatus.PENDING, Action.CONFIRM, Role.CREDITOR));
        assertTrue(PaymentStateMachine.canApply(DebtStatus.DISPUTED, Action.MARK_PAID, Role.DEBTOR));
        assertFalse(PaymentStateMachine.canApply(DebtStatus.CONFIRMED, Action.CONFIRM, Role.CREDITOR));
        assertFalse(PaymentStateMachine.canApply(DebtStatus.PENDING, Action.MARK_PAID, Role.OTHER));
        assertThrows(IllegalStateException.class, () -> PaymentStateMachine.apply(DebtStatus.CONFIRMED, Action.MARK_PAID, Role.DEBTOR));
        assertEquals(Role.DEBTOR, PaymentStateMachine.roleOf("dat", "dat", "minh"));
    }
}
