/*
 * DirectInputCheck.kt
 * Implements the DirectInputCheck class
 * A DirectInputCheck checks if a station url is valid and returns station via a listener
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-23 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.search

import android.content.Context
import android.webkit.URLUtil
import android.widget.Toast
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.CollectionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.GregorianCalendar


data class IcecastMetadata(
    val title: String?
)
/*
 * DirectInputCheck class
 */
class DirectInputCheck(private var directInputCheckListener: DirectInputCheckListener) {

    /* Interface used to send back station list for checked */
    interface DirectInputCheckListener {
        fun onDirectInputCheck(stationList: MutableList<Station>) {
        }
    }


    /* Main class variables */
    private var lastCheckedAddress: String = String()


    /* Searches station(s) on radio-browser.info */
    fun checkStationAddress(context: Context, query: String) {
        // check if valid URL
        if (URLUtil.isValidUrl(query)) {
            val stationList: MutableList<Station> = mutableListOf()
            CoroutineScope(IO).launch {
                stationList.addAll(CollectionHelper.createStationsFromUrl(query, lastCheckedAddress))
                lastCheckedAddress = query
                withContext(Main) {
                    if (stationList.isNotEmpty()) {
                        // hand over station is to listener
                        directInputCheckListener.onDirectInputCheck(stationList)
                    } else {
                        // invalid address
                        Toast.makeText(context, R.string.toastmessage_station_not_valid, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }


    private suspend fun extractIcecastMetadata(streamUri: String): IcecastMetadata {
        return withContext(IO) {
            // make an HTTP request at the stream URL to get Icecast metadata.
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(streamUri)
                .build()

            val response = client.newCall(request).execute()
            val icecastHeaders = response.headers

            // analyze the Icecast metadata and extract information like title, description, bitrate, etc.
            val title = icecastHeaders["icy-name"]

            IcecastMetadata(title?.takeIf { it.isNotEmpty() } ?: streamUri)
        }
    }


    private suspend fun updateStationWithIcecastMetadata(station: Station, icecastMetadata: IcecastMetadata) {
        withContext(Dispatchers.Default) {
            station.name = icecastMetadata.title.toString()
        }
    }

    suspend fun processIcecastStream(streamUri: String, stationList: MutableList<Station>) {
        val icecastMetadata = extractIcecastMetadata(streamUri)
        val station = Station(name = icecastMetadata.title.toString(), streamUris = mutableListOf(streamUri), modificationDate = GregorianCalendar.getInstance().time)
        updateStationWithIcecastMetadata(station, icecastMetadata)
        // create station and add to collection
        if (lastCheckedAddress != streamUri) {
            stationList.add(station)
        }
        lastCheckedAddress = streamUri
    }

}