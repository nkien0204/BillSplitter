package vn.nhom03.chiabill.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Tài khoản (hoặc thành viên khách). QR nhận tiền lưu dạng BIN + số tài khoản, ảnh QR sinh lại khi cần. */
@Entity(tableName = "users", indices = {@Index(value = "phone", unique = true)})
public class UserEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String name = "";
    public int colorIndex;
    /** Khách: có tên, chưa có tài khoản, không đăng nhập được. */
    public boolean guest;
    /** Số điện thoại đã chuẩn hoá (0912345678). Là khoá để người dùng tìm thấy nhau. Khách: null. */
    public String phone;
    public String bankBin;
    public String accountNo;
    public String accountName;
    /** Lúc đổi QR gần nhất, để cảnh báo "vừa đổi QR" (chống tráo QR). 0 = chưa khai. */
    public long qrUpdatedAt;
    public long createdAt;

    public boolean hasPaymentProfile() {
        return bankBin != null && accountNo != null && !accountNo.isEmpty();
    }
}
