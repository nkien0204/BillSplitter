package vn.nhom03.chiabill.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Hoá đơn. Phần từng người không lưu mà tính lại bằng SplitEngine (tất định). */
@Entity(tableName = "bills", indices = {@Index("groupId")})
public class BillEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String groupId = "";
    @NonNull public String title = "";
    @NonNull public String payerId = "";
    /** EQUAL | ITEMIZED */
    @NonNull public String mode = "EQUAL";
    public long subtotal;
    /** Người tham gia khi chia đều, id ngăn cách bằng dấu phẩy. */
    @NonNull public String participantsCsv = "";
    public int vatPercent;
    public int servicePercent;
    public long rounding = 1000;
    /** Tên hằng của domain.model.Category. */
    @NonNull public String category = "KHAC";
    /** CUSTOM: "id:số tiền,…"; PERCENT: "id:phần vạn,…". Rỗng với EQUAL và ITEMIZED. */
    @NonNull public String sharesCsv = "";
    public String createdBy;
    public long createdAt;
    public long updatedAt;
}
