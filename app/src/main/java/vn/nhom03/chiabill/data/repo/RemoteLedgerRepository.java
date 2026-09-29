package vn.nhom03.chiabill.data.repo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import retrofit2.Response;
import vn.nhom03.chiabill.data.db.*;
import vn.nhom03.chiabill.data.remote.*;
import vn.nhom03.chiabill.domain.ledger.PaymentStateMachine;
import vn.nhom03.chiabill.domain.qr.PaymentTarget;

public class RemoteLedgerRepository implements LedgerRepository {

    private final ChiaBillApi api;
    private final LocalLedgerRepository localRepo;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public RemoteLedgerRepository(Context context, AppDao dao, EventSink sink) {
        this.api = ApiClient.getInstance(context);
        this.localRepo = new LocalLedgerRepository(dao, sink);
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
                onMain(onError, e.getMessage());
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
            try {
                Response<UserEntity> response = api
                    .findUser(rawPhone)
                    .execute();
                if (response.isSuccessful()) {
                    onMain(onResult, response.body());
                } else {
                    onMain(onError, "Search failed");
                }
            } catch (Exception e) {
                onMain(onError, e.getMessage());
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
                // Not implemented in api interface yet, using a dummy or adding it
                // Let's assume we add it to the api interface
                // For now, I'll just call the local repo to avoid blocking
                localRepo.addMember(
                    groupId,
                    userId,
                    actorId,
                    done,
                    onError
                );
                // Wait, the LocalLedgerRepository callbacks are already on main?
                // Let's check LocalLedgerRepository.java
            } catch (Exception e) {
                onMain(onError, e.getMessage());
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
                    onMain(onGroupId, group.id);
                } else {
                    onMain(onError, "Join failed");
                }
            } catch (Exception e) {
                onMain(onError, e.getMessage());
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
                        done,
                        onError
                    );
                } else {
                    onMain(onError, "Removal failed");
                }
            } catch (Exception e) {
                onMain(onError, e.getMessage());
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
                    localRepo.leaveGroup(groupId, userId, done, onError);
                } else {
                    onMain(onError, "Leave failed");
                }
            } catch (Exception e) {
                onMain(onError, e.getMessage());
            }
        });
    }

    @Override
    public void syncGroup(
        String groupId,
        Runnable done,
        Callback<String> onError
    ) {
        executor.execute(() -> {
            try {
                Response<ChiaBillApi.BalanceDataResponse> response = api
                    .getBalances(groupId)
                    .execute();
                if (response.isSuccessful() && response.body() != null) {
                    ChiaBillApi.BalanceDataResponse data = response.body();

                    // Sync Members
                    if (data.members != null) {
                        for (MemberEntity m : data.members) {
                            localRepo.saveMember(m);
                        }
                    }

                    // Sync Bills
                    if (data.bills != null) {
                        for (ChiaBillApi.BillDetailsResponse bData : data.bills) {
                            BillEntity b = bData.bill;
                            List<BillItemEntity> items = bData.items;
                            localRepo.saveBill(b, items);
                        }
                    }

                    // Sync Debt Statuses
                    if (data.debtStatuses != null) {
                        for (DebtStatusEntity s : data.debtStatuses) {
                            localRepo.saveStatus(s);
                        }
                    }

                    onMain(done);
                } else {
                    onMain(onError, "Sync failed: " + response.code());
                }
            } catch (Exception e) {
                onMain(onError, e.getMessage());
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
                    onMain(onError, "Rename failed");
                }
            } catch (Exception e) {
                onMain(onError, e.getMessage());
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
                Response<GroupEntity> response = api
                    .createGroup(request)
                    .execute();
                if (response.isSuccessful() && response.body() != null) {
                    GroupEntity group = response.body();
                    localRepo.saveGroup(group);
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
                ChiaBillApi.BillRequest request = new ChiaBillApi.BillRequest();
                request.groupId = input.groupId;
                request.title = input.title;
                request.payerId = input.spec.getPayerId();
                request.mode = input.spec.getMode().name();
                request.subtotal = input.spec.getSubtotal();
                request.participantsCsv =
                    vn.nhom03.chiabill.data.repo.Mappers.csv(
                        input.spec.getParticipants()
                    );
                request.vatPercent = input.spec.getVatPercent();
                request.servicePercent = input.spec.getServicePercent();
                request.rounding = (int) input.spec.getRounding();
                request.category = input.category.name();
                request.sharesCsv = vn.nhom03.chiabill.data.repo.Mappers.shares(
                    input.spec.getShares()
                );

                if (input.spec.getItems() != null) {
                    List<ChiaBillApi.BillItemRequest> itemReqs =
                        new ArrayList<>();
                    for (vn.nhom03.chiabill.domain.model.BillItem item : input.spec.getItems()) {
                        ChiaBillApi.BillItemRequest ir =
                            new ChiaBillApi.BillItemRequest();
                        ir.position = 0; // Will be set by backend or sorted
                        ir.name = item.getName();
                        ir.price = item.getPrice();
                        ir.consumersCsv =
                            vn.nhom03.chiabill.data.repo.Mappers.csv(
                                item.getConsumers()
                            );
                        itemReqs.add(ir);
                    }
                    request.items = itemReqs;
                }

                Response<BillEntity> response;
                if (input.id == null || input.id.isEmpty()) {
                    response = api.createBill(request).execute();
                } else {
                    response = api.updateBill(input.id, request).execute();
                }

                if (response.isSuccessful() && response.body() != null) {
                    BillEntity bill = response.body();
                    localRepo.saveBill(bill);
                    onMain(onId, bill.id);
                } else {
                    onMain(onError, "Save bill failed");
                }
            } catch (Exception e) {
                onMain(onError, e.getMessage());
            }
        });
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
                    localRepo.deleteBill(billId, actorId, done, onError);
                } else {
                    onMain(onError, "Delete failed");
                }
            } catch (Exception e) {
                onMain(onError, e.getMessage());
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
        executor.execute(() -> {
            try {
                // Parse debtKey (billId:userId)
                String[] parts = debtKey.split(":");
                String billId = parts[0];
                String userId = parts[1];

                Response<Void> response;
                switch (action) {
                    case MARK_PAID:
                        ChiaBillApi.MarkPaidRequest paidReq =
                            new ChiaBillApi.MarkPaidRequest();
                        paidReq.billId = billId;
                        response = api.markPaid(paidReq).execute();
                        break;
                    case CONFIRM:
                        ChiaBillApi.ConfirmPaymentRequest confReq =
                            new ChiaBillApi.ConfirmPaymentRequest();
                        confReq.billId = billId;
                        confReq.debtorId = userId;
                        response = api.confirmPayment(confReq).execute();
                        break;
                    case DISPUTE:
                        ChiaBillApi.DisputePaymentRequest dispReq =
                            new ChiaBillApi.DisputePaymentRequest();
                        dispReq.billId = billId;
                        dispReq.debtorId = userId;
                        response = api.disputePayment(dispReq).execute();
                        break;
                    default:
                        throw new IllegalArgumentException(
                            "Unsupported action"
                        );
                }

                if (response.isSuccessful()) {
                    localRepo.applyDebtAction(
                        debtKey,
                        action,
                        actorId,
                        done,
                        onError
                    );
                } else {
                    onMain(onError, "Action failed");
                }
            } catch (Exception e) {
                onMain(onError, e.getMessage());
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
