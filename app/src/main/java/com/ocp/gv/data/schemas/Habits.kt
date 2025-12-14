package com.ocp.gv.data.schemas

import com.google.gson.annotations.SerializedName
import com.ocp.gv.data.models.ComparisonType
import com.ocp.gv.data.models.TargetFrequency
import com.ocp.gv.data.models.ValueType
import java.time.LocalDate
import java.time.LocalDateTime

data class PaginatedResponse<T>(
    val items: List<T>,
    val total: Int,
    val page: Int,
    @SerializedName("page_size")
    val pageSize: Int,
    @SerializedName("total_pages")
    val totalPages: Int,
)

// For creating a habit (POST body)
data class HabitCreateRequest(
    val name: String,
    val description: String? = null,
    @SerializedName("value_type")
    val valueType: ValueType,
    val unit: String? = null,
    val frequency: TargetFrequency? = null,
    @SerializedName("target_value")
    val targetValue: Double? = null,
    @SerializedName("target_min")
    val targetMin: Double? = null,
    @SerializedName("target_max")
    val targetMax: Double? = null,
    @SerializedName("comparison_type")
    val comparisonType: ComparisonType? = null,
    @SerializedName("start_date")
    val startDate: LocalDate? = null,
    @SerializedName("end_date")
    val endDate: LocalDate? = null,
    @SerializedName("is_required")
    val isRequired: Boolean = true,
    val color: String = "#4CAF50",
    val icon: String = "default",
)

// For updating (PATCH body) - all optional
data class HabitUpdateRequest(
    val name: String? = null,
    val description: String? = null,
    val unit: String? = null,
    val frequency: TargetFrequency? = null,
    @SerializedName("target_value")
    val targetValue: Double? = null,
    @SerializedName("target_min")
    val targetMin: Double? = null,
    @SerializedName("target_max")
    val targetMax: Double? = null,
    @SerializedName("comparison_type")
    val comparisonType: ComparisonType? = null,
    @SerializedName("start_date")
    val startDate: LocalDate? = null,
    @SerializedName("end_date")
    val endDate: LocalDate? = null,
    @SerializedName("is_required")
    val isRequired: Boolean? = null,
    val color: String? = null,
    val icon: String? = null,
    val active: Boolean? = null,
)

// What API returns (GET response)
data class HabitResponse(
    val id: Int,
    val name: String,
    val description: String?,
    @SerializedName("value_type")
    val valueType: ValueType,
    val unit: String?,
    val frequency: TargetFrequency?,
    @SerializedName("target_value")
    val targetValue: Double?,
    @SerializedName("target_min")
    val targetMin: Double?,
    @SerializedName("target_max")
    val targetMax: Double?,
    @SerializedName("comparison_type")
    val comparisonType: ComparisonType?,
    @SerializedName("start_date")
    val startDate: LocalDate?,
    @SerializedName("end_date")
    val endDate: LocalDate?,
    @SerializedName("is_required")
    val isRequired: Boolean,
    val color: String,
    val icon: String,
    @SerializedName("created_at")
    val createdAt: LocalDateTime,
    @SerializedName("updated_at")
    val updatedAt: LocalDateTime,
)
