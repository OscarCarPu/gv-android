package com.gv.app.data.api

import com.gv.app.domain.model.LoginRequest
import com.gv.app.domain.model.TokenResponse
import com.gv.app.domain.model.TwoFactorRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @POST("login/2fa")
    suspend fun login2fa(@Body request: TwoFactorRequest): Response<TokenResponse>
}
