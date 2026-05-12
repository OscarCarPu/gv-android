package com.gv.app.data.api

import com.gv.app.domain.model.Account
import com.gv.app.domain.model.Category
import com.gv.app.domain.model.CreateAccountRequest
import com.gv.app.domain.model.CreateCategoryRequest
import com.gv.app.domain.model.CreateTransactionRequest
import com.gv.app.domain.model.HabitWithLog
import com.gv.app.domain.model.LogHabitRequest
import com.gv.app.domain.model.LogHabitResponse
import com.gv.app.domain.model.LoginRequest
import com.gv.app.domain.model.Overview
import com.gv.app.domain.model.TokenResponse
import com.gv.app.domain.model.Transaction
import com.gv.app.domain.model.TwoFactorRequest
import com.gv.app.domain.model.UpdateAccountRequest
import com.gv.app.domain.model.UpdateCategoryRequest
import com.gv.app.domain.model.UpdateTransactionRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
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

    @GET("finance/overview")
    suspend fun getFinanceOverview(): Response<Overview>

    @GET("finance/accounts")
    suspend fun listAccounts(): Response<List<Account>>

    @POST("finance/accounts")
    suspend fun createAccount(@Body request: CreateAccountRequest): Response<Account>

    @PUT("finance/accounts/{id}")
    suspend fun updateAccount(@Path("id") id: Int, @Body request: UpdateAccountRequest): Response<Account>

    @DELETE("finance/accounts/{id}")
    suspend fun deleteAccount(@Path("id") id: Int): Response<Unit>

    @GET("finance/categories")
    suspend fun listCategories(): Response<List<Category>>

    @POST("finance/categories")
    suspend fun createCategory(@Body request: CreateCategoryRequest): Response<Category>

    @PUT("finance/categories/{id}")
    suspend fun updateCategory(@Path("id") id: Int, @Body request: UpdateCategoryRequest): Response<Category>

    @DELETE("finance/categories/{id}")
    suspend fun deleteCategory(@Path("id") id: Int): Response<Unit>

    @GET("finance/transactions")
    suspend fun listTransactions(@Query("account_id") accountId: Int? = null): Response<List<Transaction>>

    @GET("finance/transactions/{id}")
    suspend fun getTransaction(@Path("id") id: Int): Response<Transaction>

    @POST("finance/transactions")
    suspend fun createTransaction(@Body request: CreateTransactionRequest): Response<Transaction>

    @PUT("finance/transactions/{id}")
    suspend fun updateTransaction(@Path("id") id: Int, @Body request: UpdateTransactionRequest): Response<Transaction>

    @DELETE("finance/transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: Int): Response<Unit>
}
