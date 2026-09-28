package vn.nhom03.chiabill.ui.bill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import vn.nhom03.chiabill.domain.model.Bill;
import vn.nhom03.chiabill.domain.model.BillItem;
import vn.nhom03.chiabill.domain.model.BillSpec;
import vn.nhom03.chiabill.domain.model.Category;
import vn.nhom03.chiabill.domain.model.SplitMode;

/** Trạng thái form hoá đơn đang sửa (có thể đổi), sống trong ViewModel để giữ khi xoay màn hình. */
public final class BillDraft {

    public static final class Item {
        public String name = "";
        public long price;
        public final Set<String> who = new LinkedHashSet<>();
    }

    public String id;          // null = hoá đơn mới
    public String groupId;
    public String title = "";
    public String payerId;
    public SplitMode mode = SplitMode.EQUAL;
    public long subtotal;
    public final Set<String> participants = new LinkedHashSet<>();
    public final List<Item> items = new ArrayList<>();
    public int vat;
    public int service;
    public long rounding = 1000;
    public Category category = Category.AN_UONG;
    /** Ngày chi, epoch ms. */
    public long date = System.currentTimeMillis();
    /** Số tiền từng người ở chế độ CUSTOM (VND). */
    public final Map<String, Long> custom = new LinkedHashMap<>();
    /** Phần trăm từng người ở chế độ PERCENT, theo phần vạn (1% = 100). */
    public final Map<String, Long> percent = new LinkedHashMap<>();

    public static BillDraft create(String groupId, String payerId, List<String> members) {
        BillDraft d = new BillDraft();
        d.groupId = groupId;
        d.payerId = payerId;
        d.participants.addAll(members);
        d.items.add(new Item());
        return d;
    }

    public static BillDraft from(Bill bill) {
        BillDraft d = new BillDraft();
        BillSpec s = bill.getSpec();
        d.id = bill.getId();
        d.groupId = bill.getGroupId();
        d.title = bill.getTitle();
        d.payerId = s.getPayerId();
        d.mode = s.getMode();
        d.subtotal = s.getSubtotal();
        d.participants.addAll(s.getParticipants());
        for (BillItem it : s.getItems()) {
            Item i = new Item();
            i.name = it.getName();
            i.price = it.getPrice();
            i.who.addAll(it.getConsumers());
            d.items.add(i);
        }
        if (d.items.isEmpty()) d.items.add(new Item());
        d.vat = s.getVatPercent();
        d.service = s.getServicePercent();
        d.rounding = s.getRounding();
        d.category = bill.getCategory();
        d.date = bill.getCreatedAt();
        if (d.mode == SplitMode.CUSTOM) d.custom.putAll(s.getShares());
        if (d.mode == SplitMode.PERCENT) d.percent.putAll(s.getShares());
        return d;
    }

    public BillSpec toSpec() {
        List<BillItem> list = new ArrayList<>();
        for (Item i : items) list.add(new BillItem(i.name, i.price, new ArrayList<>(i.who)));
        Map<String, Long> shares = mode == SplitMode.CUSTOM ? custom : mode == SplitMode.PERCENT ? percent : null;
        long sub = subtotal;
        if (mode == SplitMode.CUSTOM) {
            sub = 0;
            for (Long v : custom.values()) if (v != null) sub += v;
        }
        return new BillSpec(payerId, mode, sub, new ArrayList<>(participants), list, vat, service, rounding, shares);
    }
}
