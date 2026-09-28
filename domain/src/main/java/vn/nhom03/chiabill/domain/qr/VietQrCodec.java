package vn.nhom03.chiabill.domain.qr;

import java.util.Map;

/**
 * Mã hoá và giải mã VietQR (chuẩn NAPAS dựa trên EMVCo Merchant-Presented Mode).
 *
 * <pre>
 * 00 Payload format = 01          01 Point of initiation: 11 tĩnh, 12 động
 * 38 Người nhận: 00 GUID A000000727 · 01 (00 BIN, 01 số TK) · 02 QRIBFTTA|QRIBFTTC
 * 53 Tiền tệ 704 · 54 Số tiền · 58 VN · 59 Tên · 62.08 Nội dung · 63 CRC
 * </pre>
 */
public final class VietQrCodec {
    public static final String NAPAS_GUID = "A000000727";
    public static final String SERVICE_ACCOUNT = "QRIBFTTA";
    public static final String SERVICE_CARD = "QRIBFTTC";
    /** Nhiều app ngân hàng cắt nội dung dài; giữ ngắn. */
    public static final int MAX_CONTENT = 25;

    private VietQrCodec() {}

    /** Sinh chuỗi VietQR. amount = 0 → QR tĩnh; content rỗng → không có trường 62. */
    public static String encode(PaymentTarget t, long amount, String content) {
        if (amount < 0) throw new IllegalArgumentException("amount < 0");
        String c = TransferContent.sanitize(content);
        StringBuilder p = new StringBuilder();
        p.append(Tlv.field("00", "01"));
        p.append(Tlv.field("01", amount > 0 ? "12" : "11"));
        String bank = Tlv.field("00", t.getBankBin()) + Tlv.field("01", t.getAccountNo());
        p.append(Tlv.field("38", Tlv.field("00", NAPAS_GUID) + Tlv.field("01", bank) + Tlv.field("02", SERVICE_ACCOUNT)));
        p.append(Tlv.field("53", "704"));
        if (amount > 0) p.append(Tlv.field("54", Long.toString(amount)));
        p.append(Tlv.field("58", "VN"));
        if (!c.isEmpty()) p.append(Tlv.field("62", Tlv.field("08", c)));
        p.append("6304");
        return p + Crc16.ccittFalseHex(p.toString());
    }

    public static QrParseResult parse(String raw) {
        if (raw == null) return QrParseResult.fail(QrError.NOT_EMV, null);
        String s = raw.trim().replace("\r", "").replace("\n", "");
        if (!s.startsWith("000201")) return QrParseResult.fail(QrError.NOT_EMV, null);
        int idx = s.lastIndexOf("6304");
        if (idx < 0 || idx + 8 != s.length()) return QrParseResult.fail(QrError.TRUNCATED, null);
        String want = s.substring(idx + 4).toUpperCase(java.util.Locale.ROOT);
        String got = Crc16.ccittFalseHex(s.substring(0, idx + 4));
        if (!want.equals(got)) {
            return QrParseResult.fail(QrError.BAD_CRC, "CRC trong mã là " + want + ", tính lại được " + got + ". Ảnh QR hỏng hoặc chuỗi đã bị sửa.");
        }
        try {
            Map<String, String> top = Tlv.parse(s);
            String m38 = top.get("38");
            if (m38 == null) return QrParseResult.fail(QrError.NOT_VIETQR, null);
            Map<String, String> merchant = Tlv.parse(m38);
            if (!NAPAS_GUID.equals(merchant.get("00")) || merchant.get("01") == null) {
                return QrParseResult.fail(QrError.NOT_VIETQR, null);
            }
            String svc = merchant.get("02");
            if (!SERVICE_ACCOUNT.equals(svc) && !SERVICE_CARD.equals(svc)) {
                return QrParseResult.fail(QrError.UNSUPPORTED_SERVICE, null);
            }
            Map<String, String> bank = Tlv.parse(merchant.get("01"));
            String bin = bank.get("00");
            String acc = bank.get("01");
            if (!PaymentTarget.isValidBin(bin) || acc == null || acc.isEmpty()) {
                return QrParseResult.fail(QrError.NOT_VIETQR, null);
            }
            long amount = 0;
            String a = top.get("54");
            if (a != null) {
                try {
                    amount = Math.round(Double.parseDouble(a));
                } catch (NumberFormatException e) {
                    return QrParseResult.fail(QrError.NOT_EMV, "Trường số tiền không hợp lệ: " + a);
                }
            }
            String content = "";
            if (top.get("62") != null) {
                String c = Tlv.parse(top.get("62")).get("08");
                if (c != null) content = c;
            }
            String name = top.get("59");
            return QrParseResult.ok(new PaymentTarget(bin, acc, name), amount, content, "12".equals(top.get("01")));
        } catch (Tlv.TlvException e) {
            return QrParseResult.fail(e.getError(), null);
        }
    }
}
