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


    /* Main class variables */
    private var lastCheckedAddress: String = String()


    /* Searches station(s) on radio-browser.info */
    fun checkStationAddress(context: Context, query: String) {
        // check if valid URL
        if (URLUtil.isValidUrl(query)) {
            val stationList: MutableList<Station> = mutableListOf()
            CoroutineScope(IO).launch {
                val contentType: String = NetworkHelper.detectContentType(query).type.lowercase(Locale.getDefault())
                // CASE: M3U playlist detected
                if (Keys.MIME_TYPES_M3U.contains(contentType)) {
                    val lines: List<String> = downloadPlaylist(query)
                    stationList.addAll(readM3uPlaylistContent(lines))
                }
                // CASE: PLS playlist detected
                else if (Keys.MIME_TYPES_PLS.contains(contentType)) {
                    val lines: List<String> = downloadPlaylist(query)
                    stationList.addAll(readPlsPlaylistContent(lines))
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
                    }
                    lastCheckedAddress = query
                }
                // CASE: invalid address
                else {
                    withContext(Main) {
                        Toast.makeText(context, R.string.toastmessage_station_not_valid, Toast.LENGTH_LONG).show()
                    }
                }
                // hand over station is to listener
                if (stationList.isNotEmpty()) {
                    withContext(Main) {
                        directInputCheckListener.onDirectInputCheck(stationList)
                    }
                }
            }
        }
    }


    /* Download playlist - up to 100 lines, with max. 200 characters */
    private fun downloadPlaylist(playlistUrlString: String): List<String> {
        val lines = mutableListOf<String>()
        val connection = URL(playlistUrlString).openConnection()
        val reader = connection.getInputStream().bufferedReader()
        reader.useLines { sequence ->
            sequence.take(100).forEach { line ->
                val trimmedLine = line.take(2000)
                lines.add(trimmedLine)
            }
        }
        return lines
    }


    /* Reads a m3u playlist and returns a list of stations */
    private fun readM3uPlaylistContent(playlist: List<String>): List<Station> {
        val stations: MutableList<Station> = mutableListOf()
        var name = String()
        var streamUri: String
        var contentType: String

        playlist.forEach { line ->
            // get name of station
            if (line.startsWith("#EXTINF:")) {
                name = line.substringAfter(",").trim()
            }
            // get stream uri and check mime type
            else if (line.isNotBlank() && !line.startsWith("#")) {
                streamUri = line.trim()
                // use the stream address as the name if no name is specified
                if (name.isEmpty()) {
                    name = streamUri
                }
                contentType = NetworkHelper.detectContentType(streamUri).type.lowercase(Locale.getDefault())
                // store station in list if mime type is supported
                if (contentType != Keys.MIME_TYPE_UNSUPPORTED) {
                    val station = Station(name = name, streamUris = mutableListOf(streamUri), streamContent = contentType, modificationDate = GregorianCalendar.getInstance().time)
                    stations.add(station)
                }
                // reset name for the next station - useful if playlist does not provide name(s)
                name = String()
            }
        }
        return stations
    }


    /* Reads a pls playlist and returns a list of stations */
    private fun readPlsPlaylistContent(playlist: List<String>): List<Station> {
        val stations: MutableList<Station> = mutableListOf()
        var name = String()
        var streamUri: String
        var contentType: String

        playlist.forEachIndexed { index, line ->
            // get stream uri and check mime type
            if (line.startsWith("File")) {
                streamUri = line.substringAfter("=").trim()
                contentType = NetworkHelper.detectContentType(streamUri).type.lowercase(Locale.getDefault())
                if (contentType != Keys.MIME_TYPE_UNSUPPORTED) {
                    // look for the matching station name
                    val number: String = line.substring(4 /* File */, line.indexOf("="))
                    val lineBeforeIndex: Int = index - 1
                    val lineAfterIndex: Int = index + 1
                    // first: check the line before
                    if (lineBeforeIndex >= 0) {
                        val lineBefore: String = playlist[lineBeforeIndex]
                        if (lineBefore.startsWith("Title$number")) {
                            name = lineBefore.substringAfter("=").trim()
                        }
                    }
                    // then: check the line after
                    if (name.isEmpty() && lineAfterIndex < playlist.size) {
                        val lineAfter: String = playlist[lineAfterIndex]
                        if (lineAfter.startsWith("Title$number")) {
                            name = lineAfter.substringAfter("=").trim()
                        }
                    }
                    // fallback: use stream uri as name
                    if (name.isEmpty()) {
                        name = streamUri
                    }
                    // add station
                    val station = Station(name = name, streamUris = mutableListOf(streamUri), streamContent = contentType, modificationDate = GregorianCalendar.getInstance().time)
                    stations.add(station)
                }
            }
        }
        return stations
    }

}