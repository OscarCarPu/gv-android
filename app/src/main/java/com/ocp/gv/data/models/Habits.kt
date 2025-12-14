package com.ocp.gv.data.models

import com.google.gson.annotations.SerializedName
import java.time.LocalDate
import java.time.LocalDateTime

enum class ValueType {
    @SerializedName("boolean") BOOLEAN,
    @SerializedName("numeric") NUMERIC,
}

enum class TargetFrequency {
    @SerializedName("daily") DAILY,
    @SerializedName("weekly") WEEKLY,
    @SerializedName("monthly") MONTHLY,
}

enum class ComparisonType {
    @SerializedName("equals") EQUALS,
    @SerializedName("greater_than") GREATER_THAN,
    @SerializedName("less_than") LESS_THAN,
    @SerializedName("greater_equal_than") GREATER_EQUAL_THAN,
    @SerializedName("less_equal_than") LESS_EQUAL_THAN,
    @SerializedName("in_range") IN_RANGE,
}

data class Habit(
    val id: Int,
    val name: String,
    val description: String,
    val value_type: ValueType,
    val unit: String,
    val frequency: TargetFrequency,
    val target_value: Double,
    val target_min: Double,
    var target_max: Double,
    val comparison_type: ComparisonType,
    val start_date: LocalDate,
    val end_date: LocalDate,
    val is_required: Boolean,
    val color: String,
    val icon: String,
    val created_at: LocalDateTime,
    val updated_at: LocalDateTime,
)

data class HabitLog(
    val id: Int,
    val habit_id: Int,
    val log_date: LocalDate,
    val value: Double,
    val created_at: LocalDateTime,
    val updated_at: LocalDateTime,
)
