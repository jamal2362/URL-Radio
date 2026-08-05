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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.playback.PlaybackUiState
import com.jamal2367.urlradio.ui.player.PlayerPane
import com.jamal2367.urlradio.ui.settings.SettingsCallbacks
import com.jamal2367.urlradio.ui.settings.SettingsScreen
import com.jamal2367.urlradio.ui.stations.StationListScreen
import kotlin.time.Duration.Companion.milliseconds

/** Which top-level surface is showing. */
enum class AppScreen { Stations, Settings }

/**
 * Rotation recreates the activity (see the note on the manifest's activity entry), so the
 * screen has to go through the saved instance state to survive it. Stored by name rather
 * than by ordinal so reordering the entries cannot silently restore the wrong screen.
 */
private val AppScreenSaver = Saver<AppScreen, String>(
    save = { it.name },
    restore = { AppScreen.valueOf(it) },
)

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
    var screen by rememberSaveable(stateSaver = AppScreenSaver) {
        mutableStateOf(AppScreen.Stations)
    }
    // Held here rather than inside StationsPane: the compact and the wide layout call that
    // composable from two different places, so a saveable inside it would be filed under two
    // different keys and the selection would be dropped on the way from one layout to the
    // other. 0 = all stations, 1 = favourites only.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var playerExpanded by remember { mutableStateOf(false) }
    // Bumped by every interaction with the expanded player so the idle timer below restarts
    // rather than closing the panel while the user is still working in it.
    var playerActivity by remember { mutableIntStateOf(0) }
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

    LaunchedEffect(playerExpanded, playerActivity) {
        if (!playerExpanded) return@LaunchedEffect
        delay(5_000.milliseconds)
        playerExpanded = false
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
        if (screen == AppScreen.Settings) {
            // Settings take the whole window in both layouts. Squeezing them into the wide
            // layout's side column left the station list visible next to a top app bar that
            // spans the full width, which read as a broken screen rather than a second pane.
            //
            // Not wrapped in a verticalScroll Column: SettingsScreen is a LazyColumn and
            // scrolls itself. Nesting the two measured it with an unbounded height, which
            // crashed the moment settings were opened in landscape.
            SettingsScreen(
                versionSummary = state.versionSummary,
                themeSelection = state.themeSelection,
                themeLabel = state.themeLabel,
                largeBuffer = state.largeBuffer,
                editStations = state.editStationsEnabled,
                editStreamUris = state.editStreamUrisEnabled,
                callbacks = actions.settings,
                contentPadding = padding,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (isWideLayout) {
            // The second column only earns its 400dp when there is something to put in it.
            // With an empty collection there is no station to play, so the column is dropped
            // and the station pane - onboarding, at that point - gets the whole window.
            val showSidePane = !state.showOnboarding

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
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        onOpenSettings = { screen = AppScreen.Settings },
                        onDeleteRequest = { pendingDelete = it },
                    )
                }
                if (showSidePane) {
                    Surface(
                        tonalElevation = 3.dp,
                        modifier = Modifier
                            .width(400.dp)
                            .fillMaxHeight(),
                    ) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            PlayerPane(
                                station = state.currentStation,
                                playback = state.playback,
                                expanded = true,
                                metadataIndex = effectiveMetadataIndex,
                                onToggleExpanded = {},
                                onSetExpanded = {},
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
            // The scaffold reports the system bar insets but does not apply them, so the
            // bottom inset is consumed here - otherwise the player and the floating
            // toolbar end up underneath the gesture bar.
            //
            // The player floats over the list rather than sitting below it: the list is
            // given just short of the player's height as bottom padding, so the last
            // station scrolls a little way underneath the player instead of stopping
            // cleanly above it.
            val density = LocalDensity.current
            var playerHeight by remember { mutableStateOf(0.dp) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
            ) {
                StationsPane(
                    state = state,
                    actions = actions,
                    contentPadding = padding,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    listBottomPadding = (playerHeight - PLAYER_STATION_OVERLAP)
                        .coerceAtLeast(0.dp),
                    onOpenSettings = { screen = AppScreen.Settings },
                    onDeleteRequest = { pendingDelete = it },
                )
                // The player is hidden entirely while onboarding is showing, which is
                // what the old bottom sheet did through STATE_HIDDEN.
                AnimatedVisibility(
                    visible = !state.showOnboarding,
                    // Grows out of the bottom edge. The default for a Box-hosted
                    // AnimatedVisibility expands from the top-start corner instead.
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 3.dp,
                        shadowElevation = 6.dp,
                        // Measured including its own margin, because that is the strip of
                        // screen the list has to leave free.
                        modifier = Modifier
                            .onSizeChanged {
                                playerHeight = with(density) { it.height.toDp() }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        PlayerPane(
                            station = state.currentStation,
                            playback = state.playback,
                            expanded = playerExpanded,
                            metadataIndex = effectiveMetadataIndex,
                            onToggleExpanded = {
                                playerExpanded = !playerExpanded
                                playerActivity++
                            },
                            onSetExpanded = {
                                playerExpanded = it
                                playerActivity++
                            },
                            onTogglePlayback = {
                                actions.onTogglePlayback(state.currentStation)
                                playerActivity++
                            },
                            onPreviousMetadata = {
                                metadataIndex = previousIndex(effectiveMetadataIndex, historySize)
                                playerActivity++
                            },
                            onNextMetadata = {
                                metadataIndex = nextIndex(effectiveMetadataIndex, historySize)
                                playerActivity++
                            },
                            onCopy = {
                                actions.onCopy(it)
                                playerActivity++
                            },
                            onCopyFullHistory = {
                                actions.onCopyFullHistory()
                                playerActivity++
                            },
                            onShare = { actions.onShare(state.currentStation) },
                            onStartSleepTimer = { showSleepTimerPicker = true },
                            onCancelSleepTimer = actions.onCancelSleepTimer,
                        )
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

/**
 * How far the last station card is allowed to slide underneath the floating player. Small on
 * purpose: it should read as the list continuing behind the player, not as a cropped card.
 */
private val PLAYER_STATION_OVERLAP = 14.dp

/**
 * @param listBottomPadding space kept free at the end of the list. In the compact layout the
 *   player floats over the list, so this is its height minus [PLAYER_STATION_OVERLAP].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StationsPane(
    state: AppState,
    actions: AppActions,
    contentPadding: PaddingValues,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteRequest: (Station) -> Unit,
    listBottomPadding: Dp = 0.dp,
) {
    // Favourites are always sorted to the front of the collection (see
    // CollectionHelper.sortCollection), so this filtered list is a plain prefix of
    // state.stations and its indices line up with the full list -- onMove needs no remapping.
    val visibleStations = if (selectedTab == 1) state.stations.filter { it.starred } else state.stations

    Column(modifier = Modifier.fillMaxSize()) {
        // Add-station and settings flank the tab selector. The tabs themselves drop out
        // during onboarding -- there is nothing to filter yet -- but the two buttons stay,
        // because adding the first station is the whole point of that screen.
        StationsTopBar(
            selectedTab = selectedTab,
            onSelect = onSelectTab,
            showTabs = !state.showOnboarding,
            onAddStation = actions.onAddStation,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.padding(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = 4.dp,
            ),
        )

        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 1 && visibleStations.isEmpty() && !state.showOnboarding) {
                Text(
                    text = stringResource(R.string.stations_favorites_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                )
            } else {
                StationListScreen(
                    stations = visibleStations,
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
                        top = 0.dp,
                        bottom = listBottomPadding,
                    ),
                )
            }
        }
    }
}

/* The top row: add-station, the all/favourites selector, and settings. */
@Composable
private fun StationsTopBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    showTabs: Boolean,
    onAddStation: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        RoundBarButton(
            icon = R.drawable.ic_add_24dp,
            contentDescription = stringResource(R.string.dialog_find_station_title),
            onClick = onAddStation,
        )

        if (showTabs) {
            FloatingTabBar(
                selectedTab = selectedTab,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        RoundBarButton(
            icon = R.drawable.ic_settings_24dp,
            contentDescription = stringResource(R.string.fragment_settings_title),
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun RoundBarButton(icon: Int, contentDescription: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        modifier = Modifier.size(56.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(painter = painterResource(icon), contentDescription = contentDescription)
        }
    }
}

/* A floating pill that swaps the station list between all stations and favourites. */
@Composable
private fun FloatingTabBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            FloatingTabBarItem(
                label = stringResource(R.string.tab_stations_all),
                selected = selectedTab == 0,
                onClick = { onSelect(0) },
                modifier = Modifier.weight(1f),
            )
            FloatingTabBarItem(
                label = stringResource(R.string.tab_stations_favorites),
                selected = selectedTab == 1,
                onClick = { onSelect(1) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FloatingTabBarItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "tabBackground",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabContent",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = content,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerPicker(onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    // Defaults to one minute, 24-hour clock -- same as the old MaterialTimePicker setup.
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
