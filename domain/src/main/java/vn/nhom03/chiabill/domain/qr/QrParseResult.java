package vn.nhom03.chiabill.domain.qr;

public final class QrParseResult {
    private final PaymentTarget target;
    private final QrError error;
    private final String detail;
    private final long amount;
    private final String content;
    private final boolean dynamic;

    private QrParseResult(PaymentTarget target, QrError error, String detail, long amount, String content, boolean dynamic) {
        this.target = target;
        this.error = error;
        this.detail = detail;
        this.amount = amount;
        this.content = content;
        this.dynamic = dynamic;
    }

    static QrParseResult ok(PaymentTarget t, long amount, String content, boolean dynamic) {
        return new QrParseResult(t, null, null, amount, content, dynamic);
    }

    static QrParseResult fail(QrError e, String detail) {
        return new QrParseResult(null, e, detail, 0, "", false);
    }

    public boolean isOk() { return error == null; }
    public PaymentTarget getTarget() { return target; }
    public QrError getError() { return error; }
    /** Thông điệp cho người dùng. */
    public String getMessage() { return error == null ? "" : (detail != null ? detail : error.getMessage()); }
    /** 0 nếu QR tĩnh. */
    public long getAmount() { return amount; }
    public String getContent() { return content; }
    public boolean isDynamic() { return dynamic; }
}
