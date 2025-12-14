package com.ocp.gv.data.mappers

import com.ocp.gv.data.models.ComparisonType
import com.ocp.gv.data.models.Habit
import com.ocp.gv.data.models.TargetFrequency
import com.ocp.gv.data.schemas.HabitResponse
import java.time.LocalDate

fun HabitResponse.toDomain() =
    Habit(
        id = id,
        name = name,
        description = description ?: "",
        value_type = valueType,
        unit = unit ?: "",
        frequency = frequency ?: TargetFrequency.DAILY,
        target_value = targetValue ?: 0.0,
        target_min = targetMin ?: 0.0,
        target_max = targetMax ?: 0.0,
        comparison_type = comparisonType ?: ComparisonType.EQUALS,
        start_date = startDate ?: LocalDate.MIN,
        end_date = endDate ?: LocalDate.MAX,
        is_required = isRequired,
        color = color,
        icon = icon,
        created_at = createdAt,
        updated_at = updatedAt,
    )
