/*
 * MainActivity.kt
 * The single activity. Hosts the Compose UI and owns the playback connection.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.AppThemeHelper
import com.jamal2367.urlradio.helpers.BackupHelper
import com.jamal2367.urlradio.helpers.CollectionHelper
import com.jamal2367.urlradio.helpers.DownloadHelper
import com.jamal2367.urlradio.helpers.FileHelper
import com.jamal2367.urlradio.helpers.ImportHelper
import com.jamal2367.urlradio.helpers.PreferencesHelper
import com.jamal2367.urlradio.helpers.ShortcutHelper
import com.jamal2367.urlradio.helpers.UpdateCheckHelper
import com.jamal2367.urlradio.playback.PlaybackConnection
import com.jamal2367.urlradio.ui.AppActions
import com.jamal2367.urlradio.ui.AppState
import com.jamal2367.urlradio.ui.UrlRadioApp
import com.jamal2367.urlradio.ui.dialogs.AddStationDialog
import com.jamal2367.urlradio.ui.dialogs.ConfirmDialog
import com.jamal2367.urlradio.ui.dialogs.ErrorDialog
import com.jamal2367.urlradio.ui.dialogs.FindStationDialog
import com.jamal2367.urlradio.ui.dialogs.StationSearchViewModel
import com.jamal2367.urlradio.ui.settings.SettingsCallbacks
import com.jamal2367.urlradio.ui.stations.StationsViewModel
import com.jamal2367.urlradio.ui.theme.UrlRadioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {

    private val tag: String = MainActivity::class.java.simpleName

    private lateinit var playback: PlaybackConnection

    /* Set by intents that arrive before the UI is ready. */
    private var pendingImportStations by mutableStateOf<List<Station>>(emptyList())
    private var pendingRestoreUri by mutableStateOf<Uri?>(null)

    /*
     * A playback request from an intent, held until the MediaController is actually
     * connected. Intents are delivered in onCreate/onNewIntent, but the controller is only
     * built in onStart and connects asynchronously, so acting immediately would call into a
     * null controller and silently do nothing. The pre-Compose code had the same ordering
     * requirement and solved it by running handleStartIntent() from setupController().
     */
    private var pendingPlaybackIntent by mutableStateOf<Intent?>(null)

    /* Messages raised outside the composition (backup/restore) that the UI shows. */
    private val snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    // ---- activity result launchers ----

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val stationUuid = imageTargetStationUuid
            imageTargetStationUuid = ""
            if (uri == null || stationUuid.isEmpty()) {
                toast(R.string.toastalert_failed_picking_media)
            } else {
                stationsViewModelRef?.setStationImage(uri, stationUuid)
            }
        }
    private var imageTargetStationUuid: String = ""
    private var stationsViewModelRef: StationsViewModel? = null

    private val saveM3uLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            copyExport(result, FileHelper.getM3ulUri(this), R.string.toastmessage_save_m3u, "M3U")
        }

    private val savePlsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            copyExport(result, FileHelper.getPlsqlUri(this), R.string.toastmessage_save_pls, "PLS")
        }

    private val backupLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val target = result.data?.data
            if (result.resultCode == RESULT_OK && target != null) {
                BackupHelper.backup(this, target) { message ->
                    lifecycleScope.launch { snackbarMessages.emit(message) }
                }
            } else {
                Log.w(tag, "Station backup failed.")
            }
        }

    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val source = result.data?.data
            if (result.resultCode == RESULT_OK && source != null) {
                pendingRestoreUri = source
            }
        }

    private val importPlaylistLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val source = result.data?.data
            if (result.resultCode == RESULT_OK && source != null) {
                importPlaylist(source)
            } else {
                Log.w(tag, "Playlist import cancelled.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // house-keeping for installs that predate the current version
        if (PreferencesHelper.isHouseKeepingNecessary()) {
            ImportHelper.removeDefaultStationImageUris(this)
            if (PreferencesHelper.loadCollectionSize() != -1) {
                PreferencesHelper.saveEditStationsEnabled(true)
            }
            PreferencesHelper.saveHouseKeepingNecessaryState()
        }

        FileHelper.createNoMediaFile(getExternalFilesDir(null))

        playback = PlaybackConnection(applicationContext, lifecycleScope)

        // volume keys control the music stream, as before
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC

        setContent { AppRoot() }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        playback.connect()
    }

    override fun onStop() {
        super.onStop()
        playback.release()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    private fun AppRoot() {
        val stationsViewModel: StationsViewModel = viewModel()
        val searchViewModel: StationSearchViewModel = viewModel()
        stationsViewModelRef = stationsViewModel

        val collection by stationsViewModel.collection.collectAsStateWithLifecycle()
        val playbackState by playback.state.collectAsStateWithLifecycle()
        val expandedUuid by stationsViewModel.expandedStationUuid.collectAsStateWithLifecycle()
        val editStations by stationsViewModel.editStationsEnabled.collectAsStateWithLifecycle()
        val editStreamUris by stationsViewModel.editStreamUrisEnabled.collectAsStateWithLifecycle()
        val onboarding by stationsViewModel.showOnboarding.collectAsStateWithLifecycle()
        val downloads by stationsViewModel.hasActiveDownloads.collectAsStateWithLifecycle()
        val searchState by searchViewModel.state.collectAsStateWithLifecycle()

        var themeSelection by remember { mutableStateOf(PreferencesHelper.loadThemeSelection()) }
        var largeBuffer by remember { mutableStateOf(PreferencesHelper.loadLargeBufferSize()) }
        var showFindDialog by remember { mutableStateOf(false) }
        // Selection of the playlist import dialog. Kept here rather than in the search view
        // model because the imported list does not come from a search.
        var importSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
        var errorDialog by remember { mutableStateOf<Pair<Int, Int>?>(null) }

        val snackbarHostState = remember { SnackbarHostState() }

        val systemDark = isSystemInDarkTheme()
        val darkTheme = when (themeSelection) {
            Keys.STATE_THEME_LIGHT_MODE -> false
            Keys.STATE_THEME_DARK_MODE -> true
            else -> systemDark
        }

        // The station the player shows: whatever is loaded, else the first in the list.
        val currentStation: Station = remember(collection, playbackState.stationUuid) {
            when {
                playbackState.stationUuid.isNotEmpty() ->
                    CollectionHelper.getStation(collection, playbackState.stationUuid)

                collection.stations.isNotEmpty() -> collection.stations.first()
                else -> Station()
            }
        }

        // Playback errors surface as a snackbar rather than a toast.
        val connectionFailed = stringResource(R.string.toastmessage_connection_failed)
        LaunchedEffect(playbackState.errorEvent) {
            if (playbackState.errorEvent != null) {
                snackbarHostState.showSnackbar(connectionFailed)
                playback.consumeError()
            }
        }

        // Update check, as before roughly five seconds after start. Bound to the
        // composition, so leaving the app cancels it.
        val updateAvailable = stringResource(R.string.snackbar_update_available)
        val showAction = stringResource(R.string.snackbar_show)
        val releasesApiUrl = stringResource(R.string.snackbar_github_update_check_url)
        val releasesPageUrl = stringResource(R.string.snackbar_url_app_home_page)
        val appName = stringResource(R.string.app_name)
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(5_000.milliseconds)
            val newer = UpdateCheckHelper.findNewerRelease(releasesApiUrl, BuildConfig.VERSION_NAME)
            if (newer != null) {
                val result = snackbarHostState.showSnackbar(
                    message = "$appName $newer $updateAvailable",
                    actionLabel = showAction,
                    duration = androidx.compose.material3.SnackbarDuration.Long,
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    openUrl(releasesPageUrl)
                }
            }
        }

        LaunchedEffect(Unit) {
            snackbarMessages.collect { snackbarHostState.showSnackbar(it) }
        }

        // Runs a start intent only once the controller is live.
        LaunchedEffect(playbackState.isConnected, pendingPlaybackIntent) {
            val intent = pendingPlaybackIntent
            if (intent != null && playbackState.isConnected) {
                pendingPlaybackIntent = null
                handleStartPlayer(intent)
            }
        }

        // A snackbar would be drawn behind the dialog's scrim, so preview feedback is a toast.
        // Only the failure is announced: a running preview is visible on the row itself.
        LaunchedEffect(searchState.previewStartedEvent, searchState.previewUnsupportedEvent) {
            if (searchState.previewUnsupportedEvent != null) {
                toast(R.string.toastmessage_preview_playback_failed)
            }
            if (searchState.previewUnsupportedEvent != null || searchState.previewStartedEvent != null) {
                searchViewModel.consumeEvents()
            }
        }

        UrlRadioTheme(darkTheme = darkTheme) {
            val windowSizeClass = calculateWindowSizeClass(this)
            val isWide = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact &&
                windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact

            UrlRadioApp(
                state = AppState(
                    stations = collection.stations,
                    currentStation = currentStation,
                    playback = playbackState,
                    expandedStationUuid = expandedUuid,
                    editStationsEnabled = editStations,
                    editStreamUrisEnabled = editStreamUris,
                    showOnboarding = onboarding,
                    hasActiveDownloads = downloads,
                    versionSummary = "${getString(R.string.pref_app_version_summary)} " +
                        "${BuildConfig.VERSION_NAME} (${getString(R.string.app_version_name)})",
                    themeSelection = themeSelection,
                    themeLabel = AppThemeHelper.getCurrentTheme(this),
                    largeBuffer = largeBuffer,
                ),
                actions = AppActions(
                    onTogglePlayback = { playback.togglePlayPause(it) },
                    onToggleEditor = { stationsViewModel.toggleEditor(it.uuid) },
                    onSaveStation = { station, name, uri ->
                        stationsViewModel.saveStation(station.uuid, name, uri)
                        stationsViewModel.closeEditor()
                    },
                    onCancelEdit = { stationsViewModel.closeEditor() },
                    onChangeImage = { station ->
                        imageTargetStationUuid = station.uuid
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPlaceOnHomeScreen = { station ->
                        ShortcutHelper.placeShortcut(this, station)
                        stationsViewModel.closeEditor()
                    },
                    onDeleteStation = { stationsViewModel.removeStation(it.uuid) },
                    onToggleStarred = { stationsViewModel.toggleStarred(it.uuid) },
                    onMove = stationsViewModel::moveStation,
                    onMoveFinished = stationsViewModel::persistOrder,
                    onAddStation = { showFindDialog = true },
                    onCopy = { copyToClipboard(it) },
                    onCopyFullHistory = {
                        copyToClipboard(
                            PreferencesHelper.loadMetadataHistory()
                                .joinToString("\n") { it.trim() }
                        )
                    },
                    onShare = { station -> shareStation(station) },
                    onStartSleepTimer = { playback.startSleepTimer(it) },
                    onCancelSleepTimer = { playback.cancelSleepTimer() },
                    settings = SettingsCallbacks(
                        onThemeSelected = {
                            themeSelection = it
                            PreferencesHelper.saveThemeSelection(it)
                        },
                        onUpdateStationImages = {
                            if (stationsViewModel.isConnectedToNetwork()) {
                                DownloadHelper.updateStationImages(this)
                                lifecycleScope.launch {
                                    snackbarHostState.showSnackbar(
                                        getString(R.string.toastmessage_updating_station_images)
                                    )
                                }
                            } else {
                                errorDialog = R.string.dialog_error_title_no_network to
                                    R.string.dialog_error_message_no_network
                            }
                        },
                        onRemoveAllStations = {
                            // Cleared first, stopped second. Pausing makes the player service
                            // write its own copy of the collection back (see
                            // CollectionHelper.savePlaybackState), so the empty collection
                            // wants to be on its way to storage before that happens.
                            stationsViewModel.removeAllStations()
                            playback.pause()
                        },
                        onImportPlaylist = {
                            // "*/*" on purpose: plenty of providers report a playlist as
                            // application/octet-stream, and a narrow filter grays those out.
                            // EXTRA_MIME_TYPES still puts the playlist types first.
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                putExtra(
                                    Intent.EXTRA_MIME_TYPES,
                                    Keys.MIME_TYPES_M3U + Keys.MIME_TYPES_PLS,
                                )
                            }
                            runCatching { importPlaylistLauncher.launch(intent) }.onFailure {
                                Log.e(tag, "Unable to open file picker for playlists.\n$it")
                                toast(R.string.toastmessage_install_file_helper)
                            }
                        },
                        onExportM3u = {
                            launchCreateDocument(saveM3uLauncher, Keys.MIME_TYPE_M3U, "collection", "m3u")
                        },
                        onExportPls = {
                            launchCreateDocument(savePlsLauncher, Keys.MIME_TYPE_PLS, "collection", "pls")
                        },
                        onBackup = {
                            launchCreateDocument(backupLauncher, Keys.MIME_TYPE_ZIP, "URL_Radio", "zip")
                        },
                        onRestore = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                putExtra(Intent.EXTRA_MIME_TYPES, Keys.MIME_TYPES_ZIP)
                            }
                            runCatching { restoreLauncher.launch(intent) }.onFailure {
                                Log.e(tag, "Unable to open file picker for ZIP.\n$it")
                            }
                        },
                        onLargeBufferChanged = {
                            largeBuffer = it
                            PreferencesHelper.saveLargeBufferSize(it)
                        },
                        onEditStationsChanged = {
                            PreferencesHelper.saveEditStationsEnabled(it)
                            // turning editing off also clears the dependent option, as before
                            if (!it) PreferencesHelper.saveEditStreamUrisEnabled(false)
                        },
                        onEditStreamUrisChanged = { PreferencesHelper.saveEditStreamUrisEnabled(it) },
                        onOpenUrl = { openUrl(it) },
                        onCopyVersion = { copyToClipboard(it) },
                    ),
                ),
                isWideLayout = isWide,
                snackbarHostState = snackbarHostState,
            ) {
                if (showFindDialog) {
                    FindStationDialog(
                        state = searchState,
                        onQueryChanged = searchViewModel::onQueryChanged,
                        onQuerySubmitted = searchViewModel::onQuerySubmitted,
                        onToggleSelection = searchViewModel::toggleSelection,
                        onTogglePreview = searchViewModel::togglePreview,
                        onSelectAll = searchViewModel::selectAll,
                        onClearSelection = searchViewModel::clearSelection,
                        onAdd = { stations -> addStationsChecked(stationsViewModel, stations) },
                        onDismiss = {
                            showFindDialog = false
                            searchViewModel.reset()
                        },
                    )
                }

                if (pendingImportStations.isNotEmpty()) {
                    val importedUuids = pendingImportStations.map { it.uuid }.toSet()
                    AddStationDialog(
                        stations = pendingImportStations,
                        selectedUuids = importSelection,
                        // The preview player is shared with the find dialog; only one of the
                        // two is ever on screen.
                        previewUuid = searchState.previewUuid,
                        onToggleSelection = { station ->
                            importSelection = if (station.uuid in importSelection) {
                                importSelection - station.uuid
                            } else {
                                importSelection + station.uuid
                            }
                        },
                        onTogglePreview = searchViewModel::togglePreview,
                        onSelectAll = { importSelection = importedUuids },
                        onClearSelection = { importSelection = emptySet() },
                        onAdd = { stations -> addStationsChecked(stationsViewModel, stations) },
                        onDismiss = {
                            pendingImportStations = emptyList()
                            importSelection = emptySet()
                            searchViewModel.stopPreview()
                        },
                    )
                }

                pendingRestoreUri?.let { uri ->
                    if (collection.stations.isNotEmpty()) {
                        ConfirmDialog(
                            message = stringResource(R.string.dialog_restore_collection_replace_existing),
                            confirmLabel = stringResource(R.string.dialog_yes_no_positive_button_default),
                            onConfirm = {
                                BackupHelper.restore(this@MainActivity, uri) { message ->
                                    lifecycleScope.launch { snackbarMessages.emit(message) }
                                }
                            },
                            onDismiss = { pendingRestoreUri = null },
                        )
                    } else {
                        LaunchedEffect(uri) {
                            BackupHelper.restore(this@MainActivity, uri) { message ->
                                lifecycleScope.launch { snackbarMessages.emit(message) }
                            }
                            pendingRestoreUri = null
                        }
                    }
                }

                errorDialog?.let { (title, message) ->
                    ErrorDialog(
                        title = stringResource(title),
                        message = stringResource(message),
                        onDismiss = { errorDialog = null },
                    )
                }
            }
        }
    }

    /*
     * Adds every station the user picked. Entries whose content type is not known yet are
     * looked up first - search results from a playlist do not always carry one.
     */
    private fun addStationsChecked(viewModel: StationsViewModel, stations: List<Station>) {
        if (stations.isEmpty()) return
        lifecycleScope.launch {
            stations.forEach { station ->
                if (station.streamContent.isEmpty() ||
                    station.streamContent == Keys.MIME_TYPE_UNSUPPORTED
                ) {
                    val contentType = withContext(Dispatchers.IO) {
                        com.jamal2367.urlradio.helpers.NetworkHelper.detectContentType(station.getStreamUri())
                    }
                    station.streamContent = contentType.type
                }
                viewModel.addStation(station)
            }
        }
    }

    // ---- intents ----

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> handleViewIntent(intent)
            // deferred until the controller is connected - see pendingPlaybackIntent
            Keys.ACTION_START -> pendingPlaybackIntent = Intent(intent)
            Keys.ACTION_SHOW_PLAYER -> Log.i(tag, "Tap on notification registered.")
        }
        intent.action = ""
    }

    private fun handleViewIntent(intent: Intent) {
        val uri: Uri = intent.data ?: return
        lifecycleScope.launch {
            val stations: List<Station> = withContext(Dispatchers.IO) {
                val scheme = uri.scheme ?: ""
                when {
                    scheme.startsWith("http") ->
                        CollectionHelper.createStationsFromUrl(uri.toString())

                    scheme.startsWith("content") ->
                        CollectionHelper.createStationListFromContentUri(this@MainActivity, uri)

                    else -> emptyList()
                }
            }
            if (stations.isNotEmpty()) {
                pendingImportStations = stations
            } else {
                toast(R.string.toastmessage_station_not_valid)
            }
        }
    }

    /*
     * Reads a picked .m3u / .pls file and offers whatever it contains for selection.
     *
     * Every entry has to be asked what it actually serves, so this can take a moment on a
     * long playlist -- hence the toast before the work starts.
     */
    private fun importPlaylist(uri: Uri) {
        toast(R.string.toastmessage_playlist_import_running)
        lifecycleScope.launch {
            val stations: List<Station> = withContext(Dispatchers.IO) {
                runCatching {
                    CollectionHelper.createStationListFromContentUri(this@MainActivity, uri)
                }.getOrElse {
                    Log.e(tag, "Unable to read the picked playlist.\n$it")
                    emptyList()
                }
            }
            if (stations.isNotEmpty()) {
                pendingImportStations = stations
            } else {
                toast(R.string.toastmessage_playlist_import_empty)
            }
        }
    }


    /*
     * App shortcut and the exported START action. The stream-URI branch reaches
     * PlayerService through the custom PLAY_STREAM command.
     */
    private fun handleStartPlayer(intent: Intent) {
        lifecycleScope.launch {
            val collection = FileHelper.readCollectionSuspended(this@MainActivity)
            when {
                intent.hasExtra(Keys.EXTRA_START_LAST_PLAYED_STATION) -> {
                    val uuid = PreferencesHelper.loadLastPlayedStationUuid()
                    playback.play(CollectionHelper.getStation(collection, uuid))
                }

                intent.hasExtra(Keys.EXTRA_STATION_UUID) -> {
                    val uuid = intent.getStringExtra(Keys.EXTRA_STATION_UUID).orEmpty()
                    playback.play(CollectionHelper.getStation(collection, uuid))
                }

                intent.hasExtra(Keys.EXTRA_STREAM_URI) -> {
                    val streamUri = intent.getStringExtra(Keys.EXTRA_STREAM_URI).orEmpty()
                    playback.playStreamDirectly(streamUri)
                }
            }
        }
    }

    // ---- small helpers ----

    private fun copyExport(result: ActivityResult, source: Uri?, messageRes: Int, label: String) {
        val target = result.data?.data
        if (result.resultCode == RESULT_OK && target != null && source != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                FileHelper.saveCopyOfFileSuspended(this@MainActivity, source, target)
            }
            toast(messageRes)
        } else {
            Log.w(tag, "$label export failed.")
        }
    }

    private fun launchCreateDocument(
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
        mimeType: String,
        baseName: String,
        extension: String,
    ) {
        val timeStamp = SimpleDateFormat("_yyyy-MM-dd'T'HH_mm", Locale.US).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, "$baseName$timeStamp.$extension")
        }
        runCatching { launcher.launch(intent) }.onFailure {
            Log.e(tag, "Unable to open the file picker.\n$it")
            toast(R.string.toastmessage_install_file_helper)
        }
    }

    private fun copyToClipboard(text: CharSequence) {
        val clip = ClipData.newPlainText("simple text", text)
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        // Since Android 13 the system shows its own copy confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            toast(R.string.toastmessage_copied_to_clipboard)
        }
    }

    private fun shareStation(station: Station) {
        val share = Intent.createChooser(Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TITLE, station.name)
            putExtra(Intent.EXTRA_TEXT, station.getStreamUri())
            type = "text/plain"
        }, null)
        startActivity(share)
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
    }
}
