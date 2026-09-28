package vn.nhom03.chiabill.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import vn.nhom03.chiabill.R;
import vn.nhom03.chiabill.domain.model.DebtStatus;

public final class Ui {
    private Ui() {}

    private static final int[] AVATAR = {R.color.avatar_0, R.color.avatar_1, R.color.avatar_2,
            R.color.avatar_3, R.color.avatar_4, R.color.avatar_5};

    public static int avatarColor(Context c, int index) {
        return ContextCompat.getColor(c, AVATAR[Math.floorMod(index, AVATAR.length)]);
    }

    public static void avatar(TextView v, String name, int colorIndex) {
        v.setText(name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
        v.setBackgroundTintList(ColorStateList.valueOf(avatarColor(v.getContext(), colorIndex)));
    }

    public static String statusLabel(Context c, DebtStatus s) {
        switch (s) {
            case MARKED_PAID: return c.getString(R.string.status_marked_paid);
            case CONFIRMED: return c.getString(R.string.status_confirmed);
            case DISPUTED: return c.getString(R.string.status_disputed);
            default: return c.getString(R.string.status_pending);
        }
    }

    /** {nền, chữ} của nhãn trạng thái. */
    public static int[] statusColors(Context c, DebtStatus s) {
        switch (s) {
            case MARKED_PAID: return new int[]{col(c, R.color.warn_bg), col(c, R.color.warn_fg)};
            case CONFIRMED: return new int[]{col(c, R.color.ok_bg), col(c, R.color.ok_fg)};
            case DISPUTED: return new int[]{col(c, R.color.err_bg), col(c, R.color.err_fg)};
            default: return new int[]{col(c, R.color.owe_bg), col(c, R.color.owe_fg)};
        }
    }

    public static void pill(TextView v, String text, int[] colors) {
        v.setText(text);
        v.setTextColor(colors[1]);
        v.setBackgroundTintList(ColorStateList.valueOf(colors[0]));
    }

    public static int col(Context c, int res) {
        return ContextCompat.getColor(c, res);
    }

    private static final java.text.SimpleDateFormat DAY =
            new java.text.SimpleDateFormat("dd/MM", java.util.Locale.ROOT);

    public static String day(long millis) {
        synchronized (DAY) {
            return DAY.format(new java.util.Date(millis));
        }
    }

    public static String ago(long millis) {
        long d = Math.max(0, System.currentTimeMillis() - millis) / 1000;
        if (d < 60) return "vừa xong";
        if (d < 3600) return (d / 60) + " phút trước";
        if (d < 86400) return (d / 3600) + " giờ trước";
        if (d < 7 * 86400) return (d / 86400) + " ngày trước";
        return day(millis);
    }

    public static int dp(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }
}
