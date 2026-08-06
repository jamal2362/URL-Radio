package com.jamal2367.urlradio.helpers

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Carries snackbar text from anywhere in the app -- plain helper objects included, which have
 * no Activity, View or coroutine scope of their own to show a Snackbar through -- to the single
 * SnackbarHost the Compose UI owns. Mirrors [CollectionChanges]: a non-suspending emit any
 * thread can call, and a SharedFlow the UI layer collects.
 */
object UserMessages {

    // replay = 0: a message shown before a collector subscribed is stale by the time anyone
    // would see it. The buffer is what lets notify() stay non-suspending so it can be called
    // from anywhere, including plain helper objects with no coroutine scope of their own.
    private val _messages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun notify(message: String) {
        _messages.tryEmit(message)
    }
}
