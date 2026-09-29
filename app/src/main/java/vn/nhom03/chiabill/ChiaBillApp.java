package vn.nhom03.chiabill;

import android.app.Application;
import android.app.ProgressDialog;
import vn.nhom03.chiabill.data.db.AppDatabase;
import vn.nhom03.chiabill.data.repo.AuthRepository;
import vn.nhom03.chiabill.data.repo.LedgerRepository;
import vn.nhom03.chiabill.data.repo.LocalLedgerRepository;
import vn.nhom03.chiabill.data.repo.RemoteAuthRepository;
import vn.nhom03.chiabill.data.repo.RemoteLedgerRepository;
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
        repository = new RemoteLedgerRepository(
            this,
            AppDatabase.get(this).dao(),
            notifications
        );
        authRepository = new RemoteAuthRepository();
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
            loadingDialog.setMessage("Đang xử lý...");
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
