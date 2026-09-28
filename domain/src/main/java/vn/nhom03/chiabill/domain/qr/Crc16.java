package vn.nhom03.chiabill.domain.qr;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF) theo EMVCo QR. Vector chuẩn: "123456789" → 29B1. */
public final class Crc16 {
    private Crc16() {}

    public static String ccittFalseHex(String s) {
        int crc = 0xFFFF;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int j = 0; j < 8; j++) {
                crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
            }
        }
        return String.format(Locale.ROOT, "%04X", crc);
    }
}
