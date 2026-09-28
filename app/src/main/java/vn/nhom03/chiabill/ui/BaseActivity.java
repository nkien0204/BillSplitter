package vn.nhom03.chiabill.ui;

import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import vn.nhom03.chiabill.ChiaBillApp;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.data.repo.LedgerRepository;
import vn.nhom03.chiabill.util.SessionManager;

public abstract class BaseActivity extends AppCompatActivity {

    protected ChiaBillApp app() {
        return (ChiaBillApp) getApplication();
    }

    protected LedgerRepository repo() {
        return app().repository();
    }

    protected SessionManager session() {
        return app().session();
    }

    /** Id tài khoản đang dùng; null nếu chưa chọn. */
    protected String me() {
        return session().currentUserId();
    }

    /** Chưa chọn tài khoản thì về màn chọn tài khoản. */
    protected boolean requireUser() {
        if (me() != null) return true;
        startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        return false;
    }

    /** Tài khoản trong session không còn trong dữ liệu (vd sau khi đặt lại demo). */
    protected boolean userMissing(AppSnapshot s) {
        if (s.user(me()) != null) return false;
        session().signOut();
        requireUser();
        return true;
    }

    protected void setupToolbar(MaterialToolbar t) {
        t.setNavigationOnClickListener(v -> finish());
    }

    protected void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
