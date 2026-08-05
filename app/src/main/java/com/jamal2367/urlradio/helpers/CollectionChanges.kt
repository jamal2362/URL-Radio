/*
 * CollectionChanges.kt
 * In-process signal that the stored collection was rewritten
 *
 * Replaces the LocalBroadcastManager broadcast that used to carry
 * Keys.ACTION_COLLECTION_CHANGED. LocalBroadcastManager is deprecated: it routes an
 * app-internal event through an Intent, which is machinery meant for crossing process
 * boundaries, and the library recommends an observable value instead.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.helpers

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Date

/**
 * Announces every write to the collection file, carrying the modification date that write was
 * stamped with. Collectors compare it against the date of the copy they hold and reload when
 * the file has moved ahead of them.
 */
object CollectionChanges {

    // replay = 0 keeps the semantics of the broadcast this replaces: whoever subscribes later
    // is not handed a change from before they existed -- they have just read the collection
    // themselves. The one-slot buffer is what lets notifyChanged stay non-suspending, so it
    // can be called from the same places the broadcast was sent from.
    private val _events = MutableSharedFlow<Date>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<Date> = _events.asSharedFlow()

    fun notifyChanged(modificationDate: Date) {
        _events.tryEmit(modificationDate)
    }
}
