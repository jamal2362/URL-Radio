/*
 * StationDialogs.kt
 * The find-station, add-station and confirmation dialogs as composables
 *
 * Replaces FindStationDialog, AddStationDialog, YesNoDialog and ErrorDialog together with
 * dialog_find_station.xml, dialog_add_station.xml, dialog_generic_with_details.xml and
 * element_search_result.xml.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station

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
    onResultTapped: (Station) -> Unit,
    onAdd: (Station) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_find_station_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChanged,
                    label = { Text(stringResource(R.string.dialog_find_station_hint)) },
                    singleLine = true,
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
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                SearchResultList(
                    results = state.results,
                    selected = state.selected,
                    onResultTapped = onResultTapped,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { state.selected?.let(onAdd); onDismiss() },
                enabled = state.selected != null,
            ) {
                Text(stringResource(R.string.dialog_find_station_button_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_generic_button_cancel))
            }
        },
    )
}

/* Pick one of the stations found in an opened playlist. Replaces AddStationDialog. */
@Composable
fun AddStationDialog(
    stations: List<Station>,
    selected: Station?,
    onResultTapped: (Station) -> Unit,
    onAdd: (Station) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_station_title)) },
        text = {
            SearchResultList(
                results = stations,
                selected = selected,
                onResultTapped = onResultTapped,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onAdd); onDismiss() },
                enabled = selected != null,
            ) {
                Text(stringResource(R.string.dialog_find_station_button_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_generic_button_cancel))
            }
        },
    )
}

@Composable
private fun SearchResultList(
    results: List<Station>,
    selected: Station?,
    onResultTapped: (Station) -> Unit,
) {
    if (results.isEmpty()) return
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .padding(top = 12.dp),
    ) {
        items(results, key = { it.uuid }) { station ->
            val isSelected = selected?.uuid == station.uuid
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onResultTapped(station) },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleSmall,
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
                    val bitrate = when {
                        station.codec.isEmpty() -> ""
                        station.bitrate == 0 -> station.codec
                        else -> "${station.codec} | ${station.bitrate}kbps"
                    }
                    if (bitrate.isNotEmpty()) {
                        Text(
                            text = bitrate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
