package vn.nhom03.chiabill.ui.tabs;

import android.widget.Toast;

import androidx.fragment.app.Fragment;

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

    protected void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
    }
}
