package vn.nhom03.chiabill.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Lưu ảnh QR vào thư viện và chia sẻ ảnh QR qua share sheet (Zalo, Messenger…). */
public final class QrExport {
    private QrExport() {}

    /** Android 10+: không cần quyền. Android 8–9: gọi sau khi đã có WRITE_EXTERNAL_STORAGE. */
    public static Uri saveToGallery(Context c, Bitmap bmp, String displayName) throws IOException {
        ContentResolver cr = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, displayName + ".png");
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= 29) {
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ChiaBill");
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
        }
        Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new IOException("MediaStore insert failed");
        try (OutputStream out = cr.openOutputStream(uri)) {
            if (out == null || !bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("write failed");
        } catch (IOException e) {
            cr.delete(uri, null, null);
            throw e;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(uri, done, null, null);
        }
        return uri;
    }

    public static Intent shareIntent(Context c, Bitmap bmp, String text) throws IOException {
        File dir = new File(c.getCacheDir(), "shared");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs failed");
        File f = new File(dir, "chiabill-qr.png");
        try (FileOutputStream out = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", f);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(send, null);
    }
}
