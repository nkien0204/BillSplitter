package vn.nhom03.chiabill.data.repo;

import android.util.Log;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.HashMap;
import java.util.Map;
import vn.nhom03.chiabill.data.network.AuthApi.UserAuthResponse;
import vn.nhom03.chiabill.domain.user.PhoneNumber;

/**
 * Đăng nhập bằng Firebase Auth, giữ nguyên màn số điện thoại + mật khẩu.
 *
 * <p>Firebase Auth có đăng nhập bằng SMS, nhưng gửi SMS cần gói trả phí và số thật. Nên mỗi số điện
 * thoại được đổi thành một email giả {@code 0912345678@chiabill.app} rồi dùng Email/Password.
 * Firebase giữ email không trùng, nên một số điện thoại chỉ đăng ký được một lần.
 *
 * <p>Mã lỗi trả về giống máy chủ cũ (USER_ALREADY_EXISTS, INVALID_CREDENTIALS...) để
 * MessageMapper và màn đăng nhập không phải đổi.
 */
public class FirebaseAuthRepository implements AuthRepository {

    private static final String TAG = "FirebaseAuth";
    static final String EMAIL_DOMAIN = "@chiabill.app";
    /** SessionManager cần một "token" khác null để biết đã đăng nhập; Firebase tự giữ phiên thật. */
    static final String SESSION_TOKEN = "firebase";

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    static String emailOf(String phone) {
        return phone + EMAIL_DOMAIN;
    }

    @Override
    public void register(String name, String rawPhone, String password, AuthCallback<UserAuthResponse> callback) {
        String phone = PhoneNumber.normalize(rawPhone);
        String n = name == null ? "" : name.trim();
        if (phone == null || n.isEmpty()) {
            callback.onError("BAD_REQUEST");
            return;
        }
        auth.createUserWithEmailAndPassword(emailOf(phone), password)
            .addOnSuccessListener(result -> {
                String uid = result.getUser().getUid();
                Map<String, Object> user = new HashMap<>();
                user.put("name", n);
                user.put("phone", phone);
                user.put("colorIndex", Math.abs(uid.hashCode()) % LocalLedgerRepository.PALETTE_SIZE);
                user.put("createdAt", System.currentTimeMillis());
                Map<String, Object> phoneDoc = new HashMap<>();
                phoneDoc.put("uid", uid);
                WriteBatch batch = db.batch();
                batch.set(db.collection("users").document(uid), user);
                batch.set(db.collection("phones").document(phone), phoneDoc);
                batch.commit()
                    .addOnSuccessListener(v -> {
                        saveFcmToken(uid);
                        callback.onSuccess(response(uid, n, phone));
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Không ghi được hồ sơ người dùng", e);
                        callback.onError(code(e));
                    });
            })
            .addOnFailureListener(e -> callback.onError(code(e)));
    }

    @Override
    public void login(String rawPhone, String password, AuthCallback<UserAuthResponse> callback) {
        String phone = PhoneNumber.normalize(rawPhone);
        if (phone == null) {
            callback.onError("INVALID_CREDENTIALS");
            return;
        }
        // LUỒNG 1 · Đăng nhập · Bước 2/5: Số điện thoại → email giả 09xx@chiabill.app → Firebase Auth kiểm mật khẩu, rồi đọc tên ở users/{uid}.
        //   (Đăng ký: register ghi thêm users/{uid} và phones/{số} trong một lô.)
        //   → quay lại LoginActivity, onSuccess
        auth.signInWithEmailAndPassword(emailOf(phone), password)
            .addOnSuccessListener(result -> {
                String uid = result.getUser().getUid();
                db.collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        saveFcmToken(uid);
                        callback.onSuccess(response(uid, nameOr(doc, phone), phone));
                    })
                    .addOnFailureListener(e -> callback.onError(code(e)));
            })
            .addOnFailureListener(e -> callback.onError(code(e)));
    }

    /** Hồ sơ bị thiếu (đăng ký dở dang lúc mất mạng): dùng số điện thoại làm tên tạm và ghi lại hồ sơ. */
    private String nameOr(DocumentSnapshot doc, String phone) {
        String name = doc.exists() ? doc.getString("name") : null;
        if (name != null && !name.isEmpty()) return name;
        Map<String, Object> user = new HashMap<>();
        user.put("name", phone);
        user.put("phone", phone);
        user.put("createdAt", System.currentTimeMillis());
        Map<String, Object> phoneDoc = new HashMap<>();
        phoneDoc.put("uid", doc.getId());
        WriteBatch batch = db.batch();
        batch.set(doc.getReference(), user, com.google.firebase.firestore.SetOptions.merge());
        batch.set(db.collection("phones").document(phone), phoneDoc);
        batch.commit();
        return phone;
    }

    @Override
    public void logout(AuthCallback<Void> callback) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            callback.onSuccess(null);
            return;
        }
        // Gỡ mã thông báo của máy này để người đăng nhập sau không nhận tin của tài khoản cũ.
        Runnable finish = () -> {
            auth.signOut();
            callback.onSuccess(null);
        };
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(t -> {
            if (!t.isSuccessful()) {
                finish.run();
                return;
            }
            db.collection("users").document(u.getUid())
                .update("fcmTokens", FieldValue.arrayRemove(t.getResult()))
                .addOnCompleteListener(x -> finish.run());
        });
    }

    @Override
    public void me(AuthCallback<UserAuthResponse.UserInfo> callback) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            callback.onError("INVALID_TOKEN");
            return;
        }
        // reload() hỏi Firebase tài khoản còn không (bị xoá/khoá thì báo INVALID_TOKEN).
        u.reload()
            .addOnSuccessListener(v -> {
                UserAuthResponse.UserInfo info = new UserAuthResponse.UserInfo();
                info.id = u.getUid();
                String email = u.getEmail();
                info.phone = email == null ? null : email.replace(EMAIL_DOMAIN, "");
                callback.onSuccess(info);
            })
            .addOnFailureListener(e -> callback.onError(
                e instanceof FirebaseAuthInvalidUserException ? "INVALID_TOKEN" : code(e)));
    }

    /** Ghi mã FCM của máy này vào hồ sơ, để Cloud Function gửi được thông báo khi app đang tắt. */
    static void saveFcmToken(String uid) {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token ->
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("fcmTokens", FieldValue.arrayUnion(token)));
    }

    private static UserAuthResponse response(String uid, String name, String phone) {
        UserAuthResponse r = new UserAuthResponse();
        r.token = SESSION_TOKEN;
        r.user = new UserAuthResponse.UserInfo();
        r.user.id = uid;
        r.user.name = name;
        r.user.phone = phone;
        return r;
    }

    /** Đổi lỗi Firebase thành mã lỗi MessageMapper đã biết. */
    private static String code(Exception e) {
        Log.w(TAG, "Firebase Auth lỗi", e);
        if (e instanceof FirebaseAuthUserCollisionException) return "USER_ALREADY_EXISTS";
        if (e instanceof FirebaseAuthWeakPasswordException) return "WEAK_PASSWORD";
        if (e instanceof FirebaseAuthInvalidCredentialsException) return "INVALID_CREDENTIALS";
        if (e instanceof FirebaseAuthInvalidUserException) return "INVALID_CREDENTIALS";
        if (e instanceof FirebaseNetworkException) return "NETWORK_ERROR";
        return "INTERNAL_SERVER_ERROR";
    }
}
