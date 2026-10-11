package vn.nhom03.chiabill.util.i18n;

import android.content.Context;
import vn.nhom03.chiabill.R;

public final class MessageMapper {
    private MessageMapper() {}

    public static String mapErrorCode(Context context, String code) {
        if (code == null) return "Đã xảy ra lỗi không xác định. Vui lòng thử lại!";

        switch (code) {
            case "USER_ALREADY_EXISTS":
                return "Số điện thoại này đã được đăng ký.";
            case "INVALID_CREDENTIALS":
                return "Số điện thoại hoặc mật khẩu không chính xác.";
            case "USER_NOT_FOUND":
                return "Không tìm thấy tài khoản người dùng.";
            case "INTERNAL_SERVER_ERROR":
                return "Máy chủ đang gặp sự cố. Vui lòng quay lại sau!";
            case "NETWORK_ERROR":
                return "Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại; lần đầu trong ngày máy chủ có thể cần khoảng 1 phút để thức dậy.";
            case "BAD_REQUEST":
                return "Yêu cầu không hợp lệ. Vui lòng kiểm tra lại thông tin.";
            default:
                return "Lỗi: " + code;
        }
    }
}
