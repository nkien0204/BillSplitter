package vn.nhom03.chiabill.data.repo;

import androidx.lifecycle.LiveData;
import java.util.List;
import vn.nhom03.chiabill.data.db.UserEntity;
import vn.nhom03.chiabill.domain.ledger.PaymentStateMachine;
import vn.nhom03.chiabill.domain.qr.PaymentTarget;

/**
 * Cổng dữ liệu duy nhất của UI. Bản hiện tại là {@link LocalLedgerRepository} (Room, một máy, nhiều tài khoản demo).
 * Bản đồng bộ nhiều máy (Firebase) hiện thực cùng interface này, UI không phải đổi.
 */
public interface LedgerRepository {
    interface Callback<T> {
        void onResult(T value);
    }

    /** Toàn bộ dữ liệu, phát lại mỗi khi có thay đổi. */
    LiveData<AppSnapshot> snapshot();

    void ensureSeeded(Runnable done);

    void resetDemo(Runnable done);

    /** Save user to local cache immediately (synchronous). */
    void saveUserNow(UserEntity u);

    /** Tạo tài khoản. Số điện thoại phải hợp lệ và chưa ai dùng; lỗi → onError. */
    void createAccount(
        String name,
        String phone,
        Callback<String> onId,
        Callback<String> onError
    );

    /**
     * Tìm tài khoản theo đúng số điện thoại (chuẩn hoá trước khi so). Không tìm gần đúng, không liệt kê,
     * để không ai dò được danh bạ người dùng. Không thấy → onResult(null). Số sai định dạng → onError.
     */
    void findUserByPhone(
        String rawPhone,
        Callback<UserEntity> onResult,
        Callback<String> onError
    );

    /** Thêm một tài khoản vào nhóm. Chỉ thành viên nhóm được thêm người; người được thêm nhận thông báo. */
    void addMember(
        String groupId,
        String userId,
        String actorId,
        Runnable done,
        Callback<String> onError
    );

    /** Vào nhóm bằng mã mời (FR3). Trả id nhóm. */
    void joinByInviteCode(
        String rawCode,
        String userId,
        Callback<String> onGroupId,
        Callback<String> onError
    );

    /** Người tạo nhóm xoá một thành viên (FR4). Không xoá được khi người đó còn khoản chưa xong trong nhóm. */
    void removeMember(
        String groupId,
        String userId,
        String actorId,
        Runnable done,
        Callback<String> onError
    );

    /** Rời nhóm (FR14). Không rời được khi còn nợ hoặc còn được nợ trong nhóm. */
    void leaveGroup(
        String groupId,
        String userId,
        Runnable done,
        Callback<String> onError
    );

    /**
     * Xoá nhóm cùng toàn bộ hoá đơn và khoản nợ. Chỉ người tạo nhóm được xoá, và chỉ khi mọi
     * khoản trong nhóm đã xác nhận xong. Các thành viên còn lại nhận thông báo.
     */
    void deleteGroup(
        String groupId,
        String actorId,
        Runnable done,
        Callback<String> onError
    );

    /** Synchronize group data from remote server to local cache. */
    void syncGroup(String groupId, Runnable done, Callback<String> onError);

    /** Đổi tên hiển thị (FR13). */
    void renameUser(
        String userId,
        String name,
        Runnable done,
        Callback<String> onError
    );

    void setPaymentTarget(String userId, PaymentTarget target, Runnable done);

    void clearPaymentTarget(String userId, Runnable done);

    void createGroup(
        String name,
        String creatorId,
        List<String> memberIds,
        List<String> guestNames,
        Callback<String> onId
    );

    /** Lưu hoá đơn (mới hoặc sửa). Chỉ người tạo hoặc người trả được sửa. Trả id; lỗi → onError. */
    void saveBill(
        BillInput input,
        String actorId,
        Callback<String> onId,
        Callback<String> onError
    );

    void deleteBill(
        String billId,
        String actorId,
        Runnable done,
        Callback<String> onError
    );

    /** Áp dụng một bước của máy trạng thái thanh toán; kiểm quyền theo vai trò của actor. */
    void applyDebtAction(
        String debtKey,
        PaymentStateMachine.Action action,
        String actorId,
        Runnable done,
        Callback<String> onError
    );

    /** Gửi nhắc (không đổi trạng thái). */
    void sendReminder(
        String recipientId,
        String fromId,
        String message,
        String billId,
        String debtKey
    );
}
