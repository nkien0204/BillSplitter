package vn.nhom03.chiabill.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Hộp thư của từng tài khoản: bản ghi của mọi thông báo gửi tới tài khoản đó. */
@Entity(tableName = "inbox", indices = {@Index("recipientId")})
public class InboxEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String recipientId = "";
    @NonNull public String message = "";
    public String billId;
    public String debtKey;
    public long createdAt;
}
