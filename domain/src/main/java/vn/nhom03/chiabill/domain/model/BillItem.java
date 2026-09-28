package vn.nhom03.chiabill.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Một món trên hoá đơn. price là tiền của cả dòng (đã nhân số lượng). */
public final class BillItem {
    private final String name;
    private final long price;
    private final List<String> consumers;

    public BillItem(String name, long price, List<String> consumers) {
        if (price < 0) throw new IllegalArgumentException("price < 0");
        this.name = name == null ? "" : name;
        this.price = price;
        this.consumers = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(consumers)));
    }

    public String getName() { return name; }
    public long getPrice() { return price; }
    public List<String> getConsumers() { return consumers; }
}
