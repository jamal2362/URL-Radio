/*
 * StationDialogs.kt
 * The find-station, add-station and confirmation dialogs as composables
 *
 * Replaces FindStationDialog, AddStationDialog, YesNoDialog and ErrorDialog together with
 * dialog_find_station.xml, dialog_add_station.xml, dialog_generic_with_details.xml and
 * element_search_result.xml.
 *
 * Both station pickers share one list: a tap ticks a station, a long press auditions it, and
 * everything ticked is added in a single step.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.ui.dialogs

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import java.text.NumberFormat
import kotlinx.coroutines.launch

/** How much of the dialog the result list is allowed to claim. */
private val ResultListMaxHeight = 340.dp

/* A yes/no confirmation. Replaces YesNoDialog. */
@Composable
fun ConfirmDialog(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = WideDialogModifier,
        properties = WideDialogProperties,
        title = title?.let { { Text(it) } },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_generic_button_cancel))
            }
        },
    )
}

/* An error with an optional detail block. Replaces ErrorDialog. */
@Composable
fun ErrorDialog(
    title: String,
    message: String,
    details: String = "",
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = WideDialogModifier,
        properties = WideDialogProperties,
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_generic_button_okay))
            }
        },
    )
}

/* Search radio-browser.info or paste a stream address. Replaces FindStationDialog. */
@Composable
fun FindStationDialog(
    state: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onQuerySubmitted: (String) -> Unit,
    onToggleSelection: (Station) -> Unit,
    onTogglePreview: (Station) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAdd: (List<Station>) -> Unit,
    onDismiss: () -> Unit,
) {
    StationPickerDialog(
        title = stringResource(R.string.dialog_find_station_title),
        stations = state.results,
        selectedUuids = state.selectedUuids,
        previewUuid = state.previewUuid,
        onToggleSelection = onToggleSelection,
        onTogglePreview = onTogglePreview,
        onSelectAll = onSelectAll,
        onClearSelection = onClearSelection,
        onAdd = onAdd,
        onDismiss = onDismiss,
        header = {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                label = { Text(stringResource(R.string.dialog_find_station_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                // Pressing search runs the query immediately. The old SearchView had
                // both a live and a submit path; live input alone skips short queries.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onQuerySubmitted(state.query) }
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isSearching) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }

            if (state.showNoResults) {
                Text(
                    text = stringResource(R.string.dialog_find_station_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            state.error?.let { error ->
                SearchErrorHint(error = error, modifier = Modifier.padding(top = 12.dp))
            }

            if (state.results.isEmpty() && !state.isSearching && !state.showNoResults && state.error == null) {
                Text(
                    text = stringResource(R.string.dialog_find_station_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
    )
}

/*
 * Explains why a search or an address check came back empty-handed: what went wrong in
 * plain words, and below it the reason exactly as the network or the server reported it -
 * that second line is what helps when someone files a bug report.
 */
@Composable
internal fun SearchErrorHint(error: SearchError, modifier: Modifier = Modifier) {
    val message: String = stringResource(
        when (error.kind) {
            SearchError.Kind.SEARCH_FAILED -> R.string.dialog_find_station_error_search
            SearchError.Kind.INVALID_ADDRESS -> R.string.dialog_find_station_error_invalid_address
            SearchError.Kind.ADDRESS_UNREACHABLE -> R.string.dialog_find_station_error_unreachable
            SearchError.Kind.NOT_AUDIO -> R.string.dialog_find_station_error_not_audio
            SearchError.Kind.EMPTY_PLAYLIST -> R.string.dialog_find_station_error_empty_playlist
        }
    )
    val detail: String = when {
        error.detail.isBlank() -> ""
        error.kind == SearchError.Kind.NOT_AUDIO -> stringResource(R.string.dialog_find_station_error_content_type, error.detail)
        error.kind == SearchError.Kind.INVALID_ADDRESS -> error.detail
        else -> stringResource(R.string.dialog_find_station_error_detail, error.detail)
    }
    ErrorHint(message = message, detail = detail, modifier = modifier)
}

/* A plain-words message with the technical reason below it, on the error color. */
@Composable
internal fun ErrorHint(message: String, detail: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_error_24dp),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Why a logo address was not taken, with the technical reason. */
data class LogoUrlError(val kind: Kind, val detail: String) {
    enum class Kind {
        /** Not an http(s) address at all. */
        INVALID,
        /** The address could not be reached, or did not answer with 200 OK. */
        UNREACHABLE,
        /** The address answered with something that is not a usable image. */
        NOT_IMAGE,
    }
}

/*
 * Asks for the address of a station logo. The address is checked before the dialog
 * closes: onSubmit returns null when the logo was taken, otherwise why it was not - which
 * is then shown under the field, the same way the station search shows its errors.
 */
@Composable
fun LogoUrlDialog(
    stationName: String,
    currentUrl: String,
    onSubmit: suspend (String) -> LogoUrlError?,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(currentUrl) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<LogoUrlError?>(null) }
    val scope = rememberCoroutineScope()
    val submit: () -> Unit = {
        if (url.isNotBlank() && !checking) {
            checking = true
            error = null
            scope.launch {
                val result: LogoUrlError? = onSubmit(url)
                checking = false
                if (result == null) onDismiss() else error = result
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = WideDialogModifier,
        properties = WideDialogProperties,
        icon = { Icon(painter = painterResource(R.drawable.ic_link_24dp), contentDescription = null) },
        title = { Text(stringResource(R.string.dialog_logo_url_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dialog_logo_url_message, stationName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.dialog_logo_url_hint)) },
                    placeholder = { Text("https://…/logo.png") },
                    singleLine = true,
                    enabled = !checking,
                    isError = error != null,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                if (checking) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
                error?.let {
                    ErrorHint(
                        message = stringResource(
                            when (it.kind) {
                                LogoUrlError.Kind.INVALID -> R.string.dialog_find_station_error_invalid_address
                                LogoUrlError.Kind.UNREACHABLE -> R.string.dialog_find_station_error_unreachable
                                LogoUrlError.Kind.NOT_IMAGE -> R.string.dialog_logo_url_error_not_image
                            }
                        ),
                        detail = when (it.kind) {
                            LogoUrlError.Kind.INVALID -> ""
                            LogoUrlError.Kind.UNREACHABLE -> stringResource(R.string.dialog_find_station_error_detail, it.detail)
                            LogoUrlError.Kind.NOT_IMAGE -> stringResource(R.string.dialog_find_station_error_content_type, it.detail)
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = url.isNotBlank() && !checking) {
                Text(stringResource(R.string.dialog_generic_button_okay))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_generic_button_cancel))
            }
        },
    )
}

/* Pick stations found in an opened or imported playlist. Replaces AddStationDialog. */
@Composable
fun AddStationDialog(
    stations: List<Station>,
    selectedUuids: Set<String>,
    previewUuid: String,
    onToggleSelection: (Station) -> Unit,
    onTogglePreview: (Station) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAdd: (List<Station>) -> Unit,
    onDismiss: () -> Unit,
) {
    StationPickerDialog(
        title = stringResource(R.string.dialog_add_station_title),
        stations = stations,
        selectedUuids = selectedUuids,
        previewUuid = previewUuid,
        onToggleSelection = onToggleSelection,
        onTogglePreview = onTogglePreview,
        onSelectAll = onSelectAll,
        onClearSelection = onClearSelection,
        onAdd = onAdd,
        onDismiss = onDismiss,
        header = {
            Text(
                text = stringResource(R.string.dialog_add_station_found, stations.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/*
 * The shared body of both pickers: an optional header, the selection toolbar, the usage hint
 * and the result list. The confirm button adds everything that is ticked in one step.
 */
@Composable
private fun StationPickerDialog(
    title: String,
    stations: List<Station>,
    selectedUuids: Set<String>,
    previewUuid: String,
    onToggleSelection: (Station) -> Unit,
    onTogglePreview: (Station) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAdd: (List<Station>) -> Unit,
    onDismiss: () -> Unit,
    header: @Composable () -> Unit,
) {
    val selectedStations = stations.filter { it.uuid in selectedUuids }
    val allSelected = stations.isNotEmpty() && selectedStations.size == stations.size

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = WideDialogModifier,
        properties = WideDialogProperties,
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )

                Column(
                    modifier = Modifier
                        .weight(weight = 1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                ) {
                    header()

                    if (stations.isNotEmpty()) {
                        SelectionToolbar(
                            selectedCount = selectedStations.size,
                            allSelected = allSelected,
                            onSelectAll = onSelectAll,
                            onClearSelection = onClearSelection,
                        )

                        StationPickerList(
                            stations = stations,
                            selectedUuids = selectedUuids,
                            previewUuid = previewUuid,
                            onToggleSelection = onToggleSelection,
                            onTogglePreview = onTogglePreview,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (stations.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.dialog_station_picker_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dialog_generic_button_cancel))
                    }
                    TextButton(
                        onClick = { onAdd(selectedStations); onDismiss() },
                        enabled = selectedStations.isNotEmpty(),
                    ) {
                        Text(
                            if (selectedStations.size > 1) {
                                stringResource(
                                    R.string.dialog_find_station_button_add_count,
                                    selectedStations.size,
                                )
                            } else {
                                stringResource(R.string.dialog_find_station_button_add)
                            }
                        )
                    }
                }
            }
        }
    }
}

/* "n selected" on the left, select-all / clear opposite it. */
@Composable
private fun SelectionToolbar(
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.dialog_station_picker_selected, selectedCount),
            style = MaterialTheme.typography.labelLarge,
            color = if (selectedCount > 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = if (allSelected) onClearSelection else onSelectAll) {
            Text(
                stringResource(
                    if (allSelected) R.string.dialog_station_picker_clear
                    else R.string.dialog_station_picker_select_all
                )
            )
        }
    }
}

@Composable
private fun StationPickerList(
    stations: List<Station>,
    selectedUuids: Set<String>,
    previewUuid: String,
    onToggleSelection: (Station) -> Unit,
    onTogglePreview: (Station) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ResultListMaxHeight)
            .padding(top = 12.dp),
    ) {
        items(stations, key = { it.uuid }) { station ->
            StationPickerRow(
                station = station,
                selected = station.uuid in selectedUuids,
                previewing = station.uuid == previewUuid,
                onToggleSelection = { onToggleSelection(station) },
                onTogglePreview = { onTogglePreview(station) },
            )
        }
    }
}

@Composable
private fun StationPickerRow(
    station: Station,
    selected: Boolean,
    previewing: Boolean,
    onToggleSelection: () -> Unit,
    onTogglePreview: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "pickerRowBackground",
    )
    val onContainer = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        // The station being auditioned is outlined rather than recolored, so it stays
        // readable whether it is also ticked.
        border = if (previewing) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onToggleSelection,
                onLongClick = onTogglePreview,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            StationPickerAvatar(station = station, previewing = previewing)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = station.getStreamUri(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Codec and bitrate, then how many listened in the last 24 hours - the
                // latter only for stations from radio-browser.info, the only source of it.
                val clicks: String = if (station.radioBrowserStationUuid.isNotEmpty()) {
                    pluralStringResource(
                        R.plurals.dialog_station_picker_clicks,
                        station.clickCount,
                        NumberFormat.getIntegerInstance().format(station.clickCount),
                    )
                } else {
                    ""
                }
                val details = listOf(bitrateLabel(station), clicks).filter { it.isNotEmpty() }.joinToString("  ·  ")
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // Not clickable itself: the whole row already toggles the tick, and a second
            // target for the same action only makes the row harder to hit.
            Checkbox(checked = selected, onCheckedChange = null)
        }
    }
}

/*
 * Shows what the row is: the station's logo when it has one - from radio-browser.info or
 * from the playlist - otherwise a note symbol, and a play symbol over either while the
 * station is being auditioned. A logo that fails to load leaves the note symbol showing.
 */
@Composable
private fun StationPickerAvatar(station: Station, previewing: Boolean) {
    val context = LocalContext.current
    val logo: String = station.remoteImageLocation
    val request = remember(logo) {
        if (logo.isEmpty()) null
        else ImageRequest.Builder(context).data(logo).build()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (!previewing) {
            Icon(
                painter = painterResource(R.drawable.ic_music_note_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (request != null) {
            // drawn over the note, so the note is what remains while loading or on failure
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (previewing) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_player_play_symbol_42dp),
                    contentDescription = stringResource(R.string.descr_station_preview_playing),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/* "codec | bitrate Kb/s" - playlist entries carry neither, so the line is left out for them. */
private fun bitrateLabel(station: Station): String = when {
    station.codec.isEmpty() -> ""
    station.bitrate == 0 -> station.codec
    else -> "${station.codec} | ${station.bitrate} Kb/s"
}
