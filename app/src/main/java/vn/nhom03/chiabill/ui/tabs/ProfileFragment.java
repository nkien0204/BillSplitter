package vn.nhom03.chiabill.ui.tabs;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import vn.nhom03.chiabill.ChiaBillApp;
import vn.nhom03.chiabill.data.db.InboxEntity;
import vn.nhom03.chiabill.data.db.UserEntity;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.databinding.FragmentProfileBinding;
import vn.nhom03.chiabill.domain.qr.BankDirectory;
import vn.nhom03.chiabill.domain.qr.PaymentTarget;
import vn.nhom03.chiabill.domain.qr.VietQrCodec;
import vn.nhom03.chiabill.domain.user.PhoneNumber;
import vn.nhom03.chiabill.ui.DebtDetailActivity;
import vn.nhom03.chiabill.ui.LoginActivity;
import vn.nhom03.chiabill.ui.QrSetupActivity;
import vn.nhom03.chiabill.ui.RowAdapter;
import vn.nhom03.chiabill.util.Nav;
import vn.nhom03.chiabill.util.QrImages;
import vn.nhom03.chiabill.util.Ui;

public class ProfileFragment extends BaseFragment {

    private FragmentProfileBinding b;
    private final RowAdapter inbox = new RowAdapter();
    private String qrPayloadShown;

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle state
    ) {
        b = FragmentProfileBinding.inflate(inflater, container, false);
        b.inbox.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.inbox.setAdapter(inbox);
        b.editQr.setOnClickListener(v ->
            startActivity(new Intent(requireContext(), QrSetupActivity.class))
        );
        b.rename.setOnClickListener(v -> askName());
        b.switchAccount.setOnClickListener(v -> {
            app().session().signOut();
            startActivity(
                new Intent(requireContext(), LoginActivity.class).addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TASK |
                        Intent.FLAG_ACTIVITY_NEW_TASK
                )
            );
        });
        b.resetDemo.setOnClickListener(v ->
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Đặt lại dữ liệu demo?")
                .setMessage(
                    "Xoá mọi nhóm, hoá đơn, tài khoản đã tạo và nạp lại dữ liệu mẫu."
                )
                .setPositiveButton("Đặt lại", (d, w) -> {
                    // Lấy app ngay bây giờ: callback chạy sau, lúc đó fragment có thể đã detach (requireActivity() sẽ ném).
                    ChiaBillApp a = app();
                    a.repository().resetDemo(() -> {
                        a.session().signIn("dat", "dummy-token");
                        toast(
                            "Đã nạp lại dữ liệu demo. Đang dùng tài khoản Đạt."
                        );
                    });
                })
                .setNegativeButton("Huỷ", null)
                .show()
        );
        b.notifWarning.setOnClickListener(v -> {
            Intent i = new Intent(
                Settings.ACTION_APP_NOTIFICATION_SETTINGS
            ).putExtra(
                Settings.EXTRA_APP_PACKAGE,
                requireContext().getPackageName()
            );
            startActivity(i);
        });
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repo().snapshot().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (b != null) b.notifWarning.setVisibility(
            app().notifications().canPost() ? View.GONE : View.VISIBLE
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private void render(AppSnapshot s) {
        if (b == null || me() == null) return;
        UserEntity u = s.user(me());
        if (u == null) return;
        Ui.avatar(b.avatar, u.name, u.colorIndex);
        b.name.setText(u.name);
        b.phone.setText(
            u.phone != null
                ? PhoneNumber.format(u.phone)
                : "Chưa có số điện thoại"
        );

        PaymentTarget t = s.paymentTarget(me());
        b.qrCard.setVisibility(t != null ? View.VISIBLE : View.GONE);
        b.noQr.setVisibility(t != null ? View.GONE : View.VISIBLE);
        b.editQr.setText(t != null ? "Đổi QR nhận tiền" : "Khai QR nhận tiền");
        if (t != null) {
            b.bank.setText(BankDirectory.nameOf(t.getBankBin()));
            b.account.setText(t.maskedAccount());
            b.holder.setText(
                t.getAccountName().isEmpty()
                    ? "QR tĩnh, không kèm số tiền"
                    : t.getAccountName()
            );
            String payload = VietQrCodec.encode(t, 0, "");
            if (!payload.equals(qrPayloadShown)) {
                try {
                    Bitmap bmp = QrImages.encode(
                        payload,
                        Ui.dp(requireContext(), 220)
                    );
                    b.qrImage.setImageBitmap(bmp);
                    qrPayloadShown = payload;
                } catch (Exception e) {
                    b.qrImage.setImageDrawable(null);
                }
            }
        }

        List<RowAdapter.Row> rows = new ArrayList<>();
        for (InboxEntity e : s.inboxOf(me())) {
            RowAdapter.Row r = new RowAdapter.Row();
            r.title = Ui.ago(e.createdAt);
            r.subtitle = e.message;
            r.subtitleMultiline = true;
            if (e.debtKey != null && s.ledger().debt(e.debtKey) != null) {
                String key = e.debtKey;
                r.onClick = () ->
                    startActivity(
                        new Intent(
                            requireContext(),
                            DebtDetailActivity.class
                        ).putExtra(Nav.DEBT_KEY, key)
                    );
            }
            rows.add(r);
            if (rows.size() >= 20) break;
        }
        inbox.submit(rows);
        b.inboxEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        b.notifWarning.setVisibility(
            app().notifications().canPost() ? View.GONE : View.VISIBLE
        );
    }

    /** FR13: đổi tên hiển thị. Ảnh đại diện là chữ cái đầu trên màu cố định của tài khoản. */
    private void askName() {
        String me = me();
        if (me == null) return;
        EditText input = new EditText(requireContext());
        input.setInputType(
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        input.setText(b != null ? b.name.getText() : "");
        input.setSelection(input.getText().length());
        FrameLayout box = new FrameLayout(requireContext());
        int pad = Ui.dp(requireContext(), 20);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sửa tên")
            .setView(box)
            .setPositiveButton("Lưu", (d, w) -> {
                ChiaBillApp a = app();
                a.repository().renameUser(
                    me,
                    input.getText().toString(),
                    () ->
                        android.widget.Toast.makeText(
                            a,
                            "Đã đổi tên.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show(),
                    err ->
                        android.widget.Toast.makeText(
                            a,
                            err,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                );
            })
            .setNegativeButton("Huỷ", null)
            .show();
    }
}
