package vn.nhom03.chiabill.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "bill_items", indices = {@Index("billId")})
public class BillItemEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String billId = "";
    public int position;
    @NonNull public String name = "";
    public long price;
    /** Người dùng món, id ngăn cách bằng dấu phẩy. */
    @NonNull public String consumersCsv = "";
}
