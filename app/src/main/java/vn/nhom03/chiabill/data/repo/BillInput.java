package vn.nhom03.chiabill.data.repo;

import vn.nhom03.chiabill.domain.model.BillSpec;
import vn.nhom03.chiabill.domain.model.Category;

/** Hoá đơn người dùng muốn lưu. id = null nghĩa là tạo mới. */
public final class BillInput {
    public final String id;
    public final String groupId;
    public final String title;
    public final BillSpec spec;
    public final Category category;
    /** Ngày chi (epoch ms). 0 = hôm nay. */
    public final long date;

    public BillInput(String id, String groupId, String title, BillSpec spec, Category category, long date) {
        this.id = id;
        this.groupId = groupId;
        this.title = title;
        this.spec = spec;
        this.category = category == null ? Category.KHAC : category;
        this.date = date;
    }
}
