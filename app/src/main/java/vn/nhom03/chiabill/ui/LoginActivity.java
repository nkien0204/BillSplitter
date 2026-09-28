package vn.nhom03.chiabill.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import vn.nhom03.chiabill.R;
import vn.nhom03.chiabill.data.db.UserEntity;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.databinding.ActivityLoginBinding;
import vn.nhom03.chiabill.domain.model.Money;
import vn.nhom03.chiabill.domain.qr.BankDirectory;
import vn.nhom03.chiabill.domain.user.PhoneNumber;
import vn.nhom03.chiabill.util.Ui;

/** Chọn tài khoản demo. Bản có backend sẽ thay màn này bằng đăng nhập email/Google. */
public class LoginActivity extends BaseActivity {
    private ActivityLoginBinding b;
    private final RowAdapter adapter = new RowAdapter();
    private boolean leaving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.accounts.setLayoutManager(new LinearLayoutManager(this));
        b.accounts.setAdapter(adapter);
        b.create.setOnClickListener(v -> askName());

        repo().ensureSeeded(() -> repo().snapshot().observe(this, this::render));
    }

    private void render(AppSnapshot s) {
        String current = me();
        if (leaving) return;
        if (current != null && s.user(current) != null && !s.user(current).guest) {
            openMain();
            return;
        }
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (UserEntity u : s.accounts()) {
            RowAdapter.Row r = new RowAdapter.Row();
            r.avatarName = u.name;
            r.colorIndex = u.colorIndex;
            r.title = u.name;
            String phone = u.phone != null ? PhoneNumber.format(u.phone) : "Chưa có số điện thoại";
            r.subtitle = phone + " · " + (u.hasPaymentProfile() ? "QR " + BankDirectory.nameOf(u.bankBin) : "chưa khai QR");
            long owe = s.ledger().owedBy(u.id, null);
            long owed = s.ledger().owedTo(u.id, null);
            if (owed > owe) {
                r.amount = "+" + Money.format(owed - owe);
                r.amountColor = Ui.col(this, R.color.brand);
            } else if (owe > owed) {
                r.amount = Money.format(owe - owed);
                r.amountColor = Ui.col(this, R.color.owe);
            }
            r.onClick = () -> {
                session().signIn(u.id);
                openMain();
            };
            rows.add(r);
        }
        adapter.submit(rows);
    }

    private void askName() {
        EditText name = new EditText(this);
        name.setHint(R.string.login_name_hint);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText phone = new EditText(this);
        phone.setHint("Số điện thoại, vd 0905 555 555");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(name);
        box.addView(phone);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Tạo tài khoản")
                .setMessage("Người khác sẽ tìm thấy bạn bằng số điện thoại này để thêm vào nhóm.")
                .setView(box)
                .setPositiveButton("Tạo", null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                repo().createAccount(name.getText().toString(), phone.getText().toString(), id -> {
                    dialog.dismiss();
                    session().signIn(id);
                    openMain();
                }, err -> {
                    if (err.startsWith("Cần nhập tên")) name.setError(err); else phone.setError(err);
                })));
        dialog.show();
    }

    private void openMain() {
        if (leaving) return;
        leaving = true;
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
