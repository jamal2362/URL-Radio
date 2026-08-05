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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station

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
        icon = R.drawable.ic_add_24dp,
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

            if (state.results.isEmpty() && !state.isSearching && !state.showNoResults) {
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
        icon = R.drawable.ic_playlist_add_24dp,
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
    icon: Int,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = WideDialogModifier,
        properties = WideDialogProperties,
        icon = { Icon(painter = painterResource(icon), contentDescription = null) },
        title = { Text(title) },
        text = {
            Column {
                header()

                if (stations.isNotEmpty()) {
                    SelectionToolbar(
                        selectedCount = selectedStations.size,
                        allSelected = allSelected,
                        onSelectAll = onSelectAll,
                        onClearSelection = onClearSelection,
                    )

                    Text(
                        text = stringResource(R.string.dialog_station_picker_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        },
        confirmButton = {
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
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_generic_button_cancel))
            }
        },
    )
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
            StationPickerAvatar(previewing = previewing)

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
                val bitrate = bitrateLabel(station)
                if (bitrate.isNotEmpty()) {
                    Text(
                        text = bitrate,
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

/* Shows what the row is: a station, or the station that is currently being auditioned. */
@Composable
private fun StationPickerAvatar(previewing: Boolean) {
    val background = if (previewing) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val tint = if (previewing) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background),
    ) {
        Icon(
            painter = painterResource(
                if (previewing) R.drawable.ic_player_play_symbol_42dp
                else R.drawable.ic_music_note_24dp
            ),
            contentDescription = if (previewing) {
                stringResource(R.string.descr_station_preview_playing)
            } else {
                null
            },
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/* "codec | bitrate kbps" - playlist entries carry neither, so the line is left out for them. */
private fun bitrateLabel(station: Station): String = when {
    station.codec.isEmpty() -> ""
    station.bitrate == 0 -> station.codec
    else -> "${station.codec} | ${station.bitrate}kbps"
}
