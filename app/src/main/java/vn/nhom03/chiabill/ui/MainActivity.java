package vn.nhom03.chiabill.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import vn.nhom03.chiabill.R;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.databinding.ActivityMainBinding;
import vn.nhom03.chiabill.ui.tabs.DebtsFragment;
import vn.nhom03.chiabill.ui.tabs.GroupsFragment;
import vn.nhom03.chiabill.ui.tabs.ProfileFragment;
import vn.nhom03.chiabill.util.Nav;

public class MainActivity extends BaseActivity {

    private static final String STATE_TAB = "tab";
    private ActivityMainBinding b;
    private int tab = R.id.tab_groups;
    private String renderedUser;

    private final ActivityResultLauncher<String> notifPermission =
        registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (!granted) toast(
                    "Thông báo đang tắt. Tin vẫn vào hộp thư ở tab Tôi."
                );
            }
        );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Chỉ xử lý lần đầu: sau khi hệ thống khôi phục (process chết) intent gốc vẫn còn extras → mở lại màn chi tiết.
        if (savedInstanceState == null) handleNotificationIntent(getIntent()); // đổi tài khoản + mở màn chi tiết; màn chính vẫn dựng ở dưới
        if (!requireUser()) return;
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.toolbar.setTitle(R.string.app_name);

        if (savedInstanceState != null) tab = savedInstanceState.getInt(
            STATE_TAB,
            R.id.tab_groups
        );
        b.bottomNav.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            return true;
        });
        b.bottomNav.setSelectedItemId(tab); // gọi listener → show(tab)
        renderedUser = me();

        repo().snapshot().observe(this, this::render);
        askNotificationPermissionOnce();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tài khoản đổi trong lúc màn này nằm dưới (từ thông báo): dựng lại để mọi tab theo tài khoản mới.
        if (
            b != null && renderedUser != null && !renderedUser.equals(me())
        ) recreate();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TAB, tab);
    }

    private void render(AppSnapshot s) {
        if (userMissing(s)) return;
        b.toolbar.setSubtitle("Đang dùng tài khoản " + s.name(me()));
        int open = 0;
        for (vn.nhom03.chiabill.domain.model.Debt d : s.ledger().debts()) {
            if (
                d.getDebtorId().equals(me()) &&
                (d.getStatus() ==
                    vn.nhom03.chiabill.domain.model.DebtStatus.PENDING ||
                    d.getStatus() ==
                        vn.nhom03.chiabill.domain.model.DebtStatus.DISPUTED)
            ) open++;
        }
        if (open > 0) b.bottomNav
            .getOrCreateBadge(R.id.tab_debts)
            .setNumber(open);
        else b.bottomNav.removeBadge(R.id.tab_debts);
    }

    private void show(int id) {
        tab = id;
        String tag = "tab" + id;
        if (
            getSupportFragmentManager().findFragmentByTag(tag) != null &&
            getSupportFragmentManager().findFragmentByTag(tag).isVisible()
        ) return;
        Fragment f;
        if (id == R.id.tab_debts) f = new DebtsFragment();
        else if (id == R.id.tab_me) f = new ProfileFragment();
        else f = new GroupsFragment();
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.content, f, tag)
            .commit();
    }

    /** Bấm thông báo: đổi sang tài khoản người nhận rồi mở đúng khoản nợ / hoá đơn. */
    private boolean handleNotificationIntent(Intent intent) {
        if (intent == null) return false;
        String asUser = intent.getStringExtra(Nav.AS_USER);
        if (asUser == null) return false;
        intent.removeExtra(Nav.AS_USER);
        boolean switched = !asUser.equals(me());

        // Ensure the user is cached locally to prevent 'userMissing' redirect
        vn.nhom03.chiabill.data.db.UserEntity user =
            new vn.nhom03.chiabill.data.db.UserEntity();
        user.id = asUser;
        // name and phone might be unknown here, but the ID is enough to satisfy userMissing()
        app().repository().saveUserNow(user);

        session().signIn(asUser, "dummy-token");
        String debtKey = intent.getStringExtra(Nav.DEBT_KEY);
        String billId = intent.getStringExtra(Nav.BILL_ID);
        if (debtKey != null) {
            startActivity(
                new Intent(this, DebtDetailActivity.class).putExtra(
                    Nav.DEBT_KEY,
                    debtKey
                )
            );
        } else if (billId != null) {
            startActivity(
                new Intent(this, BillDetailActivity.class).putExtra(
                    Nav.BILL_ID,
                    billId
                )
            );
        }
        if (switched) toast("Đã chuyển sang tài khoản người nhận thông báo.");
        return true;
    }

    private void askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return;
        android.content.SharedPreferences p = getSharedPreferences(
            "ui",
            MODE_PRIVATE
        );
        if (p.getBoolean("asked_notif", false)) return;
        p.edit().putBoolean("asked_notif", true).apply();
        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }
}
