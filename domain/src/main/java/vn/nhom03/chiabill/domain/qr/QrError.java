package vn.nhom03.chiabill.domain.qr;

public enum QrError {
    NOT_EMV("Không phải mã QR thanh toán (EMVCo)."),
    TRUNCATED("Mã QR bị cắt hoặc sai độ dài trường."),
    BAD_CRC("Mã kiểm tra CRC không khớp: ảnh QR hỏng hoặc chuỗi đã bị sửa."),
    NOT_VIETQR("QR thanh toán nhưng không theo chuẩn NAPAS VietQR nên không điền được số tiền. Dùng QR ngân hàng hoặc nhập tay."),
    UNSUPPORTED_SERVICE("QR không phải chuyển khoản tới tài khoản hoặc thẻ.");

    private final String message;

    QrError(String message) { this.message = message; }

    public String getMessage() { return message; }
}
