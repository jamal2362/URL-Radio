/*
 * RadioBrowserSearch.kt
 * Implements the RadioBrowserSearch class
 * A RadioBrowserSearch performs searches on the radio-browser.info database
 *
 * Migrated from Volley to OkHttp: OkHttp already ships with the app through
 * media3-datasource-okhttp and DirectInputCheck, so Volley was one HTTP stack too many.
 * A search that is still in flight is cancelled when a new one starts or when the dialog
 * closes, which the Volley queue only did wholesale.
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.search

import android.util.Log
import com.google.gson.GsonBuilder
import com.jamal2367.urlradio.BuildConfig
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.helpers.NetworkHelper
import com.jamal2367.urlradio.helpers.PreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit


/* radio-browser.info asks clients to identify themselves by name and version. */
private const val USER_AGENT_NAME = "URL-Radio"


/*
 * RadioBrowserSearch class
 */
class RadioBrowserSearch(private var radioBrowserSearchListener: RadioBrowserSearchListener) {


    /* Define log tag */
    private val tag: String = RadioBrowserSearch::class.java.simpleName


    /* Interface used to send back search results */
    interface RadioBrowserSearchListener {
        fun onRadioBrowserSearchResults(results: Array<RadioBrowserResult>) {
        }

        /* Every server failed - message says how, e.g. "de1.api.radio-browser.info: HTTP 503" */
        fun onRadioBrowserSearchError(message: String) {
        }
    }


    /* Main class variables */
    private var radioBrowserApi: String
    private val scope = CoroutineScope(SupervisorJob() + IO)
    private var searchJob: Job? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        // three servers are tried in turn, so each one gets less than the old 30 seconds
        .callTimeout(15, TimeUnit.SECONDS)
        .build()


    /* Init constructor */
    init {
        // get address of radio-browser.info api and update it in background
        radioBrowserApi = PreferencesHelper.loadRadioBrowserApiAddress()
        updateRadioBrowserApi()
    }


    /* Searches station(s) on radio-browser.info */
    fun searchStation(query: String, searchType: Int) {
        Log.v(tag, "Search - Querying $radioBrowserApi for: $query")

        // a newer query supersedes whatever is still running
        searchJob?.cancel()

        val path: String = when (searchType) {
            // CASE: single station search - by radio browser UUID
            Keys.SEARCH_TYPE_BY_UUID -> "/json/stations/byuuid/${query}"
            // CASE: multiple results search by search term
            // Stations radio-browser.info found unreachable are left out, and the most
            // listened-to come first instead of the order the database happens to return.
            else -> "/json/stations/search?name=" + URLEncoder.encode(query, "UTF-8") +
                "&hidebroken=true&order=clickcount&reverse=true"
        }
        // The chosen server first, then the others: radio-browser.info is down to very few
        // servers, and one that is gone or failing used to leave the search empty for good.
        val servers: List<String> = listOf(
            radioBrowserApi,
            Keys.RADIO_BROWSER_API_BASE,
            Keys.RADIO_BROWSER_API_DEFAULT,
        ).distinct()

        searchJob = scope.launch {
            var lastError = String()
            for (server in servers) {
                ensureActive()
                try {
                    val results: Array<RadioBrowserResult> = fetch("https://${server}${path}")
                    withContext(Main) {
                        radioBrowserSearchListener.onRadioBrowserSearchResults(results)
                    }
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = "$server: ${e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName}"
                    Log.w(tag, "Search failed on $server: $e")
                }
            }
            withContext(Main) {
                radioBrowserSearchListener.onRadioBrowserSearchError(lastError)
            }
        }
    }


    /* Runs one request - throws when the server cannot be reached or does not answer 200 OK */
    private fun fetch(requestUrl: String): Array<RadioBrowserResult> {
        val request = Request.Builder()
            .url(requestUrl)
            // The previous code read "$Keys.APPLICATION_NAME ...", which Kotlin
            // parses as Keys.toString() + ".APPLICATION_NAME", so radio-browser.info
            // was told the client was "com.jamal2367.urlradio.Keys@1a2b3c.APPLICATION_NAME".
            .header("User-Agent", "$USER_AGENT_NAME/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} ${response.message}".trim())
            val body: String = response.body.string()
            // an empty answer is an empty result, not an error
            return if (body.isBlank()) emptyArray() else createRadioBrowserResult(body)
        }
    }


    fun stopSearchRequest() {
        searchJob?.cancel()
        searchJob = null
    }


    /* Converts search result JSON string */
    private fun createRadioBrowserResult(result: String): Array<RadioBrowserResult> {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setDateFormat("M/d/yy hh:mm a")
        val gson = gsonBuilder.create()
        return gson.fromJson(result, Array<RadioBrowserResult>::class.java)
    }


    /* Updates the address of the radio-browser.info api */
    private fun updateRadioBrowserApi() {
        scope.launch {
            radioBrowserApi = NetworkHelper.getRadioBrowserServerSuspended()
        }
    }

}
