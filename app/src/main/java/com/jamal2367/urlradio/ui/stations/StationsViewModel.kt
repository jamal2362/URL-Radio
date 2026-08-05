/*
 * StationsViewModel.kt
 * Single source of truth for the station collection and the list UI state
 *
 * Replaces CollectionViewModel plus the collection handling that was duplicated between
 * PlayerFragment and CollectionAdapter. There used to be two CollectionViewModel instances
 * (one fragment scoped, one activity scoped), each loading the collection and each
 * registering its own broadcast receiver.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.ui.stations

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.core.Collection
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.CollectionHelper
import com.jamal2367.urlradio.helpers.FileHelper
import com.jamal2367.urlradio.helpers.NetworkHelper
import com.jamal2367.urlradio.helpers.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class StationsViewModel(application: Application) : AndroidViewModel(application) {

    private val _collection = MutableStateFlow(Collection())
    val collection: StateFlow<Collection> = _collection.asStateFlow()

    /** The station whose inline editor is open, or empty. Persisted like before. */
    private val _expandedStationUuid = MutableStateFlow(PreferencesHelper.loadStationListStreamUuid())
    val expandedStationUuid: StateFlow<String> = _expandedStationUuid.asStateFlow()

    val editStationsEnabled: StateFlow<Boolean> =
        PreferencesHelper.preferenceFlow(Keys.PREF_EDIT_STATIONS) {
            PreferencesHelper.loadEditStationsEnabled()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreferencesHelper.loadEditStationsEnabled())

    val editStreamUrisEnabled: StateFlow<Boolean> =
        PreferencesHelper.preferenceFlow(Keys.PREF_EDIT_STREAMS_URIS) {
            PreferencesHelper.loadEditStreamUrisEnabled()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreferencesHelper.loadEditStreamUrisEnabled())

    val hasActiveDownloads: StateFlow<Boolean> =
        PreferencesHelper.preferenceFlow(Keys.PREF_ACTIVE_DOWNLOADS) {
            PreferencesHelper.loadActiveDownloads() != Keys.ACTIVE_DOWNLOADS_EMPTY
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * True only for a genuinely empty, never-used collection. A user who deleted their last
     * station keeps the normal (empty) list rather than being sent back to onboarding --
     * this mirrors the old toggleOnboarding() condition exactly.
     */
    val showOnboarding: StateFlow<Boolean> = MutableStateFlow(false).also { flow ->
        viewModelScope.launch {
            collection.collect { c ->
                flow.value = c.stations.isEmpty() && PreferencesHelper.loadCollectionSize() <= 0
            }
        }
    }.asStateFlow()

    private var lastExportedSize: Int = -1
    private var modificationDate: Date = Date(0L)

    private val collectionChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!intent.hasExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE)) return
            val date = Date(intent.getLongExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE, 0L))
            if (date.after(modificationDate)) loadCollection()
        }
    }

    init {
        LocalBroadcastManager.getInstance(application).registerReceiver(
            collectionChangedReceiver,
            IntentFilter(Keys.ACTION_COLLECTION_CHANGED),
        )
        loadCollection()
    }

    override fun onCleared() {
        super.onCleared()
        LocalBroadcastManager.getInstance(getApplication())
            .unregisterReceiver(collectionChangedReceiver)
    }

    private fun loadCollection() {
        viewModelScope.launch {
            val loaded = FileHelper.readCollectionSuspended(getApplication())
            modificationDate = loaded.modificationDate
            _collection.value = loaded
            maybeExportPlaylists(loaded)
        }
    }

    /*
     * The M3U/PLS mirrors are refreshed whenever the number of stations changes. This used
     * to run on the main thread from a LiveData observer that was re-registered on every
     * onResume, so it piled up and did file I/O on the UI thread.
     */
    private fun maybeExportPlaylists(collection: Collection) {
        val size = collection.stations.size
        if (size == lastExportedSize) return
        lastExportedSize = size
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            CollectionHelper.exportCollectionM3u(context, collection)
            CollectionHelper.exportCollectionPls(context, collection)
        }
    }

    /* ---- list operations ---- */

    fun toggleEditor(stationUuid: String) {
        val next = if (_expandedStationUuid.value == stationUuid) "" else stationUuid
        _expandedStationUuid.value = next
        PreferencesHelper.saveStationListStreamUuid(next)
    }

    fun closeEditor() {
        _expandedStationUuid.value = ""
        PreferencesHelper.saveStationListStreamUuid("")
    }

    /*
     * Removes a station. Addressed by UUID rather than list position: the old code passed
     * RecyclerView adapter positions around, which are -1 while a row is being swiped away
     * and crashed with IndexOutOfBounds.
     */
    fun removeStation(stationUuid: String) {
        val current = _collection.value
        val station = current.stations.firstOrNull { it.uuid == stationUuid } ?: return
        val updated = current.deepCopy()
        updated.stations.removeAll { it.uuid == stationUuid }
        _collection.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            CollectionHelper.deleteStationImages(context, station)
            withContext(Dispatchers.Main) {
                CollectionHelper.saveCollection(context, updated)
            }
        }
    }

    /*
     * Same clean-up as removeStation, for the whole collection at once. Saved with
     * allowEmpty: the stored collection is only ever replaced by an empty one when the last
     * station goes, and wiping several at once would otherwise be silently discarded and read
     * straight back off the file.
     */
    fun removeAllStations() {
        val current = _collection.value
        if (current.stations.isEmpty()) return
        val removed = current.stations.toList()
        val updated = current.deepCopy()
        updated.stations.clear()
        _collection.value = updated
        // Written before the images are cleaned up rather than after: the sooner the empty
        // collection reaches storage, the smaller the window in which the player service can
        // save its own copy over it. saveCollection does the file write on a background
        // thread itself.
        CollectionHelper.saveCollection(getApplication(), updated, allowEmpty = true)
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            removed.forEach { CollectionHelper.deleteStationImages(context, it) }
        }
    }

    fun toggleStarred(stationUuid: String) {
        val updated = _collection.value.deepCopy()
        val station = updated.stations.firstOrNull { it.uuid == stationUuid } ?: return
        station.starred = !station.starred
        val sorted = CollectionHelper.sortCollection(updated)
        _collection.value = sorted
        CollectionHelper.saveCollection(getApplication(), sorted)
    }

    fun saveStation(stationUuid: String, name: String, streamUri: String) {
        val updated = _collection.value.deepCopy()
        val station = updated.stations.firstOrNull { it.uuid == stationUuid } ?: return
        if (name.isNotEmpty()) {
            station.name = name
            station.nameManuallySet = true
        }
        if (streamUri.isNotEmpty() && station.streamUris.isNotEmpty()) {
            station.streamUris[station.stream] = streamUri
        }
        val sorted = CollectionHelper.sortCollection(updated)
        _collection.value = sorted
        CollectionHelper.saveCollection(getApplication(), sorted)
    }

    /*
     * Reorders within the starred / non-starred group only, matching the old drag rules.
     * Returns false when the move was rejected so the UI can leave the row where it was.
     */
    fun moveStation(fromIndex: Int, toIndex: Int): Boolean {
        val current = _collection.value
        val stations = current.stations
        if (fromIndex !in stations.indices || toIndex !in stations.indices) return false
        if (stations[fromIndex].starred != stations[toIndex].starred) return false
        val updated = current.deepCopy()
        val moved = updated.stations.removeAt(fromIndex)
        updated.stations.add(toIndex, moved)
        _collection.value = updated
        return true
    }

    /* Called once the drag gesture ends, so the file is written once rather than per step. */
    fun persistOrder() {
        CollectionHelper.saveCollection(getApplication(), _collection.value)
    }

    fun addStation(station: Station) {
        _collection.value = CollectionHelper.addStation(getApplication(), _collection.value, station)
    }

    fun setStationImage(imageUri: Uri, stationUuid: String) {
        _collection.value = CollectionHelper.setStationImageWithStationUuid(
            getApplication(),
            _collection.value,
            imageUri,
            stationUuid,
            imageManuallySet = true,
        )
    }

    fun isConnectedToNetwork(): Boolean = NetworkHelper.isConnectedToNetwork(getApplication())
}
