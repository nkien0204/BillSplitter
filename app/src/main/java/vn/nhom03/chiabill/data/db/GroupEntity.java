package vn.nhom03.chiabill.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "bill_groups", indices = {@Index(value = "inviteCode", unique = true)})
public class GroupEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String name = "";
    /** Mã mời 6 ký tự, không trùng. Người có mã tự vào nhóm được (FR3). */
    public String inviteCode;
    public String createdBy;
    public long createdAt;
}
