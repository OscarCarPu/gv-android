package com.gv.app.data.repository

import com.gv.app.data.api.ApiService
import com.gv.app.domain.model.AiUsage
import com.gv.app.domain.model.VozExecuteRequest
import com.gv.app.domain.model.VozExecuteResponse
import com.gv.app.domain.model.VozSuggestRequest
import com.gv.app.domain.model.VozSuggestResponse

/**
 * Live request/response repository for the Voz assistant. Unlike the offline-first
 * domain repositories, it does no Room caching or outbox queueing: suggestions,
 * executions, and usage are derived, online-only interactions.
 */
class VozRepository(private val api: ApiService) {

    suspend fun suggest(text: String, token: String?): ApiResult<VozSuggestResponse> =
        safeApiCall { api.assistantSuggest(VozSuggestRequest(text = text, token = token)) }

    suspend fun execute(token: String): ApiResult<VozExecuteResponse> =
        safeApiCall { api.assistantExecute(VozExecuteRequest(token = token)) }

    suspend fun usage(month: String): ApiResult<AiUsage> =
        safeApiCall { api.getAssistantUsage(month) }
}
