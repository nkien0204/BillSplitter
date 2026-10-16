package vn.nhom03.chiabill.util;

import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Thông báo đẩy (FCM). Khi app đang tắt, Android tự hiện thông báo Cloud Function gửi tới
 * (functions/index.js). Khi app đang mở, FirestoreLedgerRepository đã nghe hộp thư và tự bật
 * thông báo, nên ở đây không làm gì để khỏi báo hai lần.
 */
public class ChiaBillMessagingService extends FirebaseMessagingService {

    /** Mã của máy đổi (cài lại app, xoá dữ liệu): ghi mã mới vào hồ sơ để vẫn nhận được tin. */
    @Override
    public void onNewToken(@NonNull String token) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;
        FirebaseFirestore.getInstance().collection("users").document(u.getUid())
            .update("fcmTokens", FieldValue.arrayUnion(token));
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        // app đang mở: hộp thư Firestore đã lo
    }
}
