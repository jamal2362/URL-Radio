/*
 * CollectionProvider.kt
 * Implements the CollectionProvider class
 * A CollectionProvider provides a list of stations as MediaMetadata items
 * Credit: https://github.com/googlesamples/android-MediaBrowserService/ (-> MusicProvider)
 *
 * This file is part of
 * URL Radio - Radio App for Android
 *
 * Copyright (c) 2015-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.collection

import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import com.jamal2367.urlradio.core.Collection
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.CollectionHelper

/**
 * CollectionProvider.class
 */
class CollectionProvider {


    /* Main class variables */
    private enum class State { NON_INITIALIZED, INITIALIZED }
    private var currentState = State.NON_INITIALIZED
    val stationListByName: MutableList<MediaBrowserCompat.MediaItem> = mutableListOf()


    /* Callback used by PlayerService */
    interface CollectionProviderCallback {
        fun onStationListReady(success: Boolean)
    }


    /* Return current state */
    fun isInitialized(): Boolean {
        return currentState == State.INITIALIZED
    }


    /* Gets list of stations and caches meta information in list of MediaMetaItems */
    fun retrieveMedia(context: Context, collection: Collection, stationListProviderCallback: CollectionProviderCallback) {
        if (currentState == State.INITIALIZED) {
            // already initialized, set callback immediately
            stationListProviderCallback.onStationListReady(true)
        } else {
            // sort station list by name
            val stationListSorted: List<Station> = collection.stations.sortedBy { it.name }
            stationListSorted.forEach { station ->
                stationListByName.add(CollectionHelper.buildStationMediaMetaItem(context, station))
            }
            // afterwards: update state and set callback
            currentState = State.INITIALIZED
            stationListProviderCallback.onStationListReady(true)
        }
    }


    /* Get first station as media item */
    fun getFirstStation(): MediaBrowserCompat.MediaItem? {
        return when (isInitialized() && stationListByName.isNotEmpty()) {
            true -> stationListByName.first()
            false -> null
        }
    }
}
