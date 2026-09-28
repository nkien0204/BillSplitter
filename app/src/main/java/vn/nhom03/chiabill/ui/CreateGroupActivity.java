package vn.nhom03.chiabill.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import vn.nhom03.chiabill.data.db.UserEntity;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.databinding.ActivityCreateGroupBinding;
import vn.nhom03.chiabill.domain.user.PhoneNumber;
import vn.nhom03.chiabill.util.Nav;
import vn.nhom03.chiabill.util.Ui;

/**
 * Tạo nhóm. Thành viên có tài khoản được thêm bằng cách tìm đúng số điện thoại (không liệt kê mọi người dùng),
 * thành viên khách chỉ cần tên.
 */
public class CreateGroupActivity extends BaseActivity {
    private ActivityCreateGroupBinding b;
    private final List<String> guests = new ArrayList<>();
    /** userId → tên, theo thứ tự thêm. */
    private final Map<String, String> members = new LinkedHashMap<>();
    private UserEntity found;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireUser()) return;
        b = ActivityCreateGroupBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar);
        b.addGuest.setOnClickListener(v -> addGuest());
        b.guestName.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { addGuest(); return true; }
            return false;
        });
        b.findPhone.setOnClickListener(v -> find());
        b.phone.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { find(); return true; }
            return false;
        });
        b.found.foundAdd.setOnClickListener(v -> addFound());
        b.save.setOnClickListener(v -> save());
        repo().snapshot().observe(this, this::render);
    }

    private void render(AppSnapshot s) {
        if (userMissing(s)) return;
        if (b.accounts.getChildCount() == 0) {
            Chip self = new Chip(this);
            self.setText(s.name(me()) + " (bạn)");
            self.setClickable(false);
            b.accounts.addView(self);
        }
    }

    private void find() {
        String raw = b.phone.getText() == null ? "" : b.phone.getText().toString();
        b.phoneLayout.setError(null);
        b.found.getRoot().setVisibility(View.GONE);
        found = null;
        repo().findUserByPhone(raw, u -> {
            if (u == null) {
                b.phoneLayout.setError("Chưa có tài khoản nào dùng số này. Có thể thêm người đó làm khách ở dưới.");
                return;
            }
            if (u.id.equals(me())) {
                b.phoneLayout.setError("Đây là số của bạn.");
                return;
            }
            found = u;
            Ui.avatar(b.found.foundAvatar, u.name, u.colorIndex);
            b.found.foundName.setText(u.name);
            b.found.foundPhone.setText(PhoneNumber.masked(u.phone));
            boolean already = members.containsKey(u.id);
            b.found.foundAdd.setEnabled(!already);
            b.found.foundAdd.setText(already ? "Đã thêm" : "Thêm");
            b.found.getRoot().setVisibility(View.VISIBLE);
        }, err -> b.phoneLayout.setError(err));
    }

    private void addFound() {
        if (found == null || members.containsKey(found.id)) return;
        String id = found.id;
        members.put(id, found.name);
        Chip c = new Chip(this);
        c.setText(found.name);
        c.setCloseIconVisible(true);
        c.setOnCloseIconClickListener(v -> {
            members.remove(id);
            b.accounts.removeView(c);
        });
        b.accounts.addView(c);
        b.found.getRoot().setVisibility(View.GONE);
        b.phone.setText("");
        found = null;
    }

    private void addGuest() {
        String name = b.guestName.getText() == null ? "" : b.guestName.getText().toString().trim();
        if (name.isEmpty()) return;
        guests.add(name);
        Chip c = new Chip(this);
        c.setText(name + " (khách)");
        c.setCloseIconVisible(true);
        c.setOnCloseIconClickListener(v -> {
            guests.remove(name);
            b.guests.removeView(c);
        });
        b.guests.addView(c);
        b.guestName.setText("");
    }

    private void save() {
        String name = b.name.getText() == null ? "" : b.name.getText().toString().trim();
        if (name.isEmpty()) {
            b.nameLayout.setError("Cần đặt tên nhóm");
            return;
        }
        b.nameLayout.setError(null);
        if (members.isEmpty() && guests.isEmpty()) {
            toast("Nhóm cần ít nhất một người nữa ngoài bạn.");
            return;
        }
        b.save.setEnabled(false);
        repo().createGroup(name, me(), new ArrayList<>(members.keySet()), new ArrayList<>(guests), id -> {
            startActivity(new Intent(this, GroupDetailActivity.class).putExtra(Nav.GROUP_ID, id));
            finish();
        });
    }
}
