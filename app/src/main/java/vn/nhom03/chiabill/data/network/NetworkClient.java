package vn.nhom03.chiabill.data.network;

import android.content.Context;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import vn.nhom03.chiabill.util.SessionManager;

public class NetworkClient {

    private static final String BASE_URL =
        vn.nhom03.chiabill.BuildConfig.BASE_URL;
    private static Retrofit retrofit = null;

    public static AuthApi getAuthApi(Context context) {
        if (retrofit == null) {
            SessionManager session = new SessionManager(context);
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            // logout and /me need the stored token; login/register simply have none yet.
            OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    String token = session.authToken();
                    if (token == null) return chain.proceed(original);
                    return chain.proceed(
                        original
                            .newBuilder()
                            .header("Authorization", "Bearer " + token)
                            .build()
                    );
                })
                .build();

            retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
        }
        return retrofit.create(AuthApi.class);
    }
}
