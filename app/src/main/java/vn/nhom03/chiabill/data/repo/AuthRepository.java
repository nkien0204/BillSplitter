package vn.nhom03.chiabill.data.repo;

import vn.nhom03.chiabill.data.network.AuthApi.UserAuthResponse;

public interface AuthRepository {
    interface AuthCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    void register(String name, String phone, String password, AuthCallback<UserAuthResponse> callback);
    void login(String phone, String password, AuthCallback<UserAuthResponse> callback);
    void logout(AuthCallback<Void> callback);

    /** Kiểm tra token đã lưu. onError("INVALID_TOKEN") khi server từ chối; mã khác (mất mạng, 5xx) = chưa biết. */
    void me(AuthCallback<UserAuthResponse.UserInfo> callback);
}
