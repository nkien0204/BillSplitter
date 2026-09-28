package vn.nhom03.chiabill;

import android.app.Application;

import vn.nhom03.chiabill.data.db.AppDatabase;
import vn.nhom03.chiabill.data.repo.LedgerRepository;
import vn.nhom03.chiabill.data.repo.LocalLedgerRepository;
import vn.nhom03.chiabill.util.NotificationHelper;
import vn.nhom03.chiabill.util.SessionManager;

/** Nơi dựng các đối tượng dùng chung (service locator đơn giản, không cần thư viện DI). */
public class ChiaBillApp extends Application {
    private LedgerRepository repository;
    private SessionManager session;
    private NotificationHelper notifications;

    @Override
    public void onCreate() {
        super.onCreate();
        session = new SessionManager(this);
        notifications = new NotificationHelper(this);
        repository = new LocalLedgerRepository(AppDatabase.get(this).dao(), notifications);
    }

    public LedgerRepository repository() { return repository; }

    public SessionManager session() { return session; }

    public NotificationHelper notifications() { return notifications; }
}
