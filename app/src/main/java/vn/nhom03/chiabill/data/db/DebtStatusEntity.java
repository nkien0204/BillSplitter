package vn.nhom03.chiabill.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Trạng thái khoản nợ. Khoá tất định billId:debtorId nên ghi lặp không sinh bản trùng. */
@Entity(tableName = "debt_status", indices = {@Index("billId")})
public class DebtStatusEntity {
    @PrimaryKey @NonNull public String debtKey = "";
    @NonNull public String billId = "";
    @NonNull public String status = "PENDING";
    public long updatedAt;
}
