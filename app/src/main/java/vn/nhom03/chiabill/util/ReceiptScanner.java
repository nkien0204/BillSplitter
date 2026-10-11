package vn.nhom03.chiabill.util;

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

import vn.nhom03.chiabill.domain.receipt.ReceiptParser;

/**
 * Đọc chữ trên ảnh hoá đơn bằng ML Kit Text Recognition (chạy ngay trên máy, không cần mạng,
 * miễn phí), rồi để {@link ReceiptParser} ở module domain tìm tổng tiền, tên quán và các món.
 */
public final class ReceiptScanner {
    private ReceiptScanner() {}

    public interface Listener {
        void onResult(ReceiptParser.Result result);
        void onError(String message);
    }

    public static void scan(Context context, Uri uri, Listener listener) {
        InputImage image;
        try {
            image = InputImage.fromFilePath(context, uri); // tự xoay theo EXIF
        } catch (Exception e) {
            listener.onError("Không mở được ảnh.");
            return;
        }
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(image)
                .addOnSuccessListener(text -> listener.onResult(ReceiptParser.parse(ReceiptParser.rows(fragments(text)))))
                .addOnFailureListener(e -> listener.onError("Không đọc được chữ trên ảnh. Thử chụp thẳng, đủ sáng."))
                .addOnCompleteListener(t -> {
                    try {
                        recognizer.close();
                    } catch (Exception ignored) {
                        // đã xong việc, đóng lỗi cũng không sao
                    }
                });
    }

    /** Mỗi dòng ML Kit nhận ra thành một mảnh có khung bao; ReceiptParser ghép lại theo hàng ngang. */
    private static List<ReceiptParser.Fragment> fragments(Text text) {
        List<ReceiptParser.Fragment> out = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect r = line.getBoundingBox();
                if (r == null) continue;
                out.add(new ReceiptParser.Fragment(line.getText(), r.left, r.top, r.right, r.bottom));
            }
        }
        return out;
    }
}
