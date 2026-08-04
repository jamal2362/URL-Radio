/*
 * UrlRadioApp.kt
 * The whole app UI: adaptive list/player layout, settings, dialogs
 *
 * On a phone the player sits at the bottom and expands in place. From medium width upwards
 * the same player becomes a permanent second column next to the station list.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.playback.PlaybackUiState
import com.jamal2367.urlradio.ui.player.PlayerPane
import com.jamal2367.urlradio.ui.settings.SettingsCallbacks
import com.jamal2367.urlradio.ui.settings.SettingsScreen
import com.jamal2367.urlradio.ui.stations.StationListScreen

/** Which top-level surface is showing. */
enum class AppScreen { Stations, Settings }

/** Everything the app scaffold needs from the hosting activity. */
data class AppState(
    val stations: List<Station>,
    val currentStation: Station,
    val playback: PlaybackUiState,
    val expandedStationUuid: String,
    val editStationsEnabled: Boolean,
    val editStreamUrisEnabled: Boolean,
    val showOnboarding: Boolean,
    val hasActiveDownloads: Boolean,
    val versionSummary: String,
    val themeSelection: String,
    val themeLabel: String,
    val largeBuffer: Boolean,
)

data class AppActions(
    val onTogglePlayback: (Station) -> Unit,
    val onToggleEditor: (Station) -> Unit,
    val onSaveStation: (Station, String, String) -> Unit,
    val onCancelEdit: () -> Unit,
    val onChangeImage: (Station) -> Unit,
    val onPlaceOnHomeScreen: (Station) -> Unit,
    val onDeleteStation: (Station) -> Unit,
    val onToggleStarred: (Station) -> Unit,
    val onMove: (Int, Int) -> Boolean,
    val onMoveFinished: () -> Unit,
    val onAddStation: () -> Unit,
    val onCopy: (CharSequence) -> Unit,
    val onCopyFullHistory: () -> Unit,
    val onShare: (Station) -> Unit,
    val onStartSleepTimer: (Long) -> Unit,
    val onCancelSleepTimer: () -> Unit,
    val settings: SettingsCallbacks,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UrlRadioApp(
    state: AppState,
    actions: AppActions,
    isWideLayout: Boolean,
    snackbarHostState: SnackbarHostState,
    dialogs: @Composable () -> Unit,
) {
    var screen by remember { mutableStateOf(AppScreen.Stations) }
    var playerExpanded by remember { mutableStateOf(false) }
    var metadataIndex by remember { mutableIntStateOf(-1) }
    var showSleepTimerPicker by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Station?>(null) }

    // Metadata carousel index follows the newest entry unless the user paged away.
    val historySize = state.playback.metadataHistory.size
    val effectiveMetadataIndex = if (metadataIndex in 0 until historySize) metadataIndex
    else historySize - 1

    // Back first collapses the expanded player, then leaves settings, matching the old
    // onBackPressed behaviour of minimising the sheet before the activity handled back.
    BackHandler(enabled = playerExpanded || screen == AppScreen.Settings) {
        when {
            playerExpanded -> playerExpanded = false
            else -> screen = AppScreen.Stations
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (screen == AppScreen.Settings) {
                MediumFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.fragment_settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = { screen = AppScreen.Stations }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_left_24dp),
                                contentDescription = null,
                            )
                        }
                    },
                    scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
                        rememberTopAppBarState()
                    ),
                )
            }
        },
    ) { padding ->
        if (isWideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    StationsPane(
                        state = state,
                        actions = actions,
                        contentPadding = padding,
                        onOpenSettings = { screen = AppScreen.Settings },
                        onDeleteRequest = { pendingDelete = it },
                    )
                }
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .width(400.dp)
                        .fillMaxHeight(),
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (screen == AppScreen.Settings) {
                            SettingsScreen(
                                versionSummary = state.versionSummary,
                                themeSelection = state.themeSelection,
                                themeLabel = state.themeLabel,
                                largeBuffer = state.largeBuffer,
                                editStations = state.editStationsEnabled,
                                editStreamUris = state.editStreamUrisEnabled,
                                callbacks = actions.settings,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.fillMaxHeight(),
                            )
                        } else {
                            PlayerPane(
                                station = state.currentStation,
                                playback = state.playback,
                                expanded = true,
                                metadataIndex = effectiveMetadataIndex,
                                onToggleExpanded = {},
                                onTogglePlayback = { actions.onTogglePlayback(state.currentStation) },
                                onPreviousMetadata = {
                                    metadataIndex = previousIndex(effectiveMetadataIndex, historySize)
                                },
                                onNextMetadata = {
                                    metadataIndex = nextIndex(effectiveMetadataIndex, historySize)
                                },
                                onCopy = actions.onCopy,
                                onCopyFullHistory = actions.onCopyFullHistory,
                                onShare = { actions.onShare(state.currentStation) },
                                onStartSleepTimer = { showSleepTimerPicker = true },
                                onCancelSleepTimer = actions.onCancelSleepTimer,
                                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                            )
                        }
                    }
                }
            }
        } else {
            if (screen == AppScreen.Settings) {
                SettingsScreen(
                    versionSummary = state.versionSummary,
                    themeSelection = state.themeSelection,
                    themeLabel = state.themeLabel,
                    largeBuffer = state.largeBuffer,
                    editStations = state.editStationsEnabled,
                    editStreamUris = state.editStreamUrisEnabled,
                    callbacks = actions.settings,
                    contentPadding = padding,
                )
            } else {
                // The scaffold reports the system bar insets but does not apply them, so the
                // bottom inset is consumed here - otherwise the player and the floating
                // toolbar end up underneath the gesture bar.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding())
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        StationsPane(
                            state = state,
                            actions = actions,
                            contentPadding = padding,
                            onOpenSettings = { screen = AppScreen.Settings },
                            onDeleteRequest = { pendingDelete = it },
                        )
                    }
                    // The player is hidden entirely while onboarding is showing, which is
                    // what the old bottom sheet did through STATE_HIDDEN.
                    AnimatedVisibility(visible = !state.showOnboarding) {
                        Surface(tonalElevation = 3.dp) {
                            PlayerPane(
                                station = state.currentStation,
                                playback = state.playback,
                                expanded = playerExpanded,
                                metadataIndex = effectiveMetadataIndex,
                                onToggleExpanded = { playerExpanded = !playerExpanded },
                                onTogglePlayback = { actions.onTogglePlayback(state.currentStation) },
                                onPreviousMetadata = {
                                    metadataIndex = previousIndex(effectiveMetadataIndex, historySize)
                                },
                                onNextMetadata = {
                                    metadataIndex = nextIndex(effectiveMetadataIndex, historySize)
                                },
                                onCopy = actions.onCopy,
                                onCopyFullHistory = actions.onCopyFullHistory,
                                onShare = { actions.onShare(state.currentStation) },
                                onStartSleepTimer = { showSleepTimerPicker = true },
                                onCancelSleepTimer = actions.onCancelSleepTimer,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { station ->
        com.jamal2367.urlradio.ui.dialogs.ConfirmDialog(
            message = "${stringResource(R.string.dialog_yes_no_message_remove_station)}\n\n- ${station.name}",
            confirmLabel = stringResource(R.string.dialog_yes_no_positive_button_remove_station),
            onConfirm = { actions.onDeleteStation(station) },
            onDismiss = { pendingDelete = null },
        )
    }

    if (showSleepTimerPicker) {
        SleepTimerPicker(
            onConfirm = { millis ->
                actions.onStartSleepTimer(millis)
                showSleepTimerPicker = false
            },
            onDismiss = { showSleepTimerPicker = false },
        )
    }

    dialogs()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StationsPane(
    state: AppState,
    actions: AppActions,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onDeleteRequest: (Station) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        StationListScreen(
            stations = state.stations,
            playingStationUuid = if (state.playback.isPlaying) state.playback.stationUuid else "",
            expandedStationUuid = state.expandedStationUuid,
            editStationsEnabled = state.editStationsEnabled,
            editStreamUrisEnabled = state.editStreamUrisEnabled,
            showOnboarding = state.showOnboarding,
            hasActiveDownloads = state.hasActiveDownloads,
            onTogglePlayback = actions.onTogglePlayback,
            onToggleEditor = actions.onToggleEditor,
            onSaveStation = actions.onSaveStation,
            onCancelEdit = actions.onCancelEdit,
            onChangeImage = actions.onChangeImage,
            onPlaceOnHomeScreen = actions.onPlaceOnHomeScreen,
            onDeleteRequest = onDeleteRequest,
            onToggleStarred = actions.onToggleStarred,
            onMove = actions.onMove,
            onMoveFinished = actions.onMoveFinished,
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = 96.dp,
            ),
        )

        // Add-station and settings, as an Expressive floating toolbar. Replaces the two
        // extended FABs that used to be a list footer item.
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        ) {
            IconButton(onClick = actions.onAddStation) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24dp),
                    contentDescription = stringResource(R.string.dialog_find_station_title),
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_24dp),
                    contentDescription = stringResource(R.string.fragment_settings_title),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerPicker(onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    // Defaults to one minute, 24 hour clock -- same as the old MaterialTimePicker setup.
    val timeState = rememberTimePickerState(initialHour = 0, initialMinute = 1, is24Hour = true)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = timeState.hour * 3_600_000L + timeState.minute * 60_000L + 1000L
                onConfirm(millis)
            }) { Text(stringResource(R.string.dialog_generic_button_okay)) }
        },
        title = { Text(stringResource(R.string.descr_expanded_player_sleep_timer_start_button)) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_generic_button_cancel))
            }
        },
    ) {
        TimePicker(state = timeState)
    }
}

private fun previousIndex(current: Int, size: Int): Int =
    if (size <= 0) -1 else if (current > 0) current - 1 else size - 1

private fun nextIndex(current: Int, size: Int): Int =
    if (size <= 0) -1 else if (current < size - 1) current + 1 else 0
