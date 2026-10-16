package vn.nhom03.chiabill.data.repo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.lifecycle.LiveData;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import vn.nhom03.chiabill.data.db.*;
import vn.nhom03.chiabill.domain.ledger.PaymentStateMachine;
import vn.nhom03.chiabill.domain.qr.PaymentTarget;
import vn.nhom03.chiabill.domain.user.InviteCode;
import vn.nhom03.chiabill.domain.user.PhoneNumber;

/**
 * Bản nhiều máy dùng Firebase, thay cho máy chủ Render + Supabase.
 *
 * <p><b>Ghi:</b> mọi luật nghiệp vụ (ai được sửa hoá đơn, còn nợ thì không rời nhóm, máy trạng thái
 * thanh toán...) vẫn do {@link LocalLedgerRepository} kiểm trên Room như bản offline. Kiểm xong và ghi
 * vào Room thì lớp này chép đúng các dòng vừa đổi lên Firestore. Firestore Security Rules kiểm lại
 * phía máy chủ (firestore.rules), nên app bị sửa cũng không ghi bậy được.
 *
 * <p><b>Đọc:</b> lắng nghe Firestore (nhóm của mình, hoá đơn, trạng thái nợ, hồ sơ thành viên, hộp
 * thư). Mỗi khi có thay đổi, dựng lại toàn bộ Room trong một giao dịch, nên màn hình (đọc Room qua
 * LiveData) tự cập nhật trong khoảng một giây, không cần đồng bộ định kỳ như bản Render.
 *
 * <p>Mất mạng: Firestore giữ bản sao và hàng đợi ghi trong máy, có mạng lại thì tự gửi.
 */
public class FirestoreLedgerRepository implements LedgerRepository {

    private static final String TAG = "FirestoreRepo";
    private static final String PREFS = "sync";
    private static final long TIMEOUT_S = 15;
    private static final long REBUILD_DELAY_MS = 250;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final AppDao dao;
    private final LocalLedgerRepository localRepo;
    private final EventSink notifications;
    private final SharedPreferences prefs;
    /** Ghi lên Firestore (chờ kết quả nên tách khỏi luồng lắng nghe). */
    private final Executor executor = Executors.newSingleThreadExecutor();
    /** Mọi callback lắng nghe và việc dựng lại Room chạy trên đúng một luồng này: không cần khoá. */
    private final ScheduledExecutorService mirrorExec = Executors.newSingleThreadScheduledExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    // ---- trạng thái lắng nghe (chỉ đụng tới trên mirrorExec) ----
    private String listeningUid;
    private ListenerRegistration groupsReg;
    private ListenerRegistration inboxReg;
    private final Map<String, DocumentSnapshot> groupDocs = new LinkedHashMap<>();
    private final Map<String, ListenerRegistration[]> groupRegs = new HashMap<>();
    private final Map<String, List<DocumentSnapshot>> billDocs = new HashMap<>();
    private final Map<String, List<DocumentSnapshot>> statusDocs = new HashMap<>();
    private final Map<String, ListenerRegistration> userRegs = new HashMap<>();
    private final Map<String, DocumentSnapshot> userDocs = new HashMap<>();
    private List<DocumentSnapshot> inboxDocs = new ArrayList<>();
    /** Những luồng nghe chưa trả lần đầu. Còn thì chưa dựng Room, để không xoá nhầm dữ liệu đang tải. */
    private final Set<String> waiting = new HashSet<>();
    private boolean inboxPrimed;
    private ScheduledFuture<?> pendingRebuild;
    private final List<Runnable> afterRebuild = new ArrayList<>();

    public FirestoreLedgerRepository(Context context, AppDao dao, EventSink sink) {
        this.dao = dao;
        this.notifications = sink;
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.localRepo = new LocalLedgerRepository(dao, this::sendToInbox);
        // LUỒNG 1 · Đăng nhập · Bước 4/5: Firebase báo đăng nhập/đăng xuất. Có tài khoản → bắt đầu nghe dữ liệu của tài khoản đó (LUỒNG 2).
        //   Đăng xuất → gỡ luồng nghe, xoá dữ liệu cũ trên máy.
        //   → startListening
        auth.addAuthStateListener(a -> {
            FirebaseUser u = a.getCurrentUser();
            mirrorExec.execute(() -> {
                if (u == null) stopListening(true);
                else startListening(u.getUid());
            });
        });
    }

    private String me() {
        FirebaseUser u = auth.getCurrentUser();
        return u == null ? null : u.getUid();
    }

    // ================================================================ đọc: Firestore → Room

    // LUỒNG 2 · Đồng bộ Firestore → màn hình · Bước 1/6: Mở 3 luồng nghe: các nhóm có mình (activeMemberIds), hộp thư của mình, hồ sơ của mình.
    //   → onGroups, onInbox, watchUser
    private void startListening(String uid) {
        if (uid.equals(listeningUid)) return;
        stopListening(false);
        listeningUid = uid;
        inboxPrimed = false;
        waiting.add("groups");
        waiting.add("inbox");
        groupsReg = db.collection("groups")
            .whereArrayContains("activeMemberIds", uid)
            .addSnapshotListener(mirrorExec, (snap, err) -> {
                if (uid.equals(listeningUid)) onGroups(snap, err);
            });
        inboxReg = db.collection("users").document(uid).collection("inbox")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener(mirrorExec, (snap, err) -> {
                if (uid.equals(listeningUid)) onInbox(uid, snap, err);
            });
        watchUser(uid);
    }

    /** Đăng xuất: gỡ mọi luồng nghe và (clear = true) xoá dữ liệu của tài khoản cũ khỏi máy. */
    private void stopListening(boolean clear) {
        if (groupsReg != null) groupsReg.remove();
        if (inboxReg != null) inboxReg.remove();
        for (ListenerRegistration[] regs : groupRegs.values()) for (ListenerRegistration r : regs) r.remove();
        for (ListenerRegistration r : userRegs.values()) r.remove();
        groupsReg = null;
        inboxReg = null;
        groupRegs.clear();
        userRegs.clear();
        groupDocs.clear();
        billDocs.clear();
        statusDocs.clear();
        userDocs.clear();
        inboxDocs = new ArrayList<>();
        waiting.clear();
        listeningUid = null;
        if (clear) dao.clearAll();
    }

    // LUỒNG 2 · Đồng bộ Firestore → màn hình · Bước 2/6: Danh sách nhóm đổi: với mỗi nhóm mới thì nghe thêm hoá đơn + trạng thái nợ (watchGroup),
    //   với mỗi thành viên thì nghe hồ sơ (watchUser: tên, QR nhận tiền). Nhóm mất thì gỡ.
    //   → scheduleRebuild
    private void onGroups(QuerySnapshot snap, FirebaseFirestoreException err) {
        if (err != null || snap == null) {
            Log.w(TAG, "Không nghe được danh sách nhóm", err);
            waiting.remove("groups");
            scheduleRebuild();
            return;
        }
        Set<String> now = new HashSet<>();
        for (DocumentSnapshot d : snap.getDocuments()) now.add(d.getId());
        for (String gone : new ArrayList<>(groupDocs.keySet())) {
            if (!now.contains(gone)) forgetGroup(gone);
        }
        groupDocs.clear();
        for (DocumentSnapshot d : snap.getDocuments()) {
            groupDocs.put(d.getId(), d);
            if (!groupRegs.containsKey(d.getId())) watchGroup(d.getId());
            for (String id : strings(d.get("memberIds"))) {
                if (!isGuestId(id)) watchUser(id);
            }
        }
        waiting.remove("groups");
        scheduleRebuild();
    }

    private void watchGroup(String g) {
        String uid = listeningUid;
        waiting.add("bills:" + g);
        waiting.add("statuses:" + g);
        ListenerRegistration bills = db.collection("groups").document(g).collection("bills")
            .addSnapshotListener(mirrorExec, (snap, err) -> {
                if (!uid.equals(listeningUid) || !groupRegs.containsKey(g)) return;
                if (snap != null) billDocs.put(g, snap.getDocuments());
                else Log.w(TAG, "Không nghe được hoá đơn nhóm " + g, err);
                waiting.remove("bills:" + g);
                scheduleRebuild();
            });
        ListenerRegistration statuses = db.collection("groups").document(g).collection("statuses")
            .addSnapshotListener(mirrorExec, (snap, err) -> {
                if (!uid.equals(listeningUid) || !groupRegs.containsKey(g)) return;
                if (snap != null) statusDocs.put(g, snap.getDocuments());
                else Log.w(TAG, "Không nghe được khoản nợ nhóm " + g, err);
                waiting.remove("statuses:" + g);
                scheduleRebuild();
            });
        groupRegs.put(g, new ListenerRegistration[] {bills, statuses});
    }

    /** Bị xoá khỏi nhóm, rời nhóm, hoặc nhóm bị xoá. */
    private void forgetGroup(String g) {
        ListenerRegistration[] regs = groupRegs.remove(g);
        if (regs != null) for (ListenerRegistration r : regs) r.remove();
        billDocs.remove(g);
        statusDocs.remove(g);
        waiting.remove("bills:" + g);
        waiting.remove("statuses:" + g);
    }

    /** Hồ sơ thành viên: tên, màu, tài khoản nhận tiền. Nghe để thấy ngay khi ai đó đổi QR. */
    private void watchUser(String id) {
        if (userRegs.containsKey(id)) return;
        String uid = listeningUid;
        waiting.add("user:" + id);
        userRegs.put(id, db.collection("users").document(id)
            .addSnapshotListener(mirrorExec, (doc, err) -> {
                if (!uid.equals(listeningUid)) return;
                if (doc != null && doc.exists()) userDocs.put(id, doc);
                waiting.remove("user:" + id);
                scheduleRebuild();
            }));
    }

    private void onInbox(String uid, QuerySnapshot snap, FirebaseFirestoreException err) {
        if (err != null || snap == null) {
            Log.w(TAG, "Không nghe được hộp thư", err);
            waiting.remove("inbox");
            scheduleRebuild();
            return;
        }
        inboxDocs = snap.getDocuments();
        announce(uid, snap);
        waiting.remove("inbox");
        scheduleRebuild();
    }

    /**
     * Bật thông báo cho tin mới từ máy khác. Lần nghe đầu tiên của phiên: báo những tin mới hơn lần
     * trước đã thấy (tới trong lúc app tắt); lần đầu cài app thì chỉ ghi nhớ. Sau đó: mọi tin vừa thêm.
     */
    // LUỒNG 6 · Thông báo · Bước 3/5: App đang mở: luồng nghe hộp thư thấy tin mới → bật thông báo trên máy.
    //   → NotificationHelper.onMessage
    private void announce(String uid, QuerySnapshot snap) {
        String key = "seenInboxAt_" + uid;
        long seen = prefs.getLong(key, -1);
        long max = seen;
        List<DocumentSnapshot> fresh = new ArrayList<>();
        for (DocumentChange c : snap.getDocumentChanges()) {
            if (c.getType() != DocumentChange.Type.ADDED) continue;
            DocumentSnapshot d = c.getDocument();
            long at = longOf(d, "createdAt");
            max = Math.max(max, at);
            boolean isNew = inboxPrimed || (seen >= 0 && at > seen);
            if (isNew && !d.getMetadata().hasPendingWrites()) fresh.add(d);
        }
        inboxPrimed = true;
        if (max != seen) prefs.edit().putLong(key, max).apply();
        if (notifications == null || fresh.isEmpty()) return;
        fresh.sort((a, b) -> Long.compare(longOf(a, "createdAt"), longOf(b, "createdAt")));
        DocumentSnapshot meDoc = userDocs.get(uid);
        String name = meDoc == null ? "" : String.valueOf(meDoc.getString("name"));
        for (DocumentSnapshot d : fresh) {
            String message = d.getString("message");
            String billId = d.getString("billId");
            String debtKey = d.getString("debtKey");
            main.post(() -> notifications.onMessage(uid, name, message, billId, debtKey));
        }
    }

    private void scheduleRebuild() {
        if (pendingRebuild != null) pendingRebuild.cancel(false);
        pendingRebuild = mirrorExec.schedule(this::rebuild, REBUILD_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** Gọi lại từ luồng khác (sau khi ghi lỗi): dựng lại Room từ bản Firestore để bỏ thay đổi bị từ chối. */
    private void requestRebuild() {
        mirrorExec.execute(this::scheduleRebuild);
    }

    /** Dựng lại toàn bộ Room từ những gì đang nghe được, trong một giao dịch. */
    // LUỒNG 2 · Đồng bộ Firestore → màn hình · Bước 3/6: Gom mọi tài liệu đang nghe thành dòng Room (toUser, toBill, toStatus...) và thay toàn bộ Room
    //   trong một giao dịch. Chờ đủ lần nghe đầu (waiting) để không xoá nhầm dữ liệu đang tải.
    //   → Room phát LiveData → LocalLedgerRepository
    private void rebuild() {
        if (listeningUid == null || !waiting.isEmpty()) return;
        String uid = listeningUid;
        List<UserEntity> users = new ArrayList<>();
        List<GroupEntity> groups = new ArrayList<>();
        List<MemberEntity> members = new ArrayList<>();
        List<BillEntity> bills = new ArrayList<>();
        List<BillItemEntity> items = new ArrayList<>();
        List<DebtStatusEntity> statuses = new ArrayList<>();
        List<InboxEntity> inbox = new ArrayList<>();

        for (DocumentSnapshot d : userDocs.values()) users.add(toUser(d));
        for (DocumentSnapshot g : groupDocs.values()) {
            groups.add(toGroup(g));
            List<String> all = strings(g.get("memberIds"));
            List<String> active = strings(g.get("activeMemberIds"));
            for (int i = 0; i < all.size(); i++) {
                MemberEntity m = new MemberEntity(g.getId(), all.get(i), i);
                m.active = active.contains(all.get(i));
                members.add(m);
            }
            Object guests = g.get("guests");
            if (guests instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) guests).entrySet()) {
                    if (e.getValue() instanceof Map) {
                        users.add(toGuest(String.valueOf(e.getKey()), (Map<?, ?>) e.getValue()));
                    }
                }
            }
            for (DocumentSnapshot b : orEmpty(billDocs.get(g.getId()))) {
                bills.add(toBill(g.getId(), b));
                items.addAll(toItems(b));
            }
            for (DocumentSnapshot s : orEmpty(statusDocs.get(g.getId()))) {
                DebtStatusEntity e = toStatus(s);
                if (e != null) statuses.add(e);
            }
        }
        for (DocumentSnapshot d : inboxDocs) inbox.add(toInbox(uid, d));

        try {
            dao.replaceAll(users, groups, members, bills, items, statuses, inbox);
        } catch (RuntimeException e) {
            Log.e(TAG, "Không ghi được bản Firestore vào máy", e);
        }
        for (Runnable r : afterRebuild) main.post(r);
        afterRebuild.clear();
    }

    // ---- Firestore → entity ----

    private static UserEntity toUser(DocumentSnapshot d) {
        UserEntity u = new UserEntity();
        u.id = d.getId();
        u.name = stringOr(d.getString("name"), "?");
        u.colorIndex = (int) longOf(d, "colorIndex");
        u.guest = false;
        u.phone = d.getString("phone");
        u.bankBin = d.getString("bankBin");
        u.accountNo = d.getString("accountNo");
        u.accountName = d.getString("accountName");
        u.qrUpdatedAt = longOf(d, "qrUpdatedAt");
        u.createdAt = longOf(d, "createdAt");
        return u;
    }

    private static UserEntity toGuest(String id, Map<?, ?> m) {
        UserEntity u = new UserEntity();
        u.id = id;
        u.name = stringOr(m.get("name"), "?");
        u.colorIndex = (int) asLong(m.get("colorIndex"));
        u.guest = true;
        u.createdAt = asLong(m.get("createdAt"));
        return u;
    }

    private static GroupEntity toGroup(DocumentSnapshot d) {
        GroupEntity g = new GroupEntity();
        g.id = d.getId();
        g.name = stringOr(d.getString("name"), "Nhóm");
        g.inviteCode = d.getString("inviteCode");
        g.createdBy = d.getString("createdBy");
        g.createdAt = longOf(d, "createdAt");
        return g;
    }

    private static BillEntity toBill(String groupId, DocumentSnapshot d) {
        BillEntity b = new BillEntity();
        b.id = d.getId();
        b.groupId = groupId;
        b.title = stringOr(d.getString("title"), "Hoá đơn");
        b.payerId = stringOr(d.getString("payerId"), "");
        b.mode = stringOr(d.getString("mode"), "EQUAL");
        b.subtotal = longOf(d, "subtotal");
        b.participantsCsv = stringOr(d.getString("participantsCsv"), "");
        b.vatPercent = (int) longOf(d, "vatPercent");
        b.servicePercent = (int) longOf(d, "servicePercent");
        b.rounding = d.contains("rounding") ? longOf(d, "rounding") : 1000;
        b.category = stringOr(d.getString("category"), "KHAC");
        b.sharesCsv = stringOr(d.getString("sharesCsv"), "");
        b.createdBy = d.getString("createdBy");
        b.imageUrl = d.getString("imageUrl");
        b.createdAt = longOf(d, "createdAt");
        b.updatedAt = longOf(d, "updatedAt");
        return b;
    }

    /** Món nằm ngay trong hoá đơn (mảng items), nên một hoá đơn là một lần đọc. */
    private static List<BillItemEntity> toItems(DocumentSnapshot d) {
        List<BillItemEntity> out = new ArrayList<>();
        Object raw = d.get("items");
        if (!(raw instanceof List)) return out;
        for (Object o : (List<?>) raw) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            BillItemEntity it = new BillItemEntity();
            it.billId = d.getId();
            it.position = (int) asLong(m.get("position"));
            it.name = stringOr(m.get("name"), "");
            it.price = asLong(m.get("price"));
            it.consumersCsv = stringOr(m.get("consumersCsv"), "");
            out.add(it);
        }
        return out;
    }

    private static DebtStatusEntity toStatus(DocumentSnapshot d) {
        String billId = d.getString("billId");
        String debtorId = d.getString("debtorId");
        if (billId == null || debtorId == null) return null;
        DebtStatusEntity s = new DebtStatusEntity();
        s.debtKey = billId + ":" + debtorId;
        s.billId = billId;
        s.status = stringOr(d.getString("status"), "PENDING");
        s.updatedAt = longOf(d, "updatedAt");
        s.proofImageUrl = d.getString("proofImageUrl");
        return s;
    }

    private static InboxEntity toInbox(String uid, DocumentSnapshot d) {
        InboxEntity e = new InboxEntity();
        e.recipientId = uid;
        e.message = stringOr(d.getString("message"), "");
        e.billId = d.getString("billId");
        e.debtKey = d.getString("debtKey");
        e.createdAt = longOf(d, "createdAt");
        return e;
    }

    // ================================================================ ghi: Room → Firestore

    /** Một bước ghi lên Firestore, chạy trên luồng nền. */
    private interface Work {
        void run() throws Exception;
    }

    private void push(Work work, Runnable done, Callback<String> onError) {
        executor.execute(() -> {
            try {
                work.run();
                onMain(done);
            } catch (Exception e) {
                Log.w(TAG, "Ghi Firestore lỗi", e);
                requestRebuild();
                onMain(onError, friendly(e));
            }
        });
    }

    /**
     * Chờ Firestore xác nhận. Quá thời gian (mất mạng) thì coi như xong: thay đổi đã nằm trong hàng đợi
     * của Firestore trên máy và tự gửi khi có mạng lại.
     */
    private static void await(Task<?> task) throws Exception {
        try {
            Tasks.await(task, TIMEOUT_S, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            Log.i(TAG, "Chưa có mạng, Firestore sẽ gửi sau");
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
        }
    }

    /** Đọc một lần (tìm số điện thoại, mã mời): cần mạng nên quá thời gian là lỗi. */
    private static <T> T read(Task<T> task) throws Exception {
        try {
            return Tasks.await(task, TIMEOUT_S, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
        }
    }

    static String friendly(Exception e) {
        if (e instanceof TimeoutException) return "Không kết nối được Firebase. Kiểm tra mạng rồi thử lại.";
        if (e instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) e).getCode()) {
                case PERMISSION_DENIED:
                    return "Firebase từ chối thao tác này (không đủ quyền).";
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "Không kết nối được Firebase. Kiểm tra mạng rồi thử lại.";
                case NOT_FOUND:
                    return "Dữ liệu không còn trên Firebase.";
                default:
                    break;
            }
        }
        return String.valueOf(e.getMessage());
    }

    private <T> void onMain(Callback<T> cb, T value) {
        if (cb != null) main.post(() -> cb.onResult(value));
    }

    private void onMain(Runnable r) {
        if (r != null) main.post(r);
    }

    private com.google.firebase.firestore.DocumentReference groupRef(String g) {
        return db.collection("groups").document(g);
    }

    private static String statusId(String billId, String debtorId) {
        return billId + "_" + debtorId;
    }

    /** Nhóm như đang có trong Room: thành viên theo vị trí, người còn trong nhóm, và khách (không có tài khoản). */
    private Map<String, Object> groupFields(String groupId) {
        GroupEntity g = dao.groupNow(groupId);
        if (g == null) return null;
        List<String> all = new ArrayList<>();
        List<String> active = new ArrayList<>();
        Map<String, Object> guests = new HashMap<>();
        for (MemberEntity m : dao.membersNow(groupId)) {
            all.add(m.userId);
            if (m.active) active.add(m.userId);
            UserEntity u = dao.userNow(m.userId);
            if (u != null && u.guest) {
                Map<String, Object> guest = new HashMap<>();
                guest.put("name", u.name);
                guest.put("colorIndex", u.colorIndex);
                guest.put("createdAt", u.createdAt);
                guests.put(u.id, guest);
            }
        }
        Map<String, Object> f = new HashMap<>();
        f.put("name", g.name);
        f.put("inviteCode", g.inviteCode);
        f.put("createdBy", g.createdBy);
        f.put("createdAt", g.createdAt);
        f.put("memberIds", all);
        f.put("activeMemberIds", active);
        f.put("guests", guests);
        return f;
    }

    /** Ghi lại tài liệu nhóm từ Room (sau thêm/xoá/rời thành viên). */
    private void pushGroup(String groupId) throws Exception {
        Map<String, Object> f = groupFields(groupId);
        if (f != null) await(groupRef(groupId).set(f));
    }

    private Map<String, Object> billFields(BillEntity b) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (BillItemEntity it : dao.itemsNow(b.id)) {
            Map<String, Object> m = new HashMap<>();
            m.put("position", it.position);
            m.put("name", it.name);
            m.put("price", it.price);
            m.put("consumersCsv", it.consumersCsv);
            items.add(m);
        }
        Map<String, Object> f = new HashMap<>();
        f.put("title", b.title);
        f.put("payerId", b.payerId);
        f.put("mode", b.mode);
        f.put("subtotal", b.subtotal);
        f.put("participantsCsv", b.participantsCsv);
        f.put("vatPercent", b.vatPercent);
        f.put("servicePercent", b.servicePercent);
        f.put("rounding", b.rounding);
        f.put("category", b.category);
        f.put("sharesCsv", b.sharesCsv);
        f.put("createdBy", b.createdBy);
        f.put("imageUrl", b.imageUrl);
        f.put("createdAt", b.createdAt);
        f.put("updatedAt", b.updatedAt);
        f.put("items", items);
        return f;
    }

    private Map<String, Object> statusFields(String billId, String debtorId, String status, String proofImageUrl) {
        Map<String, Object> f = new HashMap<>();
        f.put("billId", billId);
        f.put("debtorId", debtorId);
        f.put("status", status);
        f.put("proofImageUrl", proofImageUrl);
        f.put("updatedBy", me());
        f.put("updatedAt", System.currentTimeMillis());
        return f;
    }

    /** Tin LocalLedgerRepository soạn cho người khác: ghi vào hộp thư của họ trên Firestore. */
    // LUỒNG 6 · Thông báo · Bước 2/5: Ghi tin vào hộp thư người nhận: users/{người nhận}/inbox. Rules bắt fromId đúng là mình.
    //   → máy người nhận: onInbox (app mở) hoặc Cloud Function (app tắt)
    private void sendToInbox(String recipientId, String recipientName, String message, String billId, String debtKey) {
        String uid = me();
        if (uid == null || recipientId == null || recipientId.equals(uid) || isGuestId(recipientId)) return;
        Map<String, Object> f = new HashMap<>();
        f.put("fromId", uid);
        f.put("message", message);
        f.put("billId", billId);
        f.put("debtKey", debtKey);
        f.put("createdAt", System.currentTimeMillis());
        // Tin nhắn là phụ: không chờ, Firestore tự gửi khi có mạng.
        db.collection("users").document(recipientId).collection("inbox").add(f)
            .addOnFailureListener(e -> Log.w(TAG, "Không gửi được tin cho " + recipientId, e));
    }

    // ================================================================ LedgerRepository

    @Override
    public LiveData<AppSnapshot> snapshot() {
        return localRepo.snapshot();
    }

    /** Bản Firebase không có dữ liệu demo: mọi thứ đến từ Firestore. */
    @Override
    public void ensureSeeded(Runnable done) {
        onMain(done);
    }

    @Override
    public void resetDemo(Runnable done) {
        onMain(done);
    }

    @Override
    public void saveUserNow(UserEntity u) {
        localRepo.saveUserNow(u);
    }

    @Override
    public void createAccount(String name, String phone, Callback<String> onId, Callback<String> onError) {
        onMain(onError, "Tạo tài khoản ở màn đăng nhập.");
    }

    /** Tra phones/{số} → uid → users/{uid}. Firestore Rules chặn liệt kê, nên không ai dò được danh bạ. */
    @Override
    public void findUserByPhone(String rawPhone, Callback<UserEntity> onResult, Callback<String> onError) {
        executor.execute(() -> {
            String phone = PhoneNumber.normalize(rawPhone);
            if (phone == null) {
                onMain(onError, "Số điện thoại không hợp lệ. Nhập đủ 10 số, vd 0904 444 444.");
                return;
            }
            try {
                // LUỒNG 3 · Tạo nhóm và mời · Bước 2/7: Tra đúng một số: phones/{số} → uid → users/{uid}. Rules cấm liệt kê nên không dò được danh bạ.
                //   → quay lại CreateGroupActivity, hiện người vừa tìm
                DocumentSnapshot p = read(db.collection("phones").document(phone).get());
                String uid = p.exists() ? p.getString("uid") : null;
                if (uid == null) {
                    onMain(onResult, null);
                    return;
                }
                DocumentSnapshot u = read(db.collection("users").document(uid).get());
                if (!u.exists()) {
                    onMain(onResult, null);
                    return;
                }
                UserEntity user = toUser(u);
                localRepo.saveUserNow(user); // để màn nhóm hiện được tên người này
                onMain(onResult, user);
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    @Override
    public void addMember(String groupId, String userId, String actorId, Runnable done, Callback<String> onError) {
        localRepo.addMember(groupId, userId, actorId,
            () -> push(() -> pushGroup(groupId), done, onError), onError);
    }

    /**
     * Người ngoài chưa đọc được nhóm, nên chỉ thêm chính mình vào hai danh sách thành viên
     * (arrayUnion). Rules chỉ cho đúng thay đổi đó (xem joinsSelf trong firestore.rules).
     */
    @Override
    public void joinByInviteCode(String rawCode, String userId, Callback<String> onGroupId, Callback<String> onError) {
        executor.execute(() -> {
            String code = InviteCode.normalize(rawCode);
            if (code == null) {
                onMain(onError, "Mã mời gồm 6 ký tự, vd DAL-AT7.");
                return;
            }
            try {
                DocumentSnapshot invite = read(db.collection("inviteCodes").document(code).get());
                String g = invite.exists() ? invite.getString("groupId") : null;
                if (g == null) {
                    onMain(onError, "Không có nhóm nào dùng mã " + InviteCode.format(code) + ".");
                    return;
                }
                MemberEntity already = dao.memberNow(g, userId);
                if (already != null && already.active) {
                    onMain(onGroupId, g);
                    return;
                }
                // LUỒNG 3 · Tạo nhóm và mời · Bước 7/7: Mã → id nhóm, rồi chỉ thêm đúng mình vào memberIds/activeMemberIds (arrayUnion).
                //   Rules joinsSelf chặn mọi thay đổi khác. Chờ LUỒNG 2 kéo nhóm về rồi mở màn nhóm.
                await(groupRef(g).update(
                    "memberIds", FieldValue.arrayUnion(userId),
                    "activeMemberIds", FieldValue.arrayUnion(userId)));
                // Chờ luồng nghe kéo nhóm, hoá đơn cũ và thành viên về Room rồi mới mở màn nhóm.
                long deadline = System.currentTimeMillis() + TIMEOUT_S * 1000;
                while (System.currentTimeMillis() < deadline) {
                    MemberEntity m = dao.memberNow(g, userId);
                    if (dao.groupNow(g) != null && m != null && m.active) break;
                    Thread.sleep(200);
                }
                GroupEntity group = dao.groupNow(g);
                UserEntity u = dao.userNow(userId);
                if (group != null) {
                    String name = u == null ? "Một người" : u.name;
                    for (MemberEntity m : dao.membersNow(g)) {
                        if (m.active && !m.userId.equals(userId)) {
                            sendToInbox(m.userId, null, name + " đã vào nhóm “" + group.name + "” bằng mã mời.", null, null);
                        }
                    }
                }
                onMain(onGroupId, g);
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    @Override
    public void removeMember(String groupId, String userId, String actorId, Runnable done, Callback<String> onError) {
        localRepo.removeMember(groupId, userId, actorId,
            () -> push(() -> pushGroup(groupId), done, onError), onError);
    }

    @Override
    public void leaveGroup(String groupId, String userId, Runnable done, Callback<String> onError) {
        localRepo.leaveGroup(groupId, userId,
            () -> push(() -> pushGroup(groupId), done, onError), onError);
    }

    @Override
    public void deleteGroup(String groupId, String actorId, Runnable done, Callback<String> onError) {
        executor.execute(() -> {
            String blocker = localRepo.groupDeleteBlockerNow(groupId, actorId);
            if (blocker != null) {
                onMain(onError, blocker);
                return;
            }
            // Ghi nhớ những gì cần xoá trên Firestore trước khi Room xoá chúng.
            GroupEntity g = dao.groupNow(groupId);
            String inviteCode = g == null ? null : g.inviteCode;
            List<String> billIds = new ArrayList<>();
            List<String> statusIds = new ArrayList<>();
            for (BillEntity b : dao.billsOfGroupNow(groupId)) billIds.add(b.id);
            for (DebtStatusEntity s : dao.statusesOfGroupNow(groupId)) {
                statusIds.add(s.debtKey.replace(':', '_'));
            }
            localRepo.deleteGroup(groupId, actorId, () -> push(() -> {
                List<com.google.firebase.firestore.DocumentReference> refs = new ArrayList<>();
                for (String id : statusIds) refs.add(groupRef(groupId).collection("statuses").document(id));
                for (String id : billIds) refs.add(groupRef(groupId).collection("bills").document(id));
                if (inviteCode != null) refs.add(db.collection("inviteCodes").document(inviteCode));
                refs.add(groupRef(groupId)); // cuối cùng: Rules của các mục trên còn đọc được nhóm
                deleteAll(refs);
            }, done, onError), onError);
        });
    }

    /** Một lô ghi tối đa 500 thao tác. Nhóm lớn thì chia lô, giữ đúng thứ tự (nhóm xoá sau cùng). */
    private void deleteAll(List<com.google.firebase.firestore.DocumentReference> refs) throws Exception {
        for (int i = 0; i < refs.size(); i += 450) {
            WriteBatch batch = db.batch();
            for (com.google.firebase.firestore.DocumentReference r : refs.subList(i, Math.min(refs.size(), i + 450))) {
                batch.delete(r);
            }
            await(batch.commit());
        }
    }

    @Override
    public void syncGroup(String groupId, Runnable done, Callback<String> onError) {
        syncAll(done, onError);
    }

    /** Luồng nghe đã tự cập nhật; kéo xuống để làm mới chỉ dựng lại Room ngay. */
    @Override
    public void syncAll(Runnable done, Callback<String> onError) {
        String uid = me();
        if (uid == null) {
            onMain(done);
            return;
        }
        mirrorExec.execute(() -> {
            startListening(uid);
            if (done != null) afterRebuild.add(done);
            scheduleRebuild();
        });
    }

    /** Ảnh để ở users/{uid}/{bill|proof}/…: chỉ chủ ảnh ghi được, người đã đăng nhập đọc được (storage.rules). */
    @Override
    public void uploadImage(byte[] jpeg, String kind, Callback<String> onUrl, Callback<String> onError) {
        executor.execute(() -> {
            String uid = me();
            if (uid == null) {
                onMain(onError, "Cần đăng nhập để gửi ảnh.");
                return;
            }
            try {
                StorageReference ref = FirebaseStorage.getInstance().getReference()
                    .child("users").child(uid).child(kind).child(UUID.randomUUID() + ".jpg");
                StorageMetadata meta = new StorageMetadata.Builder().setContentType("image/jpeg").build();
                read(ref.putBytes(jpeg, meta));
                onMain(onUrl, read(ref.getDownloadUrl()).toString());
            } catch (Exception e) {
                Log.w(TAG, "Không gửi được ảnh", e);
                onMain(onError, "Không gửi được ảnh. Kiểm tra mạng, và Firebase Storage đã bật chưa (cần gói Blaze).");
            }
        });
    }

    @Override
    public void renameUser(String userId, String name, Runnable done, Callback<String> onError) {
        localRepo.renameUser(userId, name, () -> push(() -> {
            UserEntity u = dao.userNow(userId);
            if (u != null && !u.guest) {
                await(db.collection("users").document(userId).update("name", u.name));
            }
        }, done, onError), onError);
    }

    @Override
    public void setPaymentTarget(String userId, PaymentTarget target, Runnable done) {
        localRepo.setPaymentTarget(userId, target, () -> push(() -> pushPaymentTarget(userId), done, null));
    }

    @Override
    public void clearPaymentTarget(String userId, Runnable done) {
        localRepo.clearPaymentTarget(userId, () -> push(() -> pushPaymentTarget(userId), done, null));
    }

    private void pushPaymentTarget(String userId) throws Exception {
        UserEntity u = dao.userNow(userId);
        if (u == null) return;
        Map<String, Object> f = new HashMap<>();
        f.put("bankBin", u.bankBin);
        f.put("accountNo", u.accountNo);
        f.put("accountName", u.accountName);
        f.put("qrUpdatedAt", u.qrUpdatedAt);
        await(db.collection("users").document(userId).update(f));
    }

    @Override
    public void createGroup(String name, String creatorId, List<String> memberIds, List<String> guestNames,
                            Callback<String> onId) {
        localRepo.createGroup(name, creatorId, memberIds, guestNames, id -> push(() -> {
            Map<String, Object> f = groupFields(id);
            if (f == null) return;
            Map<String, Object> invite = new HashMap<>();
            invite.put("groupId", id);
            WriteBatch batch = db.batch();
            batch.set(groupRef(id), f);
            // LUỒNG 3 · Tạo nhóm và mời · Bước 5/7: Chép nhóm lên Firestore: groups/{id} và inviteCodes/{mã} trong một lô (cùng thành công hoặc cùng hỏng).
            //   Rules: người tạo phải là chính mình và có tên trong nhóm.
            //   → máy các thành viên thấy nhóm qua LUỒNG 2
            batch.set(db.collection("inviteCodes").document(String.valueOf(f.get("inviteCode"))), invite);
            await(batch.commit());
        }, () -> {
            if (onId != null) onId.onResult(id);
        }, err -> {
            Log.e(TAG, "Không tạo được nhóm trên Firebase: " + err);
            if (onId != null) onId.onResult(id);
        }));
    }

    /**
     * Lưu hoá đơn. LocalLedgerRepository kiểm luật, tính phần từng người, đưa các khoản đổi số tiền về
     * "chưa trả" (xoá dòng trạng thái) và soạn tin. Ở đây chép hoá đơn lên Firestore, và với mỗi dòng
     * trạng thái vừa bị xoá thì ghi PENDING (Rules không cho xoá trạng thái nợ).
     */
    @Override
    public void saveBill(BillInput input, String actorId, Callback<String> onId, Callback<String> onError) {
        executor.execute(() -> {
            Set<String> before = new HashSet<>();
            if (input.id != null) {
                for (DebtStatusEntity s : dao.statusesOfBillNow(input.id)) before.add(s.debtKey);
            }
            localRepo.saveBill(input, actorId, id -> push(() -> {
                BillEntity b = dao.billNow(id);
                if (b == null) return;
                Set<String> after = new HashSet<>();
                for (DebtStatusEntity s : dao.statusesOfBillNow(id)) after.add(s.debtKey);
                WriteBatch batch = db.batch();
                // LUỒNG 4 · Thêm hoá đơn · Bước 5/7: Chép hoá đơn (kèm món trong mảng items) lên groups/{g}/bills/{id}; khoản bị đổi số tiền ghi PENDING.
                //   Một lô ghi.
                //   → firestore.rules, match /bills
                batch.set(groupRef(b.groupId).collection("bills").document(id), billFields(b));
                for (String key : before) {
                    if (after.contains(key)) continue;
                    String debtor = key.substring(key.indexOf(':') + 1);
                    batch.set(groupRef(b.groupId).collection("statuses").document(statusId(id, debtor)),
                        statusFields(id, debtor, "PENDING", null));
                }
                await(batch.commit());
            }, () -> {
                if (onId != null) onId.onResult(id);
            }, onError), onError);
        });
    }

    @Override
    public void deleteBill(String billId, String actorId, Runnable done, Callback<String> onError) {
        executor.execute(() -> {
            BillEntity b = dao.billNow(billId);
            if (b == null) {
                onMain(done);
                return;
            }
            String groupId = b.groupId;
            List<String> statusIds = new ArrayList<>();
            for (DebtStatusEntity s : dao.statusesOfBillNow(billId)) statusIds.add(s.debtKey.replace(':', '_'));
            localRepo.deleteBill(billId, actorId, () -> push(() -> {
                List<com.google.firebase.firestore.DocumentReference> refs = new ArrayList<>();
                for (String id : statusIds) refs.add(groupRef(groupId).collection("statuses").document(id));
                refs.add(groupRef(groupId).collection("bills").document(billId));
                deleteAll(refs);
            }, done, onError), onError);
        });
    }

    @Override
    public void applyDebtAction(String debtKey, PaymentStateMachine.Action action, String actorId,
                                Runnable done, Callback<String> onError) {
        debtAction(debtKey, action, actorId, null, done, onError);
    }

    @Override
    public void markPaid(String debtKey, String actorId, String proofImageUrl, Runnable done, Callback<String> onError) {
        debtAction(debtKey, PaymentStateMachine.Action.MARK_PAID, actorId, proofImageUrl, done, onError);
    }

    private void debtAction(String debtKey, PaymentStateMachine.Action action, String actorId, String proofImageUrl,
                            Runnable done, Callback<String> onError) {
        executor.execute(() -> {
            DebtStatusEntity cur = dao.statusNow(debtKey);
            // Xác nhận hay báo chưa nhận thì giữ ảnh chuyển khoản cũ; "Tôi đã chuyển" thì dùng ảnh mới.
            String proof = action == PaymentStateMachine.Action.MARK_PAID ? proofImageUrl
                : cur == null ? null : cur.proofImageUrl;
            localRepo.applyDebtAction(debtKey, action, actorId, () -> push(() -> {
                int sep = debtKey.indexOf(':');
                String billId = debtKey.substring(0, sep);
                String debtorId = debtKey.substring(sep + 1);
                BillEntity b = dao.billNow(billId);
                DebtStatusEntity s = dao.statusNow(debtKey);
                if (b == null || s == null) return;
                // LUỒNG 5 · Trả nợ bằng VietQR · Bước 7/8: Chép trạng thái mới lên groups/{g}/statuses/{billId}_{debtorId}, kèm ảnh chuyển khoản.
                //   → firestore.rules validStep; máy người nhận thấy qua LUỒNG 2 và nhận thông báo (LUỒNG 6)
                await(groupRef(b.groupId).collection("statuses").document(statusId(billId, debtorId))
                    .set(statusFields(billId, debtorId, s.status, proof)));
            }, done, onError), onError);
        });
    }

    @Override
    public void sendReminder(String recipientId, String fromId, String message, String billId, String debtKey) {
        localRepo.sendReminder(recipientId, fromId, message, billId, debtKey);
    }

    // ================================================================ tiện ích

    private static boolean isGuestId(String id) {
        return id.startsWith("guest-");
    }

    private static List<String> strings(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List) for (Object o : (List<?>) raw) if (o != null) out.add(String.valueOf(o));
        return out;
    }

    private static long longOf(DocumentSnapshot d, String field) {
        return asLong(d.get(field));
    }

    private static long asLong(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0;
    }

    private static String stringOr(Object o, String fallback) {
        return o == null ? fallback : String.valueOf(o);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }
}
