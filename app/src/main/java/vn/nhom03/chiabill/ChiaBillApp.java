package vn.nhom03.chiabill;

import android.app.Application;
import android.app.ProgressDialog;
import vn.nhom03.chiabill.data.db.AppDatabase;
import vn.nhom03.chiabill.data.repo.AuthRepository;
import vn.nhom03.chiabill.data.repo.LedgerRepository;
import vn.nhom03.chiabill.data.repo.LocalLedgerRepository;
import vn.nhom03.chiabill.data.repo.RemoteAuthRepository;
import vn.nhom03.chiabill.data.repo.RemoteLedgerRepository;
import vn.nhom03.chiabill.data.remote.ApiClient;
import vn.nhom03.chiabill.util.AutoSync;
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
        authRepository = new RemoteAuthRepository(this);
        registerActivityLifecycleCallbacks(new AutoSync(repository, session));
        wakeServer();
    }

    /**
     * Máy chủ miễn phí (Render) ngủ khi lâu không ai gọi và mất khoảng nửa phút để thức dậy.
     * Gọi /health ngay lúc mở app để nó thức trong lúc người dùng còn đang nhìn màn hình đầu.
     */
    private void wakeServer() {
        ApiClient.getInstance(this).health().enqueue(new retrofit2.Callback<Void>() {
            @Override public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {}
            @Override public void onFailure(retrofit2.Call<Void> call, Throwable t) {}
        });
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
            loadingDialog.setMessage("Đang kết nối máy chủ…");
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
