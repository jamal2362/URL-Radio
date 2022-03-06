/*
 * PlayerState.kt
 * Implements the PlayerState class
 * A PlayerState holds parameters describing the state of the player part of the UI
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.ui

import android.os.Parcelable
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.annotations.Expose
import kotlinx.parcelize.Parcelize
import com.jamal2367.urlradio.Keys


/*
 * PlayerState class
 */
@Parcelize
data class PlayerState (@Expose var stationUuid: String = String(),
                        @Expose var playbackState: Int = PlaybackStateCompat.STATE_STOPPED,
                        @Expose var bottomSheetState: Int = BottomSheetBehavior.STATE_HIDDEN,
                        @Expose var sleepTimerState: Int = Keys.STATE_SLEEP_TIMER_STOPPED): Parcelable
