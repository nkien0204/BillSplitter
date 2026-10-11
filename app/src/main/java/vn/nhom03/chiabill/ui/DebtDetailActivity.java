package vn.nhom03.chiabill.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import vn.nhom03.chiabill.R;
import vn.nhom03.chiabill.data.db.GroupEntity;
import vn.nhom03.chiabill.data.db.UserEntity;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.databinding.ActivityDebtDetailBinding;
import vn.nhom03.chiabill.domain.ledger.PaymentStateMachine;
import vn.nhom03.chiabill.domain.ledger.PaymentStateMachine.Action;
import vn.nhom03.chiabill.domain.ledger.PaymentStateMachine.Role;
import vn.nhom03.chiabill.domain.model.Bill;
import vn.nhom03.chiabill.domain.model.Debt;
import vn.nhom03.chiabill.domain.model.DebtStatus;
import vn.nhom03.chiabill.domain.model.Money;
import vn.nhom03.chiabill.domain.qr.BankDirectory;
import vn.nhom03.chiabill.domain.qr.PaymentTarget;
import vn.nhom03.chiabill.domain.qr.TransferContent;
import vn.nhom03.chiabill.domain.qr.VietQrCodec;
import vn.nhom03.chiabill.util.Nav;
import vn.nhom03.chiabill.util.Photos;
import vn.nhom03.chiabill.util.RemoteImage;
import vn.nhom03.chiabill.util.QrExport;
import vn.nhom03.chiabill.util.QrImages;
import vn.nhom03.chiabill.util.Ui;

/**
 * Màn quan trọng nhất cho người nợ: số tiền, VietQR của người trả có sẵn số tiền + nội dung,
 * và các nút theo máy trạng thái thanh toán. "Tôi đã chuyển" có thể kèm ảnh chụp màn hình
 * giao dịch để người trả đối chiếu. Người trả thấy nút xác nhận / nhắc / gửi qua Zalo.
 */
public class DebtDetailActivity extends BaseActivity {
    private static final long RECENT_QR_CHANGE = 7L * 24 * 3600 * 1000;

    private ActivityDebtDetailBinding b;
    private String debtKey;
    private boolean reveal;
    private String payload;
    private Bitmap qrBitmap;
    private String qrFor;
    private AppSnapshot snap;

    private final ActivityResultLauncher<String> storagePermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) saveQr(); else toast("Không có quyền lưu ảnh. Dùng “Sao chép” hoặc chia sẻ thay thế.");
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickProof =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) sendProof(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireUser()) return;
        debtKey = getIntent().getStringExtra(Nav.DEBT_KEY);
        b = ActivityDebtDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar);
        b.reveal.setOnClickListener(v -> { reveal = !reveal; if (snap != null) render(snap); });
        repo().snapshot().observe(this, this::render);
    }

    private void render(AppSnapshot s) {
        if (userMissing(s)) return;
        snap = s;
        Debt d = s.ledger().debt(debtKey);
        if (d == null) {
            toast("Khoản nợ không còn (hoá đơn đã bị sửa hoặc xoá).");
            finish();
            return;
        }
        String me = me();
        Bill bill = s.ledger().bill(d.getBillId());
        GroupEntity g = s.group(d.getGroupId());
        String debtor = s.name(d.getDebtorId());
        String creditor = s.name(d.getCreditorId());
        UserEntity debtorUser = s.user(d.getDebtorId());
        UserEntity creditorUser = s.user(d.getCreditorId());
        Role role = PaymentStateMachine.roleOf(me, d.getDebtorId(), d.getCreditorId());
        DebtStatus st = d.getStatus();

        b.toolbar.setSubtitle(g != null ? g.name : "");
        b.direction.setText((role == Role.DEBTOR ? "Bạn" : debtor) + "  →  " + (role == Role.CREDITOR ? "Bạn" : creditor));
        b.amount.setText(Money.format(d.getAmount()));
        b.billLine.setText(bill.getTitle() + " · " + Ui.day(bill.getCreatedAt()));
        Ui.pill(b.status, Ui.statusLabel(this, st), Ui.statusColors(this, st));

        // ---- QR ----
        PaymentTarget target = s.paymentTarget(d.getCreditorId());
        String content = TransferContent.forDebt(d.getBillId(), debtor);
        payload = target != null ? VietQrCodec.encode(target, d.getAmount(), content) : null;
        boolean showQr = target != null && (role == Role.DEBTOR || (role == Role.CREDITOR && st.isOpen()));
        b.qrCard.setVisibility(showQr ? View.VISIBLE : View.GONE);
        if (target != null) {
            if (!payload.equals(qrFor)) {
                try {
                    qrBitmap = QrImages.encode(payload, Ui.dp(this, 260));
                    qrFor = payload;
                } catch (Exception e) {
                    qrBitmap = null;
                }
            }
            b.qr.setImageBitmap(qrBitmap);
            b.qrCaption.setText(role == Role.DEBTOR ? "Quét bằng app ngân hàng, số tiền đã điền sẵn" : "QR mà " + debtor + " sẽ nhận");
            b.bank.setText(BankDirectory.nameOf(target.getBankBin()));
            b.account.setText(reveal ? target.getAccountNo() : target.maskedAccount());
            b.reveal.setText(reveal ? "Ẩn số tài khoản" : "Hiện đủ số tài khoản");
            b.holder.setText(target.getAccountName().isEmpty() ? "—" : target.getAccountName());
            b.content.setText(content);
            long changedAgo = System.currentTimeMillis() - creditorUser.qrUpdatedAt;
            if (role == Role.DEBTOR && creditorUser.qrUpdatedAt > 0 && changedAgo < RECENT_QR_CHANGE) {
                b.qrWarning.setText(creditor + " vừa đổi QR nhận tiền " + Ui.ago(creditorUser.qrUpdatedAt)
                        + ". Đối chiếu kỹ tên người nhận mà app ngân hàng hiện ra trước khi chuyển.");
            } else {
                b.qrWarning.setText("Trước khi bấm chuyển, đối chiếu tên người nhận mà app ngân hàng hiện ra.");
            }
        }

        b.noQr.setVisibility(target == null && st.isOpen() && role != Role.OTHER ? View.VISIBLE : View.GONE);
        if (target == null) {
            if (role == Role.CREDITOR) {
                b.noQrTitle.setText("Bạn chưa khai QR nhận tiền");
                b.remindQr.setText("Khai QR ngay");
                b.remindQr.setOnClickListener(v -> startActivity(new Intent(this, QrSetupActivity.class)));
            } else {
                b.noQrTitle.setText(creditor + " chưa khai QR nhận tiền");
                b.remindQr.setText("Nhắc " + creditor + " khai QR");
                b.remindQr.setOnClickListener(v -> {
                    repo().sendReminder(d.getCreditorId(), me, debtor + " muốn trả bạn " + Money.format(d.getAmount())
                            + " nhưng bạn chưa khai QR nhận tiền. Vào tab Tôi để khai.", d.getBillId(), d.getKey());
                    toast("Đã nhắc " + creditor + ".");
                });
            }
        }

        // ---- hành động theo vai trò + trạng thái ----
        boolean canPay = role == Role.DEBTOR && PaymentStateMachine.canApply(st, Action.MARK_PAID, role);
        b.debtorActions.setVisibility(canPay ? View.VISIBLE : View.GONE);
        b.saveQr.setEnabled(target != null);
        b.saveQr.setOnClickListener(v -> requestSaveQr());
        b.copy.setOnClickListener(v -> copy(target, d.getAmount(), content));
        b.markPaid.setOnClickListener(v -> askProof(creditor));

        b.creditorConfirm.setVisibility(role == Role.CREDITOR && st == DebtStatus.MARKED_PAID ? View.VISIBLE : View.GONE);
        b.confirm.setOnClickListener(v -> act(Action.CONFIRM, "Đã xác nhận nhận " + Money.format(d.getAmount()) + " từ " + debtor + "."));
        b.dispute.setOnClickListener(v -> act(Action.DISPUTE, "Đã báo " + debtor + " là bạn chưa nhận được."));

        boolean pending = role == Role.CREDITOR && (st == DebtStatus.PENDING || st == DebtStatus.DISPUTED);
        b.creditorPending.setVisibility(pending ? View.VISIBLE : View.GONE);
        boolean guest = debtorUser != null && debtorUser.guest;
        b.remind.setEnabled(!guest);
        b.remind.setOnClickListener(v -> {
            repo().sendReminder(d.getDebtorId(), me, creditor + " nhắc bạn trả " + Money.format(d.getAmount())
                    + " cho “" + bill.getTitle() + "”.", d.getBillId(), d.getKey());
            toast("Đã gửi nhắc tới " + debtor + ".");
        });
        b.remind.setText(guest ? debtor + " là khách" : "Nhắc trong app");
        b.share.setOnClickListener(v -> share(debtor, d.getAmount(), bill.getTitle(), target, content));
        b.cashConfirm.setOnClickListener(v -> act(Action.CONFIRM, "Đã ghi nhận " + debtor + " trả tiền mặt."));

        // ---- ghi chú trạng thái ----
        String note = null;
        int bg = R.color.ok_bg, fg = R.color.ok_fg;
        if (st == DebtStatus.CONFIRMED) {
            note = creditor + " đã xác nhận nhận tiền. Khoản này đã xong.";
        } else if (st == DebtStatus.MARKED_PAID && role == Role.DEBTOR) {
            note = "Đang chờ " + creditor + " xác nhận đã nhận tiền.";
            bg = R.color.warn_bg; fg = R.color.warn_fg;
        } else if (st == DebtStatus.DISPUTED && role == Role.DEBTOR) {
            note = creditor + " báo chưa nhận được tiền. Kiểm tra lại giao dịch rồi bấm “Tôi đã chuyển”.";
            bg = R.color.err_bg; fg = R.color.err_fg;
        } else if (st == DebtStatus.DISPUTED && role == Role.CREDITOR) {
            note = "Bạn đã báo chưa nhận được. " + debtor + " sẽ kiểm tra lại.";
            bg = R.color.err_bg; fg = R.color.err_fg;
        } else if (role == Role.OTHER) {
            note = "Bạn chỉ xem được khoản này. Người nợ và người được trả mới thao tác được.";
            bg = R.color.sand; fg = R.color.muted;
        }
        b.note.setVisibility(note == null ? View.GONE : View.VISIBLE);
        if (note != null) {
            b.note.setText(note);
            b.note.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.col(this, bg)));
            b.note.setTextColor(Ui.col(this, fg));
        }

        // ---- ảnh chuyển khoản (người nợ gửi kèm "Tôi đã chuyển") ----
        String proof = s.proofOf(d.getKey());
        b.proofBox.setVisibility(proof == null ? View.GONE : View.VISIBLE);
        if (proof != null) {
            RemoteImage.load(b.proofPhoto, proof, Ui.dp(this, 720));
            b.proofPhoto.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(proof)));
                } catch (Exception e) {
                    toast("Không mở được ảnh.");
                }
            });
        }
    }

    /** "Tôi đã chuyển": hỏi có gửi kèm ảnh chụp màn hình giao dịch không (không bắt buộc). */
    private void askProof(String creditor) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Gửi kèm ảnh chuyển khoản?")
                .setMessage("Ảnh chụp màn hình giao dịch giúp " + creditor + " xác nhận nhanh hơn.")
                .setPositiveButton("Chọn ảnh", (dlg, w) -> pickProof.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()))
                .setNegativeButton("Không cần", (dlg, w) -> markPaid(creditor, null))
                .show();
    }

    private void sendProof(android.net.Uri uri) {
        String creditor = creditorName();
        app().showLoading(this);
        new Thread(() -> {
            byte[] jpeg;
            try {
                jpeg = Photos.compress(getContentResolver(), uri);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    app().hideLoading();
                    toast("Không đọc được ảnh. Thử ảnh khác.");
                });
                return;
            }
            runOnUiThread(() -> repo().uploadImage(jpeg, "proof", url -> markPaid(creditor, url), err -> {
                app().hideLoading();
                toast(err);
            }));
        }).start();
    }

    private void markPaid(String creditor, String proofUrl) {
        app().showLoading(this);
        repo().markPaid(debtKey, me(), proofUrl, () -> {
            app().hideLoading();
            toast("Đã báo " + creditor + (proofUrl != null ? " kèm ảnh chuyển khoản" : "") + ". Chờ xác nhận.");
        }, err -> {
            app().hideLoading();
            toast(err);
        });
    }

    private String creditorName() {
        Debt d = snap == null ? null : snap.ledger().debt(debtKey);
        return d == null ? "người trả" : snap.name(d.getCreditorId());
    }

    private void act(Action action, String okMessage) {
        repo().applyDebtAction(debtKey, action, me(), () -> toast(okMessage), this::toast);
    }

    private void requestSaveQr() {
        if (qrBitmap == null) return;
        if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        saveQr();
    }

    private void saveQr() {
        try {
            QrExport.saveToGallery(this, qrBitmap, "chiabill-" + debtKey.replace(':', '-'));
            toast("Đã lưu vào Ảnh › ChiaBill. Mở app ngân hàng › Quét QR › chọn ảnh này.");
        } catch (Exception e) {
            toast("Không lưu được ảnh: " + e.getMessage());
        }
    }

    private void copy(PaymentTarget t, long amount, String content) {
        String text = (t != null ? BankDirectory.nameOf(t.getBankBin()) + " · STK " + t.getAccountNo() + "\n" : "")
                + "Số tiền: " + amount + "\nNội dung: " + content;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("ChiaBill", text));
        toast("Đã sao chép thông tin chuyển khoản.");
    }

    /** Zalo là app nhắn tin phổ biến nhất: có Zalo thì mở thẳng Zalo, không thì hiện bảng chia sẻ. */
    private static final String ZALO = "com.zing.zalo";

    private void share(String debtor, long amount, String billTitle, PaymentTarget t, String content) {
        String msg = debtor + " ơi, phần của bạn cho “" + billTitle + "” là " + Money.format(amount) + ".";
        if (t != null) msg += "\nQuét QR đính kèm, hoặc chuyển " + BankDirectory.nameOf(t.getBankBin()) + " STK " + t.getAccountNo()
                + ", nội dung: " + content;
        try {
            Intent send;
            if (qrBitmap != null && t != null) {
                Intent chooser = QrExport.shareIntent(this, qrBitmap, msg);
                send = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
                if (send == null) send = chooser;
            } else {
                send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg);
            }
            if (getPackageManager().getLaunchIntentForPackage(ZALO) != null) {
                startActivity(new Intent(send).setPackage(ZALO));
            } else {
                toast("Máy chưa cài Zalo, chọn app khác để gửi.");
                startActivity(Intent.createChooser(send, "Gửi nhắc nợ"));
            }
        } catch (Exception e) {
            toast("Không mở được chia sẻ: " + e.getMessage());
        }
    }
}
