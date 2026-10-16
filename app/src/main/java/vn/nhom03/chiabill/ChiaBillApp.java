package vn.nhom03.chiabill;

import android.app.Application;
import android.app.ProgressDialog;
import vn.nhom03.chiabill.data.db.AppDatabase;
import com.google.firebase.FirebaseApp;
import vn.nhom03.chiabill.data.repo.AuthRepository;
import vn.nhom03.chiabill.data.repo.FirebaseAuthRepository;
import vn.nhom03.chiabill.data.repo.FirestoreLedgerRepository;
import vn.nhom03.chiabill.data.repo.LedgerRepository;
import vn.nhom03.chiabill.util.NotificationHelper;
import vn.nhom03.chiabill.util.SessionManager;

/** Nơi dựng các đối tượng dùng chung (service locator đơn giản, không cần thư viện DI). */
public class ChiaBillApp extends Application {

    private LedgerRepository repository;
    private AuthRepository authRepository;
    private SessionManager session;
    private NotificationHelper notifications;
    private ProgressDialog loadingDialog;

    @Override
    public void onCreate() {
        super.onCreate();
        session = new SessionManager(this);
        notifications = new NotificationHelper(this);
        if (FirebaseApp.getApps(this).isEmpty() && FirebaseApp.initializeApp(this) == null) {
            throw new IllegalStateException(
                "Thiếu app/google-services.json: tải từ Firebase Console rồi build lại.");
        }
        // Firestore tự đẩy thay đổi về máy (không cần AutoSync 20 giây như bản Render).
        repository = new FirestoreLedgerRepository(
            this,
            AppDatabase.get(this).dao(),
            notifications
        );
        authRepository = new FirebaseAuthRepository();
    }

    public LedgerRepository repository() {
        return repository;
    }

    public AuthRepository authRepository() {
        return authRepository;
    }

    public SessionManager session() {
        return session;
    }

    public NotificationHelper notifications() {
        return notifications;
    }

    public void showLoading(android.app.Activity activity) {
        if (loadingDialog == null || !loadingDialog.isShowing()) {
            loadingDialog = new ProgressDialog(activity);
            loadingDialog.setMessage("Đang kết nối…");
            loadingDialog.setCancelable(false);
            loadingDialog.show();
        }
    }

    public void hideLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }
}
