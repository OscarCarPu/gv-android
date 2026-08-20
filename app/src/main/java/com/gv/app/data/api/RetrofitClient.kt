package com.gv.app.data.api

import com.gv.app.BuildConfig
import com.gv.app.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    lateinit var tokenManager: TokenManager

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Don't leak request/response bodies (incl. tokens) in release builds.
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    /** Bearer token in, 401 out: the single place that knows how this API is authenticated. */
    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        val isLoginEndpoint = path.endsWith("/login") || path.endsWith("/login/2fa")

        val newRequest = if (!isLoginEndpoint) {
            val token = tokenManager.tokenFlow.value
            if (token != null) {
                request.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else request
        } else request

        val response = chain.proceed(newRequest)

        if (response.code == 401 && !isLoginEndpoint) {
            tokenManager.clearToken()
        }

        response
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Client for long-lived server-sent-event streams (`/calendar/stream`).
     *
     * Separate from [okHttpClient] for two reasons, both of which otherwise break the stream:
     * the read timeout is disabled, because a healthy stream is silent for 25 seconds between
     * keep-alives; and the logging interceptor is left out, because at `BODY` level it buffers
     * the whole response body before handing it on, which for an endless body never returns.
     */
    val streamClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
