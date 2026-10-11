package vn.nhom03.chiabill.data.repo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;
import vn.nhom03.chiabill.data.db.*;
import vn.nhom03.chiabill.data.remote.*;
import vn.nhom03.chiabill.domain.ledger.PaymentStateMachine;
import vn.nhom03.chiabill.domain.model.ShareLine;
import vn.nhom03.chiabill.domain.model.SplitResult;
import vn.nhom03.chiabill.domain.qr.PaymentTarget;
import vn.nhom03.chiabill.domain.split.SplitEngine;
import vn.nhom03.chiabill.util.SessionManager;

/**
 * Bản nhiều máy: mọi thay đổi gửi lên máy chủ trước, thành công mới ghi vào Room (cache).
 * {@link #syncAll} thay cache bằng dữ liệu máy chủ, nên mỗi máy thấy thay đổi của máy khác.
 *
 * <p>Tin nhắn cho người khác (hoá đơn mới, đã chuyển, đã nhận...) vẫn do LocalLedgerRepository
 * soạn như bản offline, nhưng thay vì bật thông báo trên máy này, chúng được gửi vào hộp thư của
 * người nhận trên máy chủ. Máy người nhận thấy tin mới khi đồng bộ và bật thông báo ở đó.
 */
public class RemoteLedgerRepository implements LedgerRepository {

    private static final String PREFS = "sync";

    private final ChiaBillApi api;
    private final AppDao dao;
    private final LocalLedgerRepository localRepo;
    private final EventSink notifications;
    private final SessionManager session;
    private final SharedPreferences prefs;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Đã có một lần đồng bộ đang chờ trong hàng: không xếp thêm. */
    private final AtomicBoolean syncQueued = new AtomicBoolean(false);

    public RemoteLedgerRepository(Context context, AppDao dao, EventSink sink) {
        this.api = ApiClient.getInstance(context);
        this.dao = dao;
        this.notifications = sink;
        this.session = new SessionManager(context.getApplicationContext());
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.localRepo = new LocalLedgerRepository(dao, this::sendToInbox);
    }

    /** Tin LocalLedgerRepository soạn cho người khác: gửi vào hộp thư của họ trên máy chủ. */
    private void sendToInbox(String recipientId, String recipientName, String message, String billId, String debtKey) {
        if (recipientId == null || recipientId.equals(session.currentUserId())) return;
        executor.execute(() -> {
            try {
                ChiaBillApi.NotificationRequest request = new ChiaBillApi.NotificationRequest();
                request.recipientId = recipientId;
                request.message = message;
                request.billId = billId;
                request.debtKey = debtKey;
                api.sendNotification(request).execute();
            } catch (Exception ignored) {
                // tin nhắn là phụ: mất mạng thì bỏ qua, dữ liệu chính đã lưu trên máy chủ
            }
        });
    }

    /** Lỗi dễ hiểu cho người dùng. */
    static String friendly(Exception e) {
        if (e instanceof IOException) {
            return "Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại (máy chủ miễn phí có thể cần ~1 phút để thức dậy).";
        }
        return String.valueOf(e.getMessage());
    }

    private static String failed(String what, Response<?> response) {
        String detail = "";
        try {
            if (response.errorBody() != null) {
                String body = response.errorBody().string();
                int i = body.indexOf("\"error\":\"");
                if (i >= 0) {
                    int start = i + 9;
                    int end = body.indexOf('"', start);
                    if (end > start) detail = ": " + body.substring(start, end);
                }
            }
        } catch (IOException ignored) {
            // không đọc được nội dung lỗi
        }
        return what + " (mã " + response.code() + ")" + detail;
    }

    private Runnable thenSync(Runnable done) {
        return () -> {
            requestSync();
            if (done != null) done.run();
        };
    }

    private <T> void onMain(Callback<T> cb, T value) {
        if (cb != null) main.post(() -> cb.onResult(value));
    }

    private void onMain(Runnable r) {
        if (r != null) main.post(r);
    }

    @Override
    public LiveData<AppSnapshot> snapshot() {
        return localRepo.snapshot();
    }

    @Override
    public void ensureSeeded(Runnable done) {
        localRepo.ensureSeeded(done);
    }

    @Override
    public void resetDemo(Runnable done) {
        localRepo.resetDemo(done);
    }

    public void saveUserNow(UserEntity u) {
        localRepo.saveUserNow(u);
    }

    public void saveUser(UserEntity u) {
        executor.execute(() -> localRepo.saveUser(u));
    }

    @Override
    public void createAccount(
        String name,
        String phone,
        Callback<String> onId,
        Callback<String> onError
    ) {
        executor.execute(() -> {
            try {
                ChiaBillApi.UserRequest request = new ChiaBillApi.UserRequest();
                request.name = name;
                request.phone = phone;
                Response<UserEntity> response = api.register(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    UserEntity user = response.body();
                    localRepo.saveUser(user); // Update local cache
                    onMain(onId, user.id);
                } else {
                    onMain(
                        onError,
                        "Registration failed: " + response.errorBody().string()
                    );
                }
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    @Override
    public void findUserByPhone(
        String rawPhone,
        Callback<UserEntity> onResult,
        Callback<String> onError
    ) {
        executor.execute(() -> {
            String phone = vn.nhom03.chiabill.domain.user.PhoneNumber.normalize(
                rawPhone
            );
            if (phone == null) {
                onMain(
                    onError,
                    "Số điện thoại không hợp lệ. Nhập đủ 10 số, vd 0904 444 444."
                );
                return;
            }
            try {
                Response<UserEntity> response = api.findUser(phone).execute();
                if (response.isSuccessful()) {
                    UserEntity u = response.body();
                    // Cache so group screens can resolve this user's name offline.
                    if (u != null) localRepo.saveUserNow(u);
                    onMain(onResult, u);
                } else {
                    onMain(onError, failed("Không tìm được", response));
                }
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    @Override
    public void addMember(
        String groupId,
        String userId,
        String actorId,
        Runnable done,
        Callback<String> onError
    ) {
        executor.execute(() -> {
            try {
                ChiaBillApi.AddMemberRequest request =
                    new ChiaBillApi.AddMemberRequest();
                request.groupId = groupId;
                request.userId = userId;
                Response<Void> response = api.addMember(request).execute();
                if (response.isSuccessful()) {
                    localRepo.addMember(
                        groupId,
                        userId,
                        actorId,
                        thenSync(done),
                        onError
                    );
                } else {
                    onMain(onError, failed("Không thêm được thành viên", response));
                }
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    @Override
    public void joinByInviteCode(
        String rawCode,
        String userId,
        Callback<String> onGroupId,
        Callback<String> onError
    ) {
        executor.execute(() -> {
            try {
                ChiaBillApi.JoinRequest request = new ChiaBillApi.JoinRequest();
                request.inviteCode = rawCode;
                Response<GroupEntity> response = api
                    .joinGroup(request)
                    .execute();
                if (response.isSuccessful() && response.body() != null) {
                    GroupEntity group = response.body();
                    localRepo.saveGroup(group);
                    requestSync(); // thành viên, hoá đơn cũ của nhóm
                    onMain(onGroupId, group.id);
                } else {
                    onMain(onError, failed("Không vào được nhóm. Kiểm tra lại mã mời", response));
                }
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    @Override
    public void removeMember(
        String groupId,
        String userId,
        String actorId,
        Runnable done,
        Callback<String> onError
    ) {
        executor.execute(() -> {
            try {
                Response<Void> response = api
                    .removeMember(groupId, userId)
                    .execute();
                if (response.isSuccessful()) {
                    localRepo.removeMember(
                        groupId,
                        userId,
                        actorId,
                        thenSync(done),
                        onError
                    );
                } else {
                    onMain(onError, failed("Không xoá được thành viên", response));
                }
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    @Override
    public void deleteGroup(
        String groupId,
        String actorId,
        Runnable done,
        Callback<String> onError
    ) {
        executor.execute(() -> {
            try {
                // Money rules live on the client: refuse before touching the server.
                String blocker = localRepo.groupDeleteBlockerNow(
                    groupId,
                    actorId
                );
                if (blocker != null) {
                    onMain(onError, blocker);
                    return;
                }
                Response<Void> response = api.deleteGroup(groupId).execute();
                // 404: already gone on the server, so just clean up the local copy.
                if (response.isSuccessful() || response.code() == 404) {
                    localRepo.deleteGroup(groupId, actorId, thenSync(done), onError);
                } else if (response.code() == 403) {
                    onMain(onError, "Chỉ người tạo nhóm mới xoá được nhóm.");
                } else {
                    onMain(onError, failed("Không xoá được nhóm", response));
                }
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    @Override
    public void leaveGroup(
        String groupId,
        String userId,
        Runnable done,
        Callback<String> onError
    ) {
        executor.execute(() -> {
            try {
                Response<Void> response = api.leaveGroup(groupId).execute();
                if (response.isSuccessful()) {
                    localRepo.leaveGroup(groupId, userId, thenSync(done), onError);
                } else {
                    onMain(onError, failed("Không rời được nhóm", response));
                }
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    @Override
    public void syncGroup(
        String groupId,
        Runnable done,
        Callback<String> onError
    ) {
        syncAll(done, onError);
    }

    @Override
    public void syncAll(Runnable done, Callback<String> onError) {
        boolean wantsResult = done != null || onError != null;
        // Lần tự làm mới định kỳ (không cần kết quả) bỏ qua nếu đã có một lần đang chờ.
        if (!wantsResult && !syncQueued.compareAndSet(false, true)) return;
        executor.execute(() -> {
            if (!wantsResult) syncQueued.set(false);
            String me = session.currentUserId();
            if (me == null || session.authToken() == null) {
                onMain(done);
                return;
            }
            try {
                Response<ChiaBillApi.SyncResponse> response = api.sync().execute();
                if (!response.isSuccessful() || response.body() == null) {
                    onMain(onError, failed("Không đồng bộ được", response));
                    return;
                }
                ChiaBillApi.SyncResponse d = response.body();
                List<BillEntity> bills = new ArrayList<>();
                List<BillItemEntity> items = new ArrayList<>();
                for (ChiaBillApi.BillDetailsResponse bd : orEmpty(d.bills)) {
                    if (bd == null || bd.bill == null) continue;
                    bills.add(bd.bill);
                    for (BillItemEntity it : orEmpty(bd.items)) {
                        it.id = 0; // để Room tự đánh số, tránh trùng id giữa các lần đồng bộ
                        items.add(it);
                    }
                }
                List<InboxEntity> inbox = orEmpty(d.inbox);
                dao.replaceAll(orEmpty(d.users), orEmpty(d.groups), orEmpty(d.members), bills, items,
                        orEmpty(d.debtStatuses), inbox);
                announceNew(me, inbox);
                onMain(done);
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    /** Bật thông báo cho các tin mới tới từ máy khác (id lớn hơn tin đã thấy). Lần đầu chỉ ghi nhớ. */
    private void announceNew(String me, List<InboxEntity> inbox) {
        String key = "seenInbox_" + me;
        long seen = prefs.getLong(key, -1);
        long max = seen;
        for (InboxEntity e : inbox) max = Math.max(max, e.id);
        if (max != seen) prefs.edit().putLong(key, max).apply();
        if (seen < 0 || notifications == null) return;
        UserEntity u = dao.userNow(me);
        String name = u == null ? "" : u.name;
        List<InboxEntity> fresh = new ArrayList<>();
        for (InboxEntity e : inbox) if (e.id > seen && me.equals(e.recipientId)) fresh.add(e);
        Collections.reverse(fresh); // máy chủ trả mới nhất trước; báo theo thứ tự thời gian
        for (InboxEntity e : fresh) {
            main.post(() -> notifications.onMessage(me, name, e.message, e.billId, e.debtKey));
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    /** Sau mỗi thay đổi thành công: lấy lại bản máy chủ để cache khớp hoàn toàn. */
    private void requestSync() {
        syncAll(null, null);
    }

    @Override
    public void uploadImage(byte[] jpeg, String kind, Callback<String> onUrl, Callback<String> onError) {
        executor.execute(() -> {
            try {
                RequestBody body = RequestBody.create(jpeg, MediaType.get("image/jpeg"));
                MultipartBody.Part part = MultipartBody.Part.createFormData("image", kind + ".jpg", body);
                Response<ChiaBillApi.UploadResponse> response = api.upload(kind, part).execute();
                if (response.isSuccessful() && response.body() != null && response.body().url != null) {
                    onMain(onUrl, response.body().url);
                } else {
                    onMain(onError, failed("Không gửi được ảnh", response));
                }
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    @Override
    public void renameUser(
        String userId,
        String name,
        Runnable done,
        Callback<String> onError
    ) {
        executor.execute(() -> {
            try {
                ChiaBillApi.RenameRequest request =
                    new ChiaBillApi.RenameRequest();
                request.name = name;
                Response<UserEntity> response = api
                    .renameUser(request)
                    .execute();
                if (response.isSuccessful() && response.body() != null) {
                    localRepo.saveUser(response.body());
                    onMain(done);
                } else {
                    onMain(onError, failed("Không đổi được tên", response));
                }
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    @Override
    public void setPaymentTarget(
        String userId,
        PaymentTarget target,
        Runnable done
    ) {
        executor.execute(() -> {
            try {
                ChiaBillApi.PaymentTargetRequest request =
                    new ChiaBillApi.PaymentTargetRequest();
                request.bankBin = target.getBankBin();
                request.accountNo = target.getAccountNo();
                request.accountName = target.getAccountName();
                Response<UserEntity> response = api
                    .setPaymentTarget(request)
                    .execute();
                if (response.isSuccessful() && response.body() != null) {
                    localRepo.saveUser(response.body());
                    onMain(done);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void clearPaymentTarget(String userId, Runnable done) {
        executor.execute(() -> {
            try {
                Response<Void> response = api.clearPaymentTarget().execute();
                if (response.isSuccessful()) {
                    // Update local cache
                    UserEntity user = localRepo.getUser(userId);
                    if (user != null) {
                        user.bankBin = null;
                        user.accountNo = null;
                        user.accountName = null;
                        localRepo.saveUser(user);
                    }
                    onMain(done);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void createGroup(
        String name,
        String creatorId,
        List<String> memberIds,
        List<String> guestNames,
        Callback<String> onId
    ) {
        executor.execute(() -> {
            try {
                ChiaBillApi.GroupRequest request =
                    new ChiaBillApi.GroupRequest();
                request.name = name;
                request.memberIds = memberIds;
                Response<GroupEntity> response = api
                    .createGroup(request)
                    .execute();
                if (response.isSuccessful() && response.body() != null) {
                    GroupEntity group = response.body();
                    localRepo.saveGroup(group);
                    // The server adds the creator at position 0.
                    localRepo.saveMember(
                        new MemberEntity(group.id, creatorId, 0)
                    );
                    int position = 1;
                    for (String memberId : memberIds) {
                        if (memberId.equals(creatorId)) continue;
                        localRepo.saveMember(
                            new MemberEntity(group.id, memberId, position++)
                        );
                    }
                    requestSync();
                    onMain(onId, group.id);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void saveBill(
        BillInput input,
        String actorId,
        Callback<String> onId,
        Callback<String> onError
    ) {
        executor.execute(() -> {
            try {
                List<String> order = memberIds(input.groupId);
                String invalid = SplitEngine.check(input.spec, order);
                if (invalid == null && !SplitEngine.split(input.spec, order).isSavable()) {
                    invalid = "Hoá đơn chưa có số tiền hoặc còn món chưa gán người.";
                }
                if (invalid != null) {
                    onMain(onError, invalid);
                    return;
                }

                ChiaBillApi.BillRequest request = new ChiaBillApi.BillRequest();
                request.groupId = input.groupId;
                request.title = input.title.trim().isEmpty() ? "Hoá đơn" : input.title.trim();
                request.payerId = input.spec.getPayerId();
                request.mode = input.spec.getMode().name();
                request.subtotal = input.spec.getSubtotal();
                request.participantsCsv = Mappers.csv(input.spec.getParticipants());
                request.vatPercent = input.spec.getVatPercent();
                request.servicePercent = input.spec.getServicePercent();
                request.rounding = (int) input.spec.getRounding();
                request.category = input.category.name();
                request.sharesCsv = Mappers.shares(input.spec.getShares());
                request.imageUrl = input.imageUrl;
                request.createdAt = input.date;
                if (input.id != null) request.resetDebtorsCsv = Mappers.csv(changedDebtors(input, order));

                if (input.spec.getItems() != null) {
                    List<ChiaBillApi.BillItemRequest> itemReqs = new ArrayList<>();
                    int position = 0;
                    for (vn.nhom03.chiabill.domain.model.BillItem item : input.spec.getItems()) {
                        if (item.getPrice() <= 0) continue;
                        ChiaBillApi.BillItemRequest ir = new ChiaBillApi.BillItemRequest();
                        ir.position = position++;
                        ir.name = item.getName().trim();
                        ir.price = item.getPrice();
                        ir.consumersCsv = Mappers.csv(item.getConsumers());
                        itemReqs.add(ir);
                    }
                    request.items = itemReqs;
                }

                Response<BillEntity> response = input.id == null || input.id.isEmpty()
                    ? api.createBill(request).execute()
                    : api.updateBill(input.id, request).execute();

                if (!response.isSuccessful() || response.body() == null) {
                    onMain(onError, failed("Không lưu được hoá đơn", response));
                    return;
                }
                String id = response.body().id;
                // Lưu vào máy với đúng id của máy chủ; LocalLedgerRepository soạn tin báo cho
                // từng người cần trả (gửi vào hộp thư của họ qua sendToInbox).
                BillInput saved = new BillInput(id, input.groupId, input.title, input.spec,
                        input.category, input.date, input.imageUrl);
                localRepo.saveBill(saved, actorId, ok -> {
                    requestSync();
                    if (onId != null) onId.onResult(id);
                }, err -> {
                    requestSync();
                    if (onId != null) onId.onResult(id);
                });
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    /**
     * Người có số tiền đổi (hoặc đổi người trả) khi sửa hoá đơn: trạng thái "đã chuyển/tranh chấp"
     * của họ không còn đúng nữa, máy chủ đưa về chưa trả (khoản đã xác nhận giữ nguyên).
     */
    private List<String> changedDebtors(BillInput input, List<String> order) {
        BillEntity old = dao.billNow(input.id);
        if (old == null) return new ArrayList<>();
        SplitResult prev = SplitEngine.split(Mappers.toBill(old, dao.itemsNow(old.id)).getSpec(), order);
        SplitResult next = SplitEngine.split(input.spec, order);
        boolean payerChanged = !old.payerId.equals(input.spec.getPayerId());
        Set<String> out = new LinkedHashSet<>();
        for (ShareLine l : prev.getLines()) {
            ShareLine n = next.lineOf(l.getMemberId());
            if (payerChanged || n == null || n.getAmount() != l.getAmount()) out.add(l.getMemberId());
        }
        for (ShareLine l : next.getLines()) {
            if (prev.lineOf(l.getMemberId()) == null) out.add(l.getMemberId());
        }
        return new ArrayList<>(out);
    }

    /** Mọi người từng ở nhóm theo vị trí: thứ tự chia hoá đơn (giống LocalLedgerRepository). */
    private List<String> memberIds(String groupId) {
        List<String> ids = new ArrayList<>();
        for (MemberEntity m : dao.membersNow(groupId)) ids.add(m.userId);
        return ids;
    }

    @Override
    public void deleteBill(
        String billId,
        String actorId,
        Runnable done,
        Callback<String> onError
    ) {
        executor.execute(() -> {
            try {
                Response<Void> response = api.deleteBill(billId).execute();
                if (response.isSuccessful()) {
                    localRepo.deleteBill(billId, actorId, thenSync(done), onError);
                } else {
                    onMain(onError, failed("Không xoá được hoá đơn", response));
                }
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    @Override
    public void applyDebtAction(
        String debtKey,
        PaymentStateMachine.Action action,
        String actorId,
        Runnable done,
        Callback<String> onError
    ) {
        debtAction(debtKey, action, actorId, null, done, onError);
    }

    @Override
    public void markPaid(String debtKey, String actorId, String proofImageUrl, Runnable done, Callback<String> onError) {
        debtAction(debtKey, PaymentStateMachine.Action.MARK_PAID, actorId, proofImageUrl, done, onError);
    }

    private void debtAction(
        String debtKey,
        PaymentStateMachine.Action action,
        String actorId,
        String proofImageUrl,
        Runnable done,
        Callback<String> onError
    ) {
        executor.execute(() -> {
            try {
                // debtKey = billId:debtorId
                int sep = debtKey.indexOf(':');
                String billId = debtKey.substring(0, sep);
                String debtorId = debtKey.substring(sep + 1);

                Response<Void> response;
                switch (action) {
                    case MARK_PAID:
                        ChiaBillApi.MarkPaidRequest paidReq = new ChiaBillApi.MarkPaidRequest();
                        paidReq.billId = billId;
                        paidReq.proofImageUrl = proofImageUrl;
                        response = api.markPaid(paidReq).execute();
                        break;
                    case CONFIRM:
                        ChiaBillApi.ConfirmPaymentRequest confReq = new ChiaBillApi.ConfirmPaymentRequest();
                        confReq.billId = billId;
                        confReq.debtorId = debtorId;
                        response = api.confirmPayment(confReq).execute();
                        break;
                    case DISPUTE:
                        ChiaBillApi.DisputePaymentRequest dispReq = new ChiaBillApi.DisputePaymentRequest();
                        dispReq.billId = billId;
                        dispReq.debtorId = debtorId;
                        response = api.disputePayment(dispReq).execute();
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported action");
                }

                if (response.isSuccessful()) {
                    // Ghi vào máy + soạn tin cho bên kia; rồi lấy lại bản máy chủ (có ảnh chuyển khoản).
                    localRepo.applyDebtAction(debtKey, action, actorId, () -> {
                        requestSync();
                        if (done != null) done.run();
                    }, err -> {
                        requestSync();
                        if (done != null) done.run();
                    });
                } else {
                    onMain(onError, failed("Không cập nhật được khoản nợ", response));
                }
            } catch (Exception e) {
                onMain(onError, friendly(e));
            }
        });
    }

    @Override
    public void sendReminder(
        String recipientId,
        String fromId,
        String message,
        String billId,
        String debtKey
    ) {
        executor.execute(() -> {
            try {
                ChiaBillApi.NotificationRequest request =
                    new ChiaBillApi.NotificationRequest();
                request.recipientId = recipientId;
                request.fromId = fromId;
                request.message = message;
                request.billId = billId;
                request.debtKey = debtKey;
                api.sendNotification(request).execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
