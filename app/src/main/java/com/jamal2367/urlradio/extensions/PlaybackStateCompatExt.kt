/*
 * MediaMetadataCompatExt.kt
 * Implements the MediaMetadataCompatExt extension methods
 * Useful extension methods for MediaMetadataCompatExt
 * Source: https://raw.githubusercontent.com/googlesamples/android-UniversalMusicPlayer/master/common/src/main/java/com/example/android/uamp/media/extensions/MediaMetadataCompatExt.kt
 *
 * This file is part of
 * URL Radio - Radio App for Android
 *
 * Copyright (c) 2015-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.extensions

import android.support.v4.media.session.PlaybackStateCompat

inline val PlaybackStateCompat.isActive
    get() = (state == PlaybackStateCompat.STATE_BUFFERING) ||
            (state == PlaybackStateCompat.STATE_PLAYING) ||
            (state == PlaybackStateCompat.STATE_FAST_FORWARDING) ||
            (state == PlaybackStateCompat.STATE_REWINDING)

inline val PlaybackStateCompat.isSkipToPreviousEnabled
    get() = actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS != 0L


inline val PlaybackStateCompat.stateName
    get() = when (state) {
        PlaybackStateCompat.STATE_NONE -> "STATE_NONE"
        PlaybackStateCompat.STATE_STOPPED -> "STATE_STOPPED"
        PlaybackStateCompat.STATE_PAUSED -> "STATE_PAUSED"
        PlaybackStateCompat.STATE_PLAYING -> "STATE_PLAYING"
        PlaybackStateCompat.STATE_FAST_FORWARDING -> "STATE_FAST_FORWARDING"
        PlaybackStateCompat.STATE_REWINDING -> "STATE_REWINDING"
        PlaybackStateCompat.STATE_BUFFERING -> "STATE_BUFFERING"
        PlaybackStateCompat.STATE_ERROR -> "STATE_ERROR"
        else -> "UNKNOWN_STATE"
    }
