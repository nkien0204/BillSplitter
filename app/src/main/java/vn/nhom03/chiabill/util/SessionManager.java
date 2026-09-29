package vn.nhom03.chiabill.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Tài khoản đang dùng. Bản demo offline không có mật khẩu: chọn tài khoản là vào. */
public final class SessionManager {

    private static final String PREFS = "session";
    private static final String KEY_USER = "current_user";
    private static final String KEY_TOKEN = "auth_token";
    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context
            .getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String currentUserId() {
        return prefs.getString(KEY_USER, null);
    }

    public String authToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public void signIn(String userId, String token) {
        prefs
            .edit()
            .putString(KEY_USER, userId)
            .putString(KEY_TOKEN, token)
            .commit();
    }

    public void signOut() {
        prefs.edit().remove(KEY_USER).apply();
    }
}
