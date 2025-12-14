package com.ocp.gv.data.api

import com.ocp.gv.data.schemas.HabitResponse
import com.ocp.gv.data.schemas.PaginatedResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface HabitApi {
    @GET("habits")
    suspend fun getHabits(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
    ): PaginatedResponse<HabitResponse>
}
