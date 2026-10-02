package vn.nhom03.chiabill.data.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("api/auth/register")
    Call<UserAuthResponse> register(@Body UserRegisterRequest request);

    @POST("api/auth/login")
    Call<UserAuthResponse> login(@Body UserLoginRequest request);

    @POST("api/auth/logout")
    Call<Void> logout();

    class UserRegisterRequest {

        public String name;
        public String phone;
        public String password;

        public UserRegisterRequest(String name, String phone, String password) {
            this.name = name;
            this.phone = phone;
            this.password = password;
        }
    }

    class UserLoginRequest {

        public String phone;
        public String password;

        public UserLoginRequest(String phone, String password) {
            this.phone = phone;
            this.password = password;
        }
    }

    class UserAuthResponse {

        public String token;
        public UserInfo user;

        public static class UserInfo {

            public String id;
            public String name;
            public String phone;
        }
    }

    class ApiError {

        public ErrorDetails error;

        public static class ErrorDetails {

            public String code;
            public String message;
        }
    }
}
