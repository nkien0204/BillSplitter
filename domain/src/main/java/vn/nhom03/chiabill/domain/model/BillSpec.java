package vn.nhom03.chiabill.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mọi thứ SplitEngine cần để chia một hoá đơn. Bất biến (immutable).
 * vatPercent, servicePercent tính theo phần trăm nguyên; rounding là 1 hoặc 1000.
 * shares chỉ dùng cho CUSTOM (số tiền VND mỗi người) và PERCENT (phần vạn, 1% = 100).
 */
public final class BillSpec {
    private final String payerId;
    private final SplitMode mode;
    private final long subtotal;
    private final List<String> participants;
    private final List<BillItem> items;
    private final int vatPercent;
    private final int servicePercent;
    private final long rounding;
    private final Map<String, Long> shares;

    public BillSpec(String payerId, SplitMode mode, long subtotal, List<String> participants,
                    List<BillItem> items, int vatPercent, int servicePercent, long rounding) {
        this(payerId, mode, subtotal, participants, items, vatPercent, servicePercent, rounding, null);
    }

    public BillSpec(String payerId, SplitMode mode, long subtotal, List<String> participants,
                    List<BillItem> items, int vatPercent, int servicePercent, long rounding,
                    Map<String, Long> shares) {
        this.payerId = Objects.requireNonNull(payerId);
        this.mode = Objects.requireNonNull(mode);
        if (subtotal < 0) throw new IllegalArgumentException("subtotal < 0");
        if (vatPercent < 0 || vatPercent > 100 || servicePercent < 0 || servicePercent > 100)
            throw new IllegalArgumentException("percent out of range");
        if (rounding < 1) throw new IllegalArgumentException("rounding < 1");
        this.subtotal = subtotal;
        this.participants = Collections.unmodifiableList(new ArrayList<>(participants == null ? Collections.<String>emptyList() : participants));
        this.items = Collections.unmodifiableList(new ArrayList<>(items == null ? Collections.<BillItem>emptyList() : items));
        this.vatPercent = vatPercent;
        this.servicePercent = servicePercent;
        this.rounding = rounding;
        Map<String, Long> sh = new LinkedHashMap<>();
        if (shares != null) {
            for (Map.Entry<String, Long> e : shares.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                if (e.getValue() < 0) throw new IllegalArgumentException("share < 0");
                if (e.getValue() > 0) sh.put(e.getKey(), e.getValue());
            }
        }
        this.shares = Collections.unmodifiableMap(sh);
    }

    public String getPayerId() { return payerId; }
    public SplitMode getMode() { return mode; }
    public long getSubtotal() { return subtotal; }
    public List<String> getParticipants() { return participants; }
    public List<BillItem> getItems() { return items; }
    public int getVatPercent() { return vatPercent; }
    public int getServicePercent() { return servicePercent; }
    public long getRounding() { return rounding; }
    /** Chỉ chứa các giá trị &gt; 0. */
    public Map<String, Long> getShares() { return shares; }
}
