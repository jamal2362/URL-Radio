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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.core.Station
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
    /** Set when a preview could not be started (HLS), consumed by the UI as a toast. */
    val previewUnsupportedEvent: Long? = null,
    val previewStartedEvent: Long? = null,
)

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
                    )
                }
            }

            query.startsWith("htt") -> {
                _state.update { it.copy(isSearching = true, showNoResults = false) }
                directInputCheck.checkStationAddress(getApplication(), query)
            }

            query.contains(" ") || query.length > 2 -> {
                _state.update { it.copy(isSearching = true, showNoResults = false) }
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
                )
            }

            query.startsWith("http") -> {
                _state.update { it.copy(isSearching = true, showNoResults = false) }
                directInputCheck.checkStationAddress(getApplication(), query)
            }

            else -> {
                _state.update { it.copy(isSearching = true, showNoResults = false) }
                radioBrowserSearch.searchStation(query, Keys.SEARCH_TYPE_BY_KEYWORD)
            }
        }
    }

    override fun onRadioBrowserSearchResults(results: Array<RadioBrowserResult>) {
        val stations = results.map { it.toStation() }
        publishResults(stations)
    }

    override fun onDirectInputCheck(stationList: MutableList<Station>) {
        publishResults(stationList)
    }

    private fun publishResults(stations: List<Station>) {
        _state.update {
            it.copy(
                results = stations,
                isSearching = false,
                showNoResults = stations.isEmpty(),
                selectedUuids = emptySet(),
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
        stopPreview()
        _state.value = SearchUiState()
    }

    override fun onCleared() {
        debounceJob?.cancel()
        radioBrowserSearch.stopSearchRequest()
        stopPreview()
    }
}
