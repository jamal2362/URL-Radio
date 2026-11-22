/*
 * StationSearchViewModel.kt
 * Drives the "find station" dialog: radio-browser search, direct URL input and preview playback
 *
 * The preview player is owned here and released in onCleared, so it cannot outlive the
 * dialog. The old SearchResultAdapter attached a fresh lifecycle observer to the activity
 * on every preview and never removed any of them.
 *
 * Selection and preview are two separate gestures: a tap ticks a station off the list, a long
 * press auditions it. Both dialogs that pick stations share the preview player kept here.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.ui.dialogs

import android.app.Application
import android.webkit.URLUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.CollectionHelper
import com.jamal2367.urlradio.search.DirectInputCheck
import com.jamal2367.urlradio.search.RadioBrowserResult
import com.jamal2367.urlradio.search.RadioBrowserSearch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class SearchUiState(
    val query: String = "",
    val results: List<Station> = emptyList(),
    /** UUIDs of every station the user ticked. Several stations can be added in one go. */
    val selectedUuids: Set<String> = emptySet(),
    /** UUID of the station currently being auditioned, or empty. */
    val previewUuid: String = "",
    val isSearching: Boolean = false,
    val showNoResults: Boolean = false,
    /** Set when a search or an address check failed - shown in the dialog with its reason. */
    val error: SearchError? = null,
    /** Set when a preview could not be started (HLS), consumed by the UI as a toast. */
    val previewUnsupportedEvent: Long? = null,
    val previewStartedEvent: Long? = null,
)

/** What went wrong, and the technical reason as reported by the network or the server. */
data class SearchError(val kind: Kind, val detail: String) {
    enum class Kind {
        /** radio-browser.info could not be reached or answered with an error. */
        SEARCH_FAILED,
        /** The text starts like an address but is not a valid one. */
        INVALID_ADDRESS,
        /** The address could not be reached, or did not answer with 200 OK. */
        ADDRESS_UNREACHABLE,
        /** The address answered with something that is neither a stream nor a playlist. */
        NOT_AUDIO,
        /** A playlist without a single playable entry. */
        EMPTY_PLAYLIST,
    }
}

/* How long typing has to pause before an address is checked - every check opens a connection. */
private const val ADDRESS_CHECK_DELAY_MS = 800L

class StationSearchViewModel(application: Application) : AndroidViewModel(application),
    RadioBrowserSearch.RadioBrowserSearchListener,
    DirectInputCheck.DirectInputCheckListener {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val radioBrowserSearch = RadioBrowserSearch(this)
    private val directInputCheck = DirectInputCheck(this)

    private var previewPlayer: ExoPlayer? = null
    private var debounceJob: Job? = null

    /* Live input, debounced. The old dialog used a 100 Ms Handler post. */
    fun onQueryChanged(query: String) {
        _state.update { it.copy(query = query) }
        debounceJob?.cancel()
        stopPreview()
        when {
            query.isEmpty() -> {
                _state.update {
                    it.copy(
                        results = emptyList(),
                        selectedUuids = emptySet(),
                        isSearching = false,
                        showNoResults = false,
                        error = null,
                    )
                }
            }

            query.startsWith("htt") -> {
                directInputCheck.stopCheck()
                // A half-typed address is not worth a connection, nor an error message:
                // it is only checked once it is a valid address and typing has paused.
                if (!URLUtil.isValidUrl(query)) {
                    _state.update { it.copy(isSearching = false, showNoResults = false, error = null) }
                    return
                }
                _state.update { it.copy(isSearching = true, showNoResults = false, error = null) }
                debounceJob = viewModelScope.launch {
                    delay(ADDRESS_CHECK_DELAY_MS.milliseconds)
                    directInputCheck.checkStationAddress(query)
                }
            }

            query.contains(" ") || query.length > 2 -> {
                _state.update { it.copy(isSearching = true, showNoResults = false, error = null) }
                debounceJob = viewModelScope.launch {
                    delay(300.milliseconds)
                    radioBrowserSearch.searchStation(query, Keys.SEARCH_TYPE_BY_KEYWORD)
                }
            }
        }
    }

    fun onQuerySubmitted(query: String) {
        debounceJob?.cancel()
        stopPreview()
        when {
            query.isEmpty() -> _state.update {
                it.copy(
                    results = emptyList(),
                    selectedUuids = emptySet(),
                    isSearching = false,
                    showNoResults = false,
                    error = null,
                )
            }

            query.startsWith("http") -> {
                _state.update { it.copy(isSearching = true, showNoResults = false, error = null) }
                directInputCheck.checkStationAddress(query)
            }

            else -> {
                _state.update { it.copy(isSearching = true, showNoResults = false, error = null) }
                radioBrowserSearch.searchStation(query, Keys.SEARCH_TYPE_BY_KEYWORD)
            }
        }
    }

    override fun onRadioBrowserSearchResults(results: Array<RadioBrowserResult>) {
        val stations = results.map { it.toStation() }
        publishResults(stations)
    }

    override fun onRadioBrowserSearchError(message: String) {
        publishError(SearchError(SearchError.Kind.SEARCH_FAILED, message))
    }

    override fun onDirectInputCheck(stationList: MutableList<Station>) {
        publishResults(stationList)
    }

    override fun onDirectInputError(problem: CollectionHelper.UrlProblem?, detail: String) {
        val kind: SearchError.Kind = when (problem) {
            null -> SearchError.Kind.INVALID_ADDRESS
            CollectionHelper.UrlProblem.UNREACHABLE -> SearchError.Kind.ADDRESS_UNREACHABLE
            CollectionHelper.UrlProblem.NOT_AUDIO -> SearchError.Kind.NOT_AUDIO
            CollectionHelper.UrlProblem.EMPTY_PLAYLIST -> SearchError.Kind.EMPTY_PLAYLIST
        }
        publishError(SearchError(kind, detail))
    }

    /* Ends the search with an error instead of results - the list is emptied. */
    private fun publishError(error: SearchError) {
        _state.update {
            it.copy(
                results = emptyList(),
                isSearching = false,
                showNoResults = false,
                selectedUuids = emptySet(),
                error = error,
            )
        }
    }

    private fun publishResults(stations: List<Station>) {
        _state.update {
            it.copy(
                results = stations,
                isSearching = false,
                showNoResults = stations.isEmpty(),
                selectedUuids = emptySet(),
                error = null,
            )
        }
    }

    /* ---- selection ---- */

    /* A tap ticks a station, tapping it again unticks it. */
    fun toggleSelection(station: Station) {
        _state.update { current ->
            val selected = current.selectedUuids
            current.copy(
                selectedUuids = if (station.uuid in selected) selected - station.uuid
                else selected + station.uuid
            )
        }
    }

    fun selectAll() {
        _state.update { it.copy(selectedUuids = it.results.map { station -> station.uuid }.toSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedUuids = emptySet()) }
    }

    /* ---- preview ---- */

    /*
     * A long press auditions a station without adding it. Pressing the station that is already
     * being auditioned stops the preview again.
     */
    fun togglePreview(station: Station) {
        if (_state.value.previewUuid == station.uuid) {
            stopPreview()
            return
        }
        startPreview(station)
    }

    private fun startPreview(station: Station) {
        stopPreview()
        val uri = station.getStreamUri()
        if (uri.contains(".m3u8")) {
            // HLS previews were never supported here
            _state.update { it.copy(previewUnsupportedEvent = System.currentTimeMillis()) }
            return
        }
        previewPlayer = ExoPlayer.Builder(getApplication()).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            play()
        }
        _state.update {
            it.copy(previewUuid = station.uuid, previewStartedEvent = System.currentTimeMillis())
        }
    }

    fun stopPreview() {
        previewPlayer?.release()
        previewPlayer = null
        if (_state.value.previewUuid.isNotEmpty()) {
            _state.update { it.copy(previewUuid = "") }
        }
    }

    fun consumeEvents() {
        _state.update { it.copy(previewUnsupportedEvent = null, previewStartedEvent = null) }
    }

    /* Called when the dialog closes, whether by add, cancel or dismiss. */
    fun reset() {
        debounceJob?.cancel()
        radioBrowserSearch.stopSearchRequest()
        directInputCheck.stopCheck()
        stopPreview()
        _state.value = SearchUiState()
    }

    override fun onCleared() {
        debounceJob?.cancel()
        radioBrowserSearch.stopSearchRequest()
        directInputCheck.stopCheck()
        stopPreview()
    }
}
