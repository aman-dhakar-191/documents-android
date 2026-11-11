package io.github.amandhakar.documents.ui

import kotlinx.coroutines.channels.Channel

/**
 * Requests handled one at a time, where a newer request replaces any older one still waiting.
 *
 * Used for text selection while a finger drags: lookups are slower than the finger moves, so
 * queueing every position would make the selection fall further and further behind. Skipping the
 * ones in between keeps it close to the finger, and the newest position is always handled last.
 */
class LatestRequests<T> {
    private val channel = Channel<T>(Channel.CONFLATED)

    /** Adds a request, replacing one that hasn't been started yet. Safe to call from any thread. */
    fun submit(request: T) {
        channel.trySend(request)
    }

    /** Handles requests, one at a time, until [close] is called. */
    suspend fun process(handler: suspend (T) -> Unit) {
        for (request in channel) handler(request)
    }

    /** Ends [process] once the requests already received have been handled. */
    fun close() {
        channel.close()
    }
}
