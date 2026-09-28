package vn.nhom03.chiabill.domain.qr;

import java.util.Objects;

/** Người nhận tiền: mã BIN ngân hàng (6 số) + số tài khoản, tên chủ tài khoản nếu biết. */
public final class PaymentTarget {
    private final String bankBin;
    private final String accountNo;
    private final String accountName;

    public PaymentTarget(String bankBin, String accountNo, String accountName) {
        this.bankBin = Objects.requireNonNull(bankBin);
        this.accountNo = Objects.requireNonNull(accountNo);
        this.accountName = accountName == null ? "" : accountName;
    }

    public String getBankBin() { return bankBin; }
    public String getAccountNo() { return accountNo; }
    public String getAccountName() { return accountName; }

    /** 1012345678 → 101•••678 */
    public String maskedAccount() {
        if (accountNo.length() <= 6) return accountNo;
        return accountNo.substring(0, 3) + "•••" + accountNo.substring(accountNo.length() - 3);
    }

    public static boolean isValidBin(String bin) {
        return bin != null && bin.matches("\\d{6}");
    }

    public static boolean isValidAccount(String acc) {
        return acc != null && acc.matches("[0-9A-Za-z]{6,19}");
    }
}
