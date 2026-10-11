package vn.nhom03.chiabill.ui.tabs;

import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import vn.nhom03.chiabill.ChiaBillApp;
import vn.nhom03.chiabill.data.repo.LedgerRepository;

public abstract class BaseFragment extends Fragment {
    protected ChiaBillApp app() {
        return (ChiaBillApp) requireActivity().getApplication();
    }

    protected LedgerRepository repo() {
        return app().repository();
    }

    protected String me() {
        return app().session().currentUserId();
    }

    /** Kéo xuống để đồng bộ ngay với máy chủ; vòng xoay tắt khi xong hoặc lỗi. */
    protected void bindRefresh(SwipeRefreshLayout refresh) {
        refresh.setColorSchemeResources(vn.nhom03.chiabill.R.color.brand);
        refresh.setOnRefreshListener(() -> repo().syncAll(
                () -> refresh.setRefreshing(false),
                err -> {
                    refresh.setRefreshing(false);
                    toast(err);
                }));
    }

    protected void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
    }
}
