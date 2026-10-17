export type QrError = 'NOT_EMV' | 'TRUNCATED' | 'BAD_CRC' | 'NOT_VIETQR' | 'UNSUPPORTED_SERVICE';

const MESSAGES: Record<QrError, string> = {
  NOT_EMV: 'Không phải mã QR thanh toán (EMVCo).',
  TRUNCATED: 'Mã QR bị cắt hoặc sai độ dài trường.',
  BAD_CRC: 'Mã kiểm tra CRC không khớp: ảnh QR hỏng hoặc chuỗi đã bị sửa.',
  NOT_VIETQR:
    'QR thanh toán nhưng không theo chuẩn NAPAS VietQR nên không điền được số tiền. Dùng QR ngân hàng hoặc nhập tay.',
  UNSUPPORTED_SERVICE: 'QR không phải chuyển khoản tới tài khoản hoặc thẻ.',
};

export function qrErrorMessage(e: QrError): string {
  return MESSAGES[e];
}
