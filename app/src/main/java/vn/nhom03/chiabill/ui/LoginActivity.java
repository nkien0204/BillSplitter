package vn.nhom03.chiabill.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import vn.nhom03.chiabill.R;
import vn.nhom03.chiabill.data.network.AuthApi;
import vn.nhom03.chiabill.data.network.AuthApi.UserAuthResponse;
import vn.nhom03.chiabill.data.repo.AuthRepository;
import vn.nhom03.chiabill.databinding.ActivityLoginBinding;
import vn.nhom03.chiabill.util.Ui;
import vn.nhom03.chiabill.util.i18n.MessageMapper;

public class LoginActivity extends BaseActivity {

    private ActivityLoginBinding b;
    private boolean leaving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.btnLogin.setOnClickListener(v -> handleLogin());
        b.create.setOnClickListener(v -> askRegister());

        if (session().currentUserId() != null && session().authToken() != null) {
            autoLogin();
        }
    }

    /**
     * Đã có token: hỏi server xem còn hợp lệ không. Còn → vào thẳng màn chính; bị từ chối → xoá
     * session và hiện màn đăng nhập; không liên lạc được server (mất mạng, 5xx) → vẫn vào màn chính.
     */
    private void autoLogin() {
        b.getRoot().setVisibility(View.INVISIBLE);
        app()
            .authRepository()
            .me(
                new AuthRepository.AuthCallback<AuthApi.UserAuthResponse.UserInfo>() {
                    @Override
                    public void onSuccess(AuthApi.UserAuthResponse.UserInfo u) {
                        openMain();
                    }

                    @Override
                    public void onError(String errorCode) {
                        if ("INVALID_TOKEN".equals(errorCode)) {
                            session().signOut();
                            b.getRoot().setVisibility(View.VISIBLE);
                        } else {
                            openMain();
                        }
                    }
                }
            );
    }

    private void handleLogin() {
        String phone = b.editPhone.getText().toString().trim();
        String password = b.editPassword.getText().toString().trim();

        if (phone.isEmpty()) {
            b.editPhone.setError("Vui lòng nhập số điện thoại");
            return;
        }
        if (password.isEmpty()) {
            b.editPassword.setError("Vui lòng nhập mật khẩu");
            return;
        }

        ((vn.nhom03.chiabill.ChiaBillApp) getApplication()).showLoading(this);
        ((vn.nhom03.chiabill.ChiaBillApp) getApplication())
            .authRepository()
            .login(
                phone,
                password,
                new AuthRepository.AuthCallback<UserAuthResponse>() {
                    @Override
                    public void onSuccess(UserAuthResponse result) {
                        (
                            (vn.nhom03.chiabill.ChiaBillApp) getApplication()
                        ).hideLoading();

                        // Save user to local cache immediately to prevent 'userMissing' redirect
                        vn.nhom03.chiabill.data.db.UserEntity user =
                            new vn.nhom03.chiabill.data.db.UserEntity();
                        user.id = result.user.id;
                        user.name = result.user.name;
                        user.phone = result.user.phone;
                        ((vn.nhom03.chiabill.ChiaBillApp) getApplication())
                            .repository()
                            .saveUserNow(user);

                        session().signIn(result.user.id, result.token);
                        openMain();
                    }

                    @Override
                    public void onError(String errorCode) {
                        (
                            (vn.nhom03.chiabill.ChiaBillApp) getApplication()
                        ).hideLoading();
                        String friendlyMessage = MessageMapper.mapErrorCode(
                            LoginActivity.this,
                            errorCode
                        );
                        Toast.makeText(
                            LoginActivity.this,
                            friendlyMessage,
                            Toast.LENGTH_SHORT
                        ).show();
                    }
                }
            );
    }

    private void askRegister() {
        EditText name = new EditText(this);
        name.setHint(R.string.login_name_hint);
        name.setInputType(
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );

        EditText phone = new EditText(this);
        phone.setHint("Số điện thoại, vd 0905 555 555");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);

        EditText password = new EditText(this);
        password.setHint("Mật khẩu");
        password.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(name);
        box.addView(phone);
        box.addView(password);

        androidx.appcompat.app.AlertDialog dialog =
            new MaterialAlertDialogBuilder(this)
                .setTitle("Tạo tài khoản")
                .setMessage(
                    "Người khác sẽ tìm thấy bạn bằng số điện thoại này để thêm vào nhóm."
                )
                .setView(box)
                .setPositiveButton("Tạo", null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d ->
            dialog
                .getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String n = name.getText().toString().trim();
                    String p = phone.getText().toString().trim();
                    String pw = password.getText().toString().trim();

                    if (n.isEmpty() || p.isEmpty() || pw.isEmpty()) {
                        Toast.makeText(
                            this,
                            "Vui lòng nhập đầy đủ thông tin",
                            Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    (
                        (vn.nhom03.chiabill.ChiaBillApp) getApplication()
                    ).showLoading(this);
                    ((vn.nhom03.chiabill.ChiaBillApp) getApplication())
                        .authRepository()
                        .register(
                            n,
                            p,
                            pw,
                            new AuthRepository.AuthCallback<UserAuthResponse>() {
                                @Override
                                public void onSuccess(UserAuthResponse result) {
                                    (
                                        (vn.nhom03.chiabill.ChiaBillApp) getApplication()
                                    ).hideLoading();
                                    dialog.dismiss();

                                    // Save user to local cache immediately to prevent 'userMissing' redirect
                                    vn.nhom03.chiabill.data.db.UserEntity user =
                                        new vn.nhom03.chiabill.data.db.UserEntity();
                                    user.id = result.user.id;
                                    user.name = result.user.name;
                                    user.phone = result.user.phone;
                                    (
                                        (vn.nhom03.chiabill.ChiaBillApp) getApplication()
                                    )
                                        .repository()
                                        .saveUserNow(user);

                                    session().signIn(
                                        result.user.id,
                                        result.token
                                    );
                                    openMain();
                                }

                                @Override
                                public void onError(String errorCode) {
                                    (
                                        (vn.nhom03.chiabill.ChiaBillApp) getApplication()
                                    ).hideLoading();
                                    String friendlyMessage =
                                        MessageMapper.mapErrorCode(
                                            LoginActivity.this,
                                            errorCode
                                        );
                                    Toast.makeText(
                                        LoginActivity.this,
                                        friendlyMessage,
                                        Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                        );
                })
        );
        dialog.show();
    }

    private void openMain() {
        if (leaving) return;
        leaving = true;
        startActivity(
            new Intent(this, MainActivity.class).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK
            )
        );
        finish();
    }
}
