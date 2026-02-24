package com.gv.app.data.api

import com.gv.app.domain.model.Habit
import com.gv.app.domain.model.LogRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @GET("habits")
    suspend fun getHabits(@Query("date") date: String): List<Habit>

    @POST("habits/log")
    suspend fun logHabit(@Body request: LogRequest)
}
