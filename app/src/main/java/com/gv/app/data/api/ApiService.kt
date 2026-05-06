package com.gv.app.data.api

import com.gv.app.domain.model.HabitWithLog
import com.gv.app.domain.model.LogHabitRequest
import com.gv.app.domain.model.LogHabitResponse
import com.gv.app.domain.model.LoginRequest
import com.gv.app.domain.model.TokenResponse
import com.gv.app.domain.model.TwoFactorRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @POST("login/2fa")
    suspend fun login2fa(@Body request: TwoFactorRequest): Response<TokenResponse>

    @GET("habits")
    suspend fun getHabits(@Query("date") date: String): Response<List<HabitWithLog>>

    @POST("habits/log")
    suspend fun logHabit(@Body request: LogHabitRequest): Response<LogHabitResponse>

    @DELETE("habits/{id}")
    suspend fun deleteHabit(@Path("id") id: Int): Response<Unit>
}
