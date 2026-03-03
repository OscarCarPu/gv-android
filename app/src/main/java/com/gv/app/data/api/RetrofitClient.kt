package com.gv.app.data.api

import com.gv.app.BuildConfig
import com.gv.app.data.local.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    lateinit var tokenManager: TokenManager

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
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
            .addInterceptor(loggingInterceptor)
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
