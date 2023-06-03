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
import android.util.Log
import android.webkit.URLUtil
import android.widget.Toast
import com.android.volley.RequestQueue
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.GregorianCalendar
import java.util.Locale


/*
 * DirectInputCheck class
 */
class DirectInputCheck(private var directInputCheckListener: DirectInputCheckListener) {

    /* Interface used to send back station list for checked */
    interface DirectInputCheckListener {
        fun onDirectInputCheck(stationList: MutableList<Station>) {
        }
    }


    /* Define log tag */
    private val TAG: String = DirectInputCheck::class.java.simpleName
    private val stationList: MutableList<Station> = mutableListOf()
    private var lastCheckedAddress: String = String()


    /* Main class variables */
    private lateinit var requestQueue: RequestQueue


    /* Searches station(s) on radio-browser.info */
    fun checkStationAddress(context: Context, query: String) {
        // check if valid URL
        if (URLUtil.isValidUrl(query)) {
            CoroutineScope(IO).launch {
                val contentType: String = NetworkHelper.detectContentType(query).type.lowercase(Locale.getDefault())
                Log.e(TAG, "contentType => $contentType") // todo remove when finished
                // CASE: playlist detected
                if (Keys.MIME_TYPES_M3U.contains(contentType) or
                    Keys.MIME_TYPES_PLS.contains(contentType)) {
                    // download playlist - up to 100 lines, with max. 200 characters
                    val lines = mutableListOf<String>()
                    val connection =
                        withContext(IO) {
                            URL(query).openConnection()
                        }
                    val reader = withContext(IO) {
                        connection.getInputStream()
                    }.bufferedReader()
                    reader.useLines { sequence ->
                        sequence.take(100).forEach { line ->
                            val trimmedLine = line.take(2000)
                            lines.add(trimmedLine)
                        }
                    }
                    Log.e(TAG, "Downloaded =>\n$lines") // todo remove when finished
                    // todo create station(s) and hand them over to adapter
                }
                // CASE: stream address detected
                else if (Keys.MIME_TYPES_MPEG.contains(contentType) or
                    Keys.MIME_TYPES_OGG.contains(contentType) or
                    Keys.MIME_TYPES_AAC.contains(contentType) or
                    Keys.MIME_TYPES_HLS.contains(contentType)) {
                    // create station and add to collection
                    val station = Station(name = query, streamUris = mutableListOf(query), streamContent = contentType, modificationDate = GregorianCalendar.getInstance().time)
                    if (lastCheckedAddress != query) {
                        stationList.add(station)
                        withContext(Main) {
                            directInputCheckListener.onDirectInputCheck(stationList)
                        }
                    }
                    lastCheckedAddress = query
                }
                // CASE: invalid address
                else {
                    withContext(Main) {
                        Toast.makeText(context, R.string.toastmessage_station_not_valid, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

}