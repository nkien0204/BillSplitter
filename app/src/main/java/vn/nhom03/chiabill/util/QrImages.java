package vn.nhom03.chiabill.util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/** Sinh ảnh QR từ chuỗi, và đọc chuỗi QR từ ảnh (ảnh chụp màn hình QR trong app ngân hàng). */
public final class QrImages {
    private QrImages() {}

    public static Bitmap encode(String payload, int sizePx) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix m = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
        int w = m.getWidth(), h = m.getHeight();
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            int off = y * w;
            for (int x = 0; x < w; x++) px[off + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.setPixels(px, 0, w, 0, 0, w, h);
        return bmp;
    }

    /** @return chuỗi trong QR, hoặc null nếu không tìm thấy mã QR trong ảnh. Chạy trên luồng nền. */
    public static String decode(ContentResolver cr, Uri uri) throws IOException {
        Bitmap bmp = loadScaled(cr, uri, 1600);
        if (bmp == null) return null;
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        LuminanceSource src = new RGBLuminanceSource(w, h, px);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        QRCodeReader reader = new QRCodeReader();
        try {
            Result r = reader.decode(new BinaryBitmap(new HybridBinarizer(src)), hints);
            return r.getText();
        } catch (NotFoundException | com.google.zxing.ChecksumException | com.google.zxing.FormatException e) {
            reader.reset();
            try {
                return reader.decode(new BinaryBitmap(new GlobalHistogramBinarizer(src)), hints).getText();
            } catch (Exception again) {
                return null;
            }
        }
    }

    private static Bitmap loadScaled(ContentResolver cr, Uri uri, int maxSide) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream in = cr.openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, opts);
        }
    }
}
