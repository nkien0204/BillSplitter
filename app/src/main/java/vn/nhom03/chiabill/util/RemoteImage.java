package vn.nhom03.chiabill.util;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Tải ảnh từ URL (ảnh hoá đơn, ảnh chuyển khoản) vào ImageView. Đủ dùng cho vài ảnh mỗi màn,
 * nên không thêm thư viện như Glide: OkHttp có sẵn, cache trong bộ nhớ theo URL.
 */
public final class RemoteImage {
    private RemoteImage() {}

    private static final OkHttpClient http = new OkHttpClient();
    private static final ExecutorService pool = Executors.newFixedThreadPool(2);
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    public static void load(ImageView view, String url, int maxSide) {
        view.setTag(url);
        Bitmap hit = url == null ? null : cache.get(url);
        if (hit != null || url == null) {
            view.setImageBitmap(hit);
            return;
        }
        view.setImageDrawable(null);
        pool.execute(() -> {
            Bitmap bmp = null;
            try (Response r = http.newCall(new Request.Builder().url(url).build()).execute()) {
                ResponseBody body = r.body();
                if (r.isSuccessful() && body != null) bmp = Photos.thumbnail(body.bytes(), maxSide);
            } catch (Exception ignored) {
                // không tải được thì để trống; người dùng vẫn mở được ảnh bằng trình duyệt
            }
            if (bmp != null) cache.put(url, bmp);
            final Bitmap result = bmp;
            main.post(() -> {
                if (url.equals(view.getTag())) view.setImageBitmap(result);
            });
        });
    }
}
