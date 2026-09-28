package vn.nhom03.chiabill.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import vn.nhom03.chiabill.data.db.UserEntity;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.databinding.ActivityQrSetupBinding;
import vn.nhom03.chiabill.domain.model.Money;
import vn.nhom03.chiabill.domain.qr.BankDirectory;
import vn.nhom03.chiabill.domain.qr.PaymentTarget;
import vn.nhom03.chiabill.domain.qr.QrParseResult;
import vn.nhom03.chiabill.domain.qr.TransferContent;
import vn.nhom03.chiabill.domain.qr.VietQrCodec;
import vn.nhom03.chiabill.util.QrImages;

/**
 * Khai QR nhận tiền: chọn ảnh chụp màn hình QR (Photo Picker, không cần quyền), quét camera,
 * dán chuỗi, hoặc nhập tay. Mọi đường đều đi qua VietQrCodec.parse (kiểm CRC) trước khi lưu.
 */
public class QrSetupActivity extends BaseActivity {
    private ActivityQrSetupBinding b;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<String> bins = new ArrayList<>();
    private PaymentTarget pending;
    private boolean prefilled;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onImagePicked);

    private final ActivityResultLauncher<ScanOptions> scan =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) handleRaw(result.getContents());
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireUser()) return;
        b = ActivityQrSetupBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar);

        List<String> names = new ArrayList<>();
        for (Map.Entry<String, String> e : BankDirectory.all().entrySet()) {
            bins.add(e.getKey());
            names.add(e.getValue() + " (" + e.getKey() + ")");
        }
        ArrayAdapter<String> banks = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        banks.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        b.bank.setAdapter(banks);

        b.pickImage.setOnClickListener(v -> pickImage.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        b.scanCamera.setOnClickListener(v -> scan.launch(new ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Đưa mã QR nhận tiền vào khung")
                .setBeepEnabled(false)
                .setOrientationLocked(false)));
        b.parse.setOnClickListener(v -> handleRaw(b.paste.getText().toString()));
        b.sampleOk.setOnClickListener(v -> {
            String p = VietQrCodec.encode(new PaymentTarget("970422", "0987654321", "NGUYEN VAN A"), 0, "");
            b.paste.setText(p);
            handleRaw(p);
        });
        b.sampleBad.setOnClickListener(v -> {
            String p = VietQrCodec.encode(new PaymentTarget("970422", "0987654321", ""), 0, "");
            p = p.substring(0, p.length() - 1) + (p.endsWith("A") ? "B" : "A");
            b.paste.setText(p);
            handleRaw(p);
        });
        b.useResult.setOnClickListener(v -> {
            if (pending != null) save(pending);
        });
        b.saveManual.setOnClickListener(v -> saveManual());
        b.remove.setOnClickListener(v -> repo().clearPaymentTarget(me(), () -> {
            toast("Đã xoá QR nhận tiền.");
            finish();
        }));

        repo().snapshot().observe(this, this::prefill);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }

    private void prefill(AppSnapshot s) {
        if (userMissing(s) || prefilled) return;
        prefilled = true;
        UserEntity u = s.user(me());
        b.remove.setVisibility(u.hasPaymentProfile() ? View.VISIBLE : View.GONE);
        if (u.hasPaymentProfile()) {
            int idx = bins.indexOf(u.bankBin);
            if (idx >= 0) b.bank.setSelection(idx);
            b.accountNo.setText(u.accountNo);
            b.accountName.setText(u.accountName);
        }
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        b.pickImage.setEnabled(false);
        worker.execute(() -> {
            String text;
            try {
                text = QrImages.decode(getContentResolver(), uri);
            } catch (Exception e) {
                text = null;
            }
            final String result = text;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                b.pickImage.setEnabled(true);
                if (result == null) {
                    showError("Không tìm thấy mã QR trong ảnh. Thử ảnh chụp màn hình rõ nét, chỉ có mã QR.");
                } else {
                    b.paste.setText(result);
                    handleRaw(result);
                }
            });
        });
    }

    private void handleRaw(String raw) {
        QrParseResult r = VietQrCodec.parse(raw);
        if (!r.isOk()) {
            pending = null;
            showError(r.getError().name() + " · " + r.getMessage());
            return;
        }
        pending = r.getTarget();
        b.resultError.setVisibility(View.GONE);
        b.resultOk.setVisibility(View.VISIBLE);
        StringBuilder sb = new StringBuilder();
        sb.append("Ngân hàng: ").append(BankDirectory.nameOf(pending.getBankBin()));
        sb.append("\nSố tài khoản: ").append(pending.getAccountNo());
        if (!pending.getAccountName().isEmpty()) sb.append("\nChủ tài khoản: ").append(pending.getAccountName());
        sb.append(r.isDynamic() ? "\nQR này có sẵn số tiền " + Money.format(r.getAmount()) + "; ChiaBill chỉ lấy số tài khoản." : "\nQR tĩnh (không kèm số tiền)");
        b.resultText.setText(sb);
    }

    private void showError(String msg) {
        b.resultOk.setVisibility(View.GONE);
        b.resultError.setVisibility(View.VISIBLE);
        b.resultError.setText(msg);
    }

    private void saveManual() {
        String acc = b.accountNo.getText().toString().trim();
        if (!PaymentTarget.isValidAccount(acc)) {
            b.accountNo.setError("Số tài khoản 6–19 chữ số");
            return;
        }
        int idx = b.bank.getSelectedItemPosition();
        if (idx < 0 || idx >= bins.size()) return;
        String name = TransferContent.toAscii(b.accountName.getText().toString()).toUpperCase(java.util.Locale.ROOT).trim();
        save(new PaymentTarget(bins.get(idx), acc, name));
    }

    private void save(PaymentTarget t) {
        repo().setPaymentTarget(me(), t, () -> {
            toast("Đã lưu QR nhận tiền: " + BankDirectory.nameOf(t.getBankBin()) + " " + t.maskedAccount());
            finish();
        });
    }
}
