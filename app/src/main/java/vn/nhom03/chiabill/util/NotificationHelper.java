package vn.nhom03.chiabill.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import vn.nhom03.chiabill.R;
import vn.nhom03.chiabill.data.repo.EventSink;
import vn.nhom03.chiabill.ui.MainActivity;

/**
 * Đẩy thông báo hệ thống. Vì mọi tài khoản demo ở chung một máy, tiêu đề ghi rõ gửi tới ai;
 * bấm vào thì app đổi sang tài khoản đó và mở đúng khoản nợ.
 */
public final class NotificationHelper implements EventSink {
    public static final String CHANNEL = "debts";
    private final Context app;
    private int nextId = 1000;

    public NotificationHelper(Context context) {
        this.app = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, app.getString(R.string.channel_debts), NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription(app.getString(R.string.channel_debts_desc));
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    public boolean canPost() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(app).areNotificationsEnabled();
    }

    @SuppressLint("MissingPermission") // canPost() đã kiểm quyền; SecurityException vẫn được bắt
    @Override
    public void onMessage(String recipientId, String recipientName, String message, String billId, String debtKey) {
        if (!canPost()) return; // tin vẫn nằm trong hộp thư của người nhận
        Intent open = new Intent(app, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Nav.AS_USER, recipientId)
                .putExtra(Nav.BILL_ID, billId)
                .putExtra(Nav.DEBT_KEY, debtKey);
        int id = nextId++;
        PendingIntent pi = PendingIntent.getActivity(app, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(app.getString(R.string.notif_to, recipientName))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        try {
            NotificationManagerCompat.from(app).notify(id, b.build());
        } catch (SecurityException ignored) {
            // quyền bị thu hồi giữa chừng
        }
    }
}
