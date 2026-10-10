package vn.nhom03.chiabill.data.remote;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;
import vn.nhom03.chiabill.data.db.*;

public interface ChiaBillApi {
    // Auth
    @POST("api/auth/register")
    Call<UserEntity> register(@Body UserRequest request);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body AuthRequest request);

    // Users
    @GET("api/users/find")
    Call<UserEntity> findUser(@Query("phone") String phone);

    @PUT("api/users/rename")
    Call<UserEntity> renameUser(@Body RenameRequest request);

    @PUT("api/users/payment-target")
    Call<UserEntity> setPaymentTarget(@Body PaymentTargetRequest request);

    @DELETE("api/users/payment-target")
    Call<Void> clearPaymentTarget();

    // Groups
    @POST("api/groups")
    Call<GroupEntity> createGroup(@Body GroupRequest request);

    @POST("api/groups/join")
    Call<GroupEntity> joinGroup(@Body JoinRequest request);

    @GET("api/groups")
    Call<List<GroupEntity>> listGroups();

    @POST("api/groups/add-member")
    Call<Void> addMember(@Body AddMemberRequest request);

    @GET("api/groups/{groupId}/members")
    Call<List<MemberResponse>> getMembers(@Path("groupId") String groupId);

    @DELETE("api/groups/members/{groupId}/{userId}")
    Call<Void> removeMember(
        @Path("groupId") String groupId,
        @Path("userId") String userId
    );

    @DELETE("api/groups/{groupId}")
    Call<Void> deleteGroup(@Path("groupId") String groupId);

    @DELETE("api/groups/{groupId}/leave")
    Call<Void> leaveGroup(@Path("groupId") String groupId);

    // Bills
    @POST("api/bills")
    Call<BillEntity> createBill(@Body BillRequest request);

    @PUT("api/bills/{id}")
    Call<BillEntity> updateBill(
        @Path("id") String id,
        @Body BillRequest request
    );

    @DELETE("api/bills/{id}")
    Call<Void> deleteBill(@Path("id") String id);

    @GET("api/bills/group/{groupId}")
    Call<List<BillEntity>> listBillsByGroup(@Path("groupId") String groupId);

    @GET("api/bills/{id}")
    Call<BillDetailsResponse> getBillDetails(@Path("id") String id);

    // Payments
    @POST("api/payments/mark-paid")
    Call<Void> markPaid(@Body MarkPaidRequest request);

    @POST("api/payments/confirm")
    Call<Void> confirmPayment(@Body ConfirmPaymentRequest request);

    @POST("api/payments/dispute")
    Call<Void> disputePayment(@Body DisputePaymentRequest request);

    @GET("api/payments/group/{groupId}/balances")
    Call<BalanceDataResponse> getBalances(@Path("groupId") String groupId);

    // Inbox
    @POST("api/inbox/send")
    Call<Void> sendNotification(@Body NotificationRequest request);

    @GET("api/inbox")
    Call<List<InboxEntity>> getNotifications();

    // DTOs
    class UserRequest {

        public String name;
        public String phone;
        public String password;
    }

    class AuthRequest {

        public String phone;
        public String password;
    }

    class AuthResponse {

        public String token;
        public String userId;
    }

    class RenameRequest {

        public String name;
    }

    class PaymentTargetRequest {

        public String bankBin;
        public String accountNo;
        public String accountName;
    }

    class GroupRequest {

        public String name;
        public List<String> memberIds;
    }

    class AddMemberRequest {

        public String groupId;
        public String userId;
    }

    class JoinRequest {

        public String inviteCode;
    }

    class MemberResponse {

        public String groupId;
        public String userId;
        public int position;
        public boolean active;
        public UserEntity user;
    }

    class BillRequest {

        public String groupId;
        public String title;
        public String payerId;
        public String mode;
        public long subtotal;
        public String participantsCsv;
        public int vatPercent;
        public int servicePercent;
        public int rounding;
        public String category;
        public String sharesCsv;
        public List<BillItemRequest> items;
    }

    class BillItemRequest {

        public int position;
        public String name;
        public long price;
        public String consumersCsv;
    }

    class BillDetailsResponse {

        public BillEntity bill;
        public List<BillItemEntity> items;
    }

    class MarkPaidRequest {

        public String billId;
    }

    class ConfirmPaymentRequest {

        public String billId;
        public String debtorId;
    }

    class DisputePaymentRequest {

        public String billId;
        public String debtorId;
    }

    class BalanceDataResponse {

        public List<BillDetailsResponse> bills;
        public List<MemberEntity> members;
        public List<DebtStatusEntity> debtStatuses;
    }

    class NotificationRequest {

        public String recipientId;
        public String fromId;
        public String message;
        public String billId;
        public String debtKey;
    }
}
