package vn.nhom03.chiabill.util;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import vn.nhom03.chiabill.data.repo.LedgerRepository;

/**
 * Tự lấy dữ liệu mới từ máy chủ khi app đang mở: ngay lúc mở app và sau đó mỗi
 * {@link #INTERVAL_MS}. App vào nền thì dừng, để không tốn pin và lượt gọi máy chủ.
 * Nhờ vậy hoá đơn hay "đã chuyển" từ máy khác hiện ra mà không cần thao tác gì.
 */
public final class AutoSync implements Application.ActivityLifecycleCallbacks {
    public static final long INTERVAL_MS = 20_000;

    private final LedgerRepository repo;
    private final SessionManager session;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int started;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (session.authToken() != null) repo.syncAll(null, null);
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    public AutoSync(LedgerRepository repo, SessionManager session) {
        this.repo = repo;
        this.session = session;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (started++ == 0) {
            handler.removeCallbacks(tick);
            handler.post(tick);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (started > 0 && --started == 0) handler.removeCallbacks(tick);
    }

    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityResumed(Activity a) {}
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {}
}
