package vn.nhom03.chiabill.data.remote;

import android.content.Context;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import vn.nhom03.chiabill.BuildConfig;
import vn.nhom03.chiabill.util.SessionManager;

public class ApiClient {

    private static ChiaBillApi api;

    public static ChiaBillApi getInstance(Context context) {
        if (api == null) {
            SessionManager session = new SessionManager(context);

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            Interceptor authInterceptor = chain -> {
                Request original = chain.request();
                String token = session.authToken();

                if (token == null) {
                    return chain.proceed(original);
                }

                Request request = original
                    .newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .method(original.method(), original.body())
                    .build();
                return chain.proceed(request);
            };

            OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(authInterceptor)
                .build();

            Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

            api = retrofit.create(ChiaBillApi.class);
        }
        return api;
    }
}
