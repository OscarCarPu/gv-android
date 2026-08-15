package com.gv.app.data.repository

import com.gv.app.data.sync.ConnectivityObserver

/**
 * The one place that decides whether a write is allowed.
 *
 * The app is **online-first, offline read-only**: it talks to the server directly, exactly
 * like gv-web does, and the Room database is only a read cache. When there is no connection
 * the cache is still served for reading, but every write is refused up front instead of being
 * queued.
 *
 * That is a deliberate reversal of the previous write-behind outbox. Replaying queued mutations
 * meant the client had to guess how to reconcile them — remapping temporary ids onto server
 * ids, merging conflicting edits, deciding which failures were permanent — and each of those
 * guesses was a way for the UI to disagree with the server. Refusing the write is worse for the
 * three minutes a year you edit a task in a tunnel, and better every other minute, because what
 * the screen shows is what the server has.
 *
 * Repositories call [requireOnline] as the first line of every mutating function, so the rule
 * cannot be bypassed by a screen that forgets to disable a button.
 */
class OnlineGate(private val connectivity: ConnectivityObserver) {

    fun isOnline(): Boolean = connectivity.isOnline()

    /**
     * Returns a failure to hand straight back to the caller when offline, or null to proceed.
     *
     * Usage: `requireOnline()?.let { return it }`
     */
    fun requireOnline(): ApiResult.Failure? =
        if (connectivity.isOnline()) null else ApiResult.offline()
}
