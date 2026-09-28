package vn.nhom03.chiabill.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;

@Entity(tableName = "members", primaryKeys = {"groupId", "userId"}, indices = {@Index("userId")})
public class MemberEntity {
    @NonNull public String groupId = "";
    @NonNull public String userId = "";
    /** Thứ tự trong nhóm: quyết định thứ tự dòng và thứ tự phát phần dư khi chia. */
    public int position;
    /**
     * false = đã rời nhóm hoặc bị xoá. Dòng vẫn giữ để hoá đơn cũ chia ra đúng như trước
     * (thứ tự thành viên quyết định phần dư), chỉ ẩn khỏi giao diện và hoá đơn mới.
     */
    public boolean active = true;

    public MemberEntity() {}

    @Ignore
    public MemberEntity(@NonNull String groupId, @NonNull String userId, int position) {
        this.groupId = groupId;
        this.userId = userId;
        this.position = position;
    }
}
