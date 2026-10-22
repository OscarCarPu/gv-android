package com.gv.app.data.repository

import com.gv.app.domain.model.MergeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether gv-api is holding the app while it restores data after a failover.
 *
 * Any call can be the one that finds out, so the signal is process-wide: [safeApiCall] raises it
 * when an answer carries the `merge_pending` code, and the UI blocks on it until the server says
 * it is done. The report of what home had is read in the web app.
 */
object MergePending {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    fun seen() {
        _active.value = true
    }

    fun clear() {
        _active.value = false
    }
}

/**
 * Asks the server whether the hold is over. The semiprivate flavour has no token for the merge
 * endpoint, so it only offers a retry: the next call that is still refused raises the hold again.
 */
class MergeMonitor(
    private val fetch: suspend () -> ApiResult<MergeStatus>,
    private val semiprivate: Boolean,
) {
    suspend fun stillPending(): Boolean {
        if (semiprivate) return false
        return when (val r = fetch()) {
            is ApiResult.Success -> r.data.mode != MergeStatus.MODE_NORMAL
            is ApiResult.Failure -> true
        }
    }
}
