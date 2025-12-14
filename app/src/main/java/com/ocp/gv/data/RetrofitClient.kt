package com.ocp.gv.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonSerializer
import com.ocp.gv.data.api.HabitApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object RetrofitClient {
    private const val BASE_URL = "http://10.0.2.2:8000/api/v1/"

    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, JsonDeserializer { json, _, _ ->
            LocalDate.parse(json.asString)
        })
        .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { src, _, _ ->
            com.google.gson.JsonPrimitive(src.toString())
        })
        .registerTypeAdapter(LocalDateTime::class.java, JsonDeserializer { json, _, _ ->
            LocalDateTime.parse(json.asString, DateTimeFormatter.ISO_DATE_TIME)
        })
        .registerTypeAdapter(LocalDateTime::class.java, JsonSerializer<LocalDateTime> { src, _, _ ->
            com.google.gson.JsonPrimitive(src.format(DateTimeFormatter.ISO_DATE_TIME))
        })
        .create()

    private val httpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                },
            ).addInterceptor(
                Interceptor { chain ->
                    val originalRequest = chain.request()
                    val requestBuilder =
                        originalRequest
                            .newBuilder()
                            .header("accept", "application/json")
                            .header("X-API-KEY", "1234")
                    val request = requestBuilder.build()
                    chain.proceed(request)
                },
            ).build()

    private val retrofit =
        Retrofit
            .Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    val habitApi: HabitApi = retrofit.create(HabitApi::class.java)
}
