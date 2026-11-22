/*
 * DirectInputCheck.kt
 * Implements the DirectInputCheck class
 * A DirectInputCheck checks if a station url is valid and returns station via a listener
 *
 * Every check reports back - stations, or why there are none - so the search dialog never
 * waits forever. A newer check supersedes one that is still running.
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-23 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.search

import android.webkit.URLUtil
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.CollectionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/*
 * DirectInputCheck class
 */
class DirectInputCheck(private var directInputCheckListener: DirectInputCheckListener) {

    /* Interface used to send back station list for checked */
    interface DirectInputCheckListener {
        fun onDirectInputCheck(stationList: MutableList<Station>) {
        }

        /* The address yielded no station - problem says why, detail is the technical reason */
        fun onDirectInputError(problem: CollectionHelper.UrlProblem?, detail: String) {
        }
    }


    /* Main class variables */
    private val scope = CoroutineScope(SupervisorJob() + IO)
    private var checkJob: Job? = null


    /* Checks whether the given address is a stream or a playlist */
    fun checkStationAddress(query: String) {
        checkJob?.cancel()
        if (!URLUtil.isValidUrl(query)) {
            // null: the address itself is malformed
            directInputCheckListener.onDirectInputError(null, query)
            return
        }
        checkJob = scope.launch {
            val result: CollectionHelper.UrlCheck = try {
                CollectionHelper.checkStationsFromUrl(query)
            } catch (e: Exception) {
                CollectionHelper.UrlCheck(
                    emptyList(),
                    CollectionHelper.UrlProblem.UNREACHABLE,
                    e.localizedMessage ?: e.javaClass.simpleName
                )
            }
            withContext(Main) {
                if (result.stations.isNotEmpty()) {
                    directInputCheckListener.onDirectInputCheck(result.stations.toMutableList())
                } else {
                    directInputCheckListener.onDirectInputError(result.problem, result.detail)
                }
            }
        }
    }


    /* Cancels a check that is still running */
    fun stopCheck() {
        checkJob?.cancel()
        checkJob = null
    }
}
