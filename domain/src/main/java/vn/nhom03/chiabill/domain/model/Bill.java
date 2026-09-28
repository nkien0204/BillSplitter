package vn.nhom03.chiabill.domain.model;

import java.util.Objects;

/** Hoá đơn đã lưu: định danh + nội dung chia. */
public final class Bill {
    private final String id;
    private final String groupId;
    private final String title;
    private final long createdAt;
    private final String createdBy;
    private final BillSpec spec;
    private final Category category;

    public Bill(String id, String groupId, String title, long createdAt, String createdBy, BillSpec spec) {
        this(id, groupId, title, createdAt, createdBy, spec, Category.KHAC);
    }

    /** createdAt là ngày chi (người dùng chọn được), không phải lúc bấm lưu. */
    public Bill(String id, String groupId, String title, long createdAt, String createdBy, BillSpec spec, Category category) {
        this.id = Objects.requireNonNull(id);
        this.groupId = Objects.requireNonNull(groupId);
        this.title = title == null ? "" : title;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.spec = Objects.requireNonNull(spec);
        this.category = category == null ? Category.KHAC : category;
    }

    public String getId() { return id; }
    public String getGroupId() { return groupId; }
    public String getTitle() { return title; }
    public long getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public BillSpec getSpec() { return spec; }
    public String getPayerId() { return spec.getPayerId(); }
    public Category getCategory() { return category; }
}
