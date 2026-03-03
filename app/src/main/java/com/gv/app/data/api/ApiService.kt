package com.gv.app.data.api

import com.gv.app.domain.model.Habit
import com.gv.app.domain.model.LogRequest
import com.gv.app.domain.model.LoginRequest
import com.gv.app.domain.model.TokenResponse
import com.gv.app.domain.model.TwoFactorRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @GET("habits")
    suspend fun getHabits(@Query("date") date: String): List<Habit>

    @POST("habits/log")
    suspend fun logHabit(@Body request: LogRequest)

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @POST("login/2fa")
    suspend fun login2fa(@Body request: TwoFactorRequest): Response<TokenResponse>
}
