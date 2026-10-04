package vn.nhom03.chiabill.data.repo;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.nhom03.chiabill.data.network.AuthApi;
import vn.nhom03.chiabill.data.network.AuthApi.ApiError;
import vn.nhom03.chiabill.data.network.AuthApi.MeResponse;
import vn.nhom03.chiabill.data.network.AuthApi.UserAuthResponse;
import vn.nhom03.chiabill.data.network.AuthApi.UserLoginRequest;
import vn.nhom03.chiabill.data.network.AuthApi.UserRegisterRequest;
import vn.nhom03.chiabill.data.network.NetworkClient;

public class RemoteAuthRepository implements AuthRepository {

    private static final String TAG = "AuthRepo";
    private final AuthApi authApi;
    private final Gson gson = new Gson();

    public RemoteAuthRepository(Context context) {
        this.authApi = NetworkClient.getAuthApi(context.getApplicationContext());
    }

    private String parseErrorMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String errorJson = response.errorBody().string();
                ApiError apiError = gson.fromJson(errorJson, ApiError.class);
                if (apiError != null && apiError.error != null) {
                    return apiError.error.code;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing error body: " + e.getMessage());
        }
        return "UNKNOWN_ERROR";
    }

    @Override
    public void register(
        String name,
        String phone,
        String password,
        AuthCallback<UserAuthResponse> callback
    ) {
        Log.d(TAG, "Attempting registration for: " + phone);
        UserRegisterRequest request = new UserRegisterRequest(
            name,
            phone,
            password
        );
        authApi.register(request).enqueue(
            new Callback<UserAuthResponse>() {
                @Override
                public void onResponse(
                    Call<UserAuthResponse> call,
                    Response<UserAuthResponse> response
                ) {
                    if (response.isSuccessful() && response.body() != null) {
                        Log.d(TAG, "Registration successful for: " + phone);
                        callback.onSuccess(response.body());
                    } else {
                        String errorCode = parseErrorMessage(response);
                        Log.e(
                            TAG,
                            "Registration failed with code: " + errorCode
                        );
                        callback.onError(errorCode);
                    }
                }

                @Override
                public void onFailure(
                    Call<UserAuthResponse> call,
                    Throwable t
                ) {
                    Log.e(
                        TAG,
                        "Registration network failure: " + t.getMessage()
                    );
                    callback.onError("NETWORK_ERROR");
                }
            }
        );
    }

    @Override
    public void login(
        String phone,
        String password,
        AuthCallback<UserAuthResponse> callback
    ) {
        Log.d(TAG, "Attempting login for: " + phone);
        UserLoginRequest request = new UserLoginRequest(phone, password);
        authApi.login(request).enqueue(
            new Callback<UserAuthResponse>() {
                @Override
                public void onResponse(
                    Call<UserAuthResponse> call,
                    Response<UserAuthResponse> response
                ) {
                    if (response.isSuccessful() && response.body() != null) {
                        Log.d(TAG, "Login successful for: " + phone);
                        callback.onSuccess(response.body());
                    } else {
                        String errorCode = parseErrorMessage(response);
                        Log.e(TAG, "Login failed with code: " + errorCode);
                        callback.onError(errorCode);
                    }
                }

                @Override
                public void onFailure(
                    Call<UserAuthResponse> call,
                    Throwable t
                ) {
                    Log.e(TAG, "Login network failure: " + t.getMessage());
                    callback.onError("NETWORK_ERROR");
                }
            }
        );
    }

    @Override
    public void logout(AuthCallback<Void> callback) {
        Log.d(TAG, "Attempting logout...");
        authApi.logout().enqueue(
            new Callback<Void>() {
                @Override
                public void onResponse(
                    Call<Void> call,
                    Response<Void> response
                ) {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Logout successful");
                        callback.onSuccess(null);
                    } else {
                        Log.e(TAG, "Logout failed: " + response.code());
                        callback.onError("Logout failed: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Log.e(TAG, "Logout network failure: " + t.getMessage());
                    callback.onError("Network error: " + t.getMessage());
                }
            }
        );
    }

    @Override
    public void me(AuthCallback<UserAuthResponse.UserInfo> callback) {
        authApi.me().enqueue(
            new Callback<MeResponse>() {
                @Override
                public void onResponse(
                    Call<MeResponse> call,
                    Response<MeResponse> response
                ) {
                    if (response.isSuccessful() && response.body() != null) {
                        callback.onSuccess(response.body().user);
                    } else if (response.code() == 401) {
                        callback.onError("INVALID_TOKEN");
                    } else {
                        callback.onError("SERVER_ERROR");
                    }
                }

                @Override
                public void onFailure(Call<MeResponse> call, Throwable t) {
                    callback.onError("NETWORK_ERROR");
                }
            }
        );
    }
}
