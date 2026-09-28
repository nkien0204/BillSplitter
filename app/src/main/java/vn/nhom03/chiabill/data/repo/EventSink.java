package vn.nhom03.chiabill.data.repo;

/** Nơi repository đẩy thông báo ra ngoài (system notification). Repository không biết Android UI. */
public interface EventSink {
    void onMessage(String recipientId, String recipientName, String message, String billId, String debtKey);
}
