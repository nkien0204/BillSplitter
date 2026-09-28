package vn.nhom03.chiabill.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class AppDao {
    // ---- đọc (LiveData: Room chạy query ngoài main thread và phát lại khi bảng đổi) ----
    @Query("SELECT * FROM users ORDER BY createdAt, name")
    public abstract LiveData<List<UserEntity>> users();

    @Query("SELECT * FROM bill_groups ORDER BY createdAt DESC")
    public abstract LiveData<List<GroupEntity>> groups();

    @Query("SELECT * FROM members ORDER BY groupId, position")
    public abstract LiveData<List<MemberEntity>> members();

    @Query("SELECT * FROM bills ORDER BY createdAt")
    public abstract LiveData<List<BillEntity>> bills();

    @Query("SELECT * FROM bill_items ORDER BY billId, position")
    public abstract LiveData<List<BillItemEntity>> billItems();

    @Query("SELECT * FROM debt_status")
    public abstract LiveData<List<DebtStatusEntity>> debtStatuses();

    @Query("SELECT * FROM inbox ORDER BY createdAt DESC LIMIT 200")
    public abstract LiveData<List<InboxEntity>> inbox();

    // ---- đọc đồng bộ (chỉ gọi trên luồng nền) ----
    @Query("SELECT COUNT(*) FROM users")
    public abstract int userCount();

    @Query("SELECT * FROM users WHERE id = :id")
    public abstract UserEntity userNow(String id);

    /** Tìm đúng một tài khoản theo số đã chuẩn hoá. Không có truy vấn liệt kê hay tìm gần đúng. */
    @Query("SELECT * FROM users WHERE phone = :phone AND guest = 0 LIMIT 1")
    public abstract UserEntity userByPhoneNow(String phone);

    @Query("SELECT COALESCE(MAX(position), -1) FROM members WHERE groupId = :groupId")
    public abstract int maxPositionNow(String groupId);

    @Query("SELECT * FROM bill_groups WHERE id = :id")
    public abstract GroupEntity groupNow(String id);

    @Query("SELECT * FROM bill_groups WHERE inviteCode = :code LIMIT 1")
    public abstract GroupEntity groupByInviteNow(String code);

    @Query("SELECT * FROM members WHERE groupId = :groupId AND userId = :userId")
    public abstract MemberEntity memberNow(String groupId, String userId);

    @Query("UPDATE members SET active = :active WHERE groupId = :groupId AND userId = :userId")
    public abstract void setMemberActive(String groupId, String userId, boolean active);

    @Query("SELECT * FROM bills WHERE groupId = :groupId")
    public abstract List<BillEntity> billsOfGroupNow(String groupId);

    @Query("SELECT * FROM debt_status WHERE billId IN (SELECT id FROM bills WHERE groupId = :groupId)")
    public abstract List<DebtStatusEntity> statusesOfGroupNow(String groupId);

    @Query("SELECT * FROM bills WHERE id = :id")
    public abstract BillEntity billNow(String id);

    @Query("SELECT * FROM bill_items WHERE billId = :billId ORDER BY position")
    public abstract List<BillItemEntity> itemsNow(String billId);

    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY position")
    public abstract List<MemberEntity> membersNow(String groupId);

    @Query("SELECT * FROM debt_status WHERE debtKey = :key")
    public abstract DebtStatusEntity statusNow(String key);

    @Query("SELECT * FROM debt_status WHERE billId = :billId")
    public abstract List<DebtStatusEntity> statusesOfBillNow(String billId);

    // ---- ghi ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void upsertUser(UserEntity u);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void upsertGroup(GroupEntity g);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void upsertMembers(List<MemberEntity> m);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void upsertBill(BillEntity b);

    @Insert
    public abstract void insertItems(List<BillItemEntity> items);

    @Query("DELETE FROM bill_items WHERE billId = :billId")
    public abstract void deleteItems(String billId);

    @Query("DELETE FROM bills WHERE id = :billId")
    public abstract void deleteBillRow(String billId);

    @Query("DELETE FROM debt_status WHERE billId = :billId")
    public abstract void deleteStatusesOfBill(String billId);

    @Query("DELETE FROM debt_status WHERE debtKey = :key")
    public abstract void deleteStatus(String key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void upsertStatus(DebtStatusEntity s);

    @Insert
    public abstract void insertInbox(InboxEntity e);

    @Transaction
    public void replaceBill(BillEntity bill, List<BillItemEntity> items) {
        upsertBill(bill);
        deleteItems(bill.id);
        if (!items.isEmpty()) insertItems(items);
    }

    @Transaction
    public void deleteBill(String billId) {
        deleteItems(billId);
        deleteStatusesOfBill(billId);
        deleteBillRow(billId);
    }

    @Query("DELETE FROM users") public abstract void clearUsers();
    @Query("DELETE FROM bill_groups") public abstract void clearGroups();
    @Query("DELETE FROM members") public abstract void clearMembers();
    @Query("DELETE FROM bills") public abstract void clearBills();
    @Query("DELETE FROM bill_items") public abstract void clearItems();
    @Query("DELETE FROM debt_status") public abstract void clearStatuses();
    @Query("DELETE FROM inbox") public abstract void clearInbox();

    @Transaction
    public void clearAll() {
        clearInbox();
        clearStatuses();
        clearItems();
        clearBills();
        clearMembers();
        clearGroups();
        clearUsers();
    }
}
