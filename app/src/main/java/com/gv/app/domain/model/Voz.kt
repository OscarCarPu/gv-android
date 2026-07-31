package com.gv.app.domain.model

// DTOs for the "Voz" assistant. Field names are snake_case to match the API
// JSON via Gson defaults, exactly like the other domain models.

// ---- POST assistant/suggest ----
// First turn sends only text; a feedback round resends the opaque token from the
// prior suggestion plus the refinement in text.
data class VozSuggestRequest(
    val text: String,
    val token: String? = null,
)

data class VozSuggestResponse(
    val kind: String,               // read | write | reject  (see VozKind)
    val explanation: String,        // plain-language description
    val query: String,              // read: the SELECT; write: a description; reject: ""
    val warning: String? = null,    // e.g. destructive-write notice
    val token: String? = null,      // opaque signed token to echo on execute (absent for reject)
)

// ---- POST assistant/execute ----
data class VozExecuteRequest(
    val token: String,
)

data class VozExecuteResponse(
    val kind: String,               // read | write
    val summary: String,            // read: readable answer; write: confirmation
    val row_count: Int? = null,     // read only
)

enum class VozKind(val wire: String) {
    READ("read"), WRITE("write"), REJECT("reject");

    companion object {
        fun from(s: String): VozKind = entries.firstOrNull { it.wire == s } ?: REJECT
    }
}

// ---- GET assistant/usage?month=YYYY-MM ----
data class AiUsageDay(
    val date: String,        // YYYY-MM-DD
    val cost_usd: String,    // decimal string; format via BigDecimal, never Float
    val count: Int = 0,
)

data class AiUsage(
    val month: String,               // YYYY-MM
    val currency: String,            // USD
    val total_cost_usd: String,      // decimal string
    val total_input_tokens: Long,
    val total_output_tokens: Long,
    val interaction_count: Int,
    val by_day: List<AiUsageDay> = emptyList(),
)
