package vn.nhom03.chiabill.domain.model;

import java.util.Collections;
import java.util.List;

public final class SplitResult {
    private final List<ShareLine> lines;
    private final long subtotal;
    private final long vat;
    private final long service;
    private final long total;
    private final List<String> unassignedItems;

    public SplitResult(List<ShareLine> lines, long subtotal, long vat, long service, long total, List<String> unassignedItems) {
        this.lines = Collections.unmodifiableList(lines);
        this.subtotal = subtotal;
        this.vat = vat;
        this.service = service;
        this.total = total;
        this.unassignedItems = Collections.unmodifiableList(unassignedItems);
    }

    public List<ShareLine> getLines() { return lines; }
    public long getSubtotal() { return subtotal; }
    public long getVat() { return vat; }
    public long getService() { return service; }
    public long getTotal() { return total; }
    /** Tên các món có giá nhưng chưa gán cho ai (chỉ ở chế độ ITEMIZED). */
    public List<String> getUnassignedItems() { return unassignedItems; }

    public long sumOfShares() {
        long s = 0;
        for (ShareLine l : lines) s += l.getAmount();
        return s;
    }

    /** Bất biến chính: tổng các phần bằng đúng tổng hoá đơn. */
    public boolean isBalanced() {
        return sumOfShares() == total;
    }

    /** Lưu được khi có tiền, không còn món chưa gán, và tổng khớp. */
    public boolean isSavable() {
        return total > 0 && unassignedItems.isEmpty() && isBalanced();
    }

    public ShareLine lineOf(String memberId) {
        for (ShareLine l : lines) if (l.getMemberId().equals(memberId)) return l;
        return null;
    }
}
