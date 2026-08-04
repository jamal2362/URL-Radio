/*
 * StationSearchViewModel.kt
 * Drives the "find station" dialog: radio-browser search, direct URL input and preview playback
 *
 * The preview player is owned here and released in onCleared, so it cannot outlive the
 * dialog. The old SearchResultAdapter attached a fresh lifecycle observer to the activity
 * on every preview and never removed any of them.
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

data class SearchUiState(
    val query: String = "",
    val results: List<Station> = emptyList(),
    val selected: Station? = null,
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

    /* Live input, debounced. The old dialog used a 100 ms Handler post. */
    fun onQueryChanged(query: String) {
        _state.update { it.copy(query = query) }
        debounceJob?.cancel()
        stopPreview()
        when {
            query.isEmpty() -> {
                _state.update {
                    it.copy(results = emptyList(), selected = null, isSearching = false, showNoResults = false)
                }
            }

            query.startsWith("htt") -> {
                _state.update { it.copy(isSearching = true, showNoResults = false) }
                directInputCheck.checkStationAddress(getApplication(), query)
            }

            query.contains(" ") || query.length > 2 -> {
                _state.update { it.copy(isSearching = true, showNoResults = false) }
                debounceJob = viewModelScope.launch {
                    delay(300)
                    radioBrowserSearch.searchStation(getApplication(), query, Keys.SEARCH_TYPE_BY_KEYWORD)
                }
            }
        }
    }

    fun onQuerySubmitted(query: String) {
        debounceJob?.cancel()
        stopPreview()
        when {
            query.isEmpty() -> _state.update {
                it.copy(results = emptyList(), selected = null, isSearching = false, showNoResults = false)
            }

            query.startsWith("http") -> {
                _state.update { it.copy(isSearching = true, showNoResults = false) }
                directInputCheck.checkStationAddress(getApplication(), query)
            }

            else -> {
                _state.update { it.copy(isSearching = true, showNoResults = false) }
                radioBrowserSearch.searchStation(getApplication(), query, Keys.SEARCH_TYPE_BY_KEYWORD)
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
                selected = null,
            )
        }
    }

    /* Tapping the selected entry again clears the selection, as before. */
    fun onResultTapped(station: Station) {
        val alreadySelected = _state.value.selected?.uuid == station.uuid
        if (alreadySelected) {
            stopPreview()
            _state.update { it.copy(selected = null) }
            return
        }
        _state.update { it.copy(selected = station) }
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
        _state.update { it.copy(previewStartedEvent = System.currentTimeMillis()) }
    }

    fun stopPreview() {
        previewPlayer?.release()
        previewPlayer = null
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
        super.onCleared()
        debounceJob?.cancel()
        radioBrowserSearch.stopSearchRequest()
        stopPreview()
    }
}
