/*
 * StationCard.kt
 * One row of the station list, including its inline editor
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.ui.stations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private val CardShape = RoundedCornerShape(24.dp)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StationCard(
    station: Station,
    isPlaying: Boolean,
    isEditorOpen: Boolean,
    editStationsEnabled: Boolean,
    editStreamUrisEnabled: Boolean,
    onTogglePlayback: () -> Unit,
    onToggleEditor: () -> Unit,
    onSave: (name: String, streamUri: String) -> Unit,
    onCancelEdit: () -> Unit,
    onChangeImage: () -> Unit,
    onPlaceOnHomeScreen: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth(),
    ) {
        // Explicitly clipped: the accent bar runs to the very edge of the card, and
        // without this it squares off the rounded trailing corners.
        Box(modifier = Modifier.clip(CardShape)) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Long-pressing empty space in the row picks the station up for
                        // reordering. The cover and the name below carry their own
                        // long-press, so those two spots open the editor instead.
                        .then(dragModifier)
                        .combinedClickable(onClick = onTogglePlayback)
                ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                StationCover(
                    station = station,
                    modifier = Modifier
                        .size(64.dp)
                        .then(
                            // While the editor is open the cover is the shortcut to the image
                            // picker; otherwise a long press on it opens the editor.
                            if (isEditorOpen) {
                                Modifier.clickable(
                                    interactionSource = null,
                                    indication = null,
                                    onClick = onChangeImage,
                                )
                            } else {
                                Modifier.combinedClickable(
                                    interactionSource = null,
                                    indication = null,
                                    onClick = onTogglePlayback,
                                    onLongClick = if (editStationsEnabled) onToggleEditor else null,
                                )
                            }
                        ),
                )

                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    // Clip would cut the last glyph in half; a name that does not fit ends
                    // in an ellipsis instead.
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        // With a heart the name stops 8dp short of it; without one there is
                        // nothing to keep clear of, so the name runs out to the row's own
                        // 8dp padding.
                        .padding(start = 8.dp, end = if (station.starred) 8.dp else 0.dp)
                        // The cover and the name stay visually silent when tapped: no ripple,
                        // no hover highlight. Only the free area of the row still reacts.
                        .combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onClick = onTogglePlayback,
                            onLongClick = if (editStationsEnabled) onToggleEditor else null,
                        ),
                )

                // Neither the heart nor the padding around the name claims touches -- padding
                // sits outside the text's clickable -- so a long press to the right of the
                // name still reaches the row's reorder gesture, however long the name is.
                if (station.starred) {
                    val heartTint = if (station.imageColor != -1) Color(station.imageColor)
                    else MaterialTheme.colorScheme.primary

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            // Tinted with the heart's own color rather than a neutral one, kept
                            // light so the fully-opaque heart on top still stands out against it.
                            .background(heartTint.copy(alpha = 0.15f)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_favorite_default_24dp),
                            contentDescription = stringResource(R.string.descr_card_starred_station),
                            tint = heartTint,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
                }

                AnimatedVisibility(
                    visible = isEditorOpen,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    StationEditor(
                        station = station,
                        editStreamUrisEnabled = editStreamUrisEnabled,
                        onSave = onSave,
                        onCancel = onCancelEdit,
                        onPlaceOnHomeScreen = onPlaceOnHomeScreen,
                    )
                }
            }

            // The station that is playing is marked by an accent bar down the trailing
            // edge. A sibling of the whole Column -- not just the header row -- so it still
            // reaches the bottom of the card while the inline editor is open.
            if (isPlaying) {
                // matchParentSize takes the column's measured height, which fillMaxHeight
                // alone cannot do here: the Box wraps its content, so there is no
                // bounded height to fill and the bar would collapse to nothing.
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(8.dp)
                            .background(accentColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun StationCover(station: Station, modifier: Modifier = Modifier) {
    val description = "${stringResource(R.string.descr_player_station_image)}: ${station.name}"
    // Stations without their own artwork fall back to the app's default station image
    // rather than showing an empty colored square.
    val placeholder = painterResource(R.drawable.ic_default_station_image_72dp)
    val context = LocalContext.current
    // Kept across recompositions: the list rebuilds this row whenever playback state or the
    // collection changes, and assembling a request per row per pass is work the scroll can
    // feel. The file keeps its name when a station image is replaced, so the modification
    // date is folded into the cache key -- otherwise Coil would serve the old bitmap.
    val request = remember(station.smallImage, station.modificationDate) {
        ImageRequest.Builder(context)
            .data(station.smallImage.ifEmpty { null })
            .memoryCacheKey("${station.smallImage}:${station.modificationDate.time}")
            .diskCacheKey("${station.smallImage}:${station.modificationDate.time}")
            .build()
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (station.imageColor != -1) Color(station.imageColor)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .semantics { contentDescription = description },
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun StationEditor(
    station: Station,
    editStreamUrisEnabled: Boolean,
    onSave: (name: String, streamUri: String) -> Unit,
    onCancel: () -> Unit,
    onPlaceOnHomeScreen: () -> Unit,
) {
    var name by rememberSaveable(station.uuid) { mutableStateOf(station.name) }
    var streamUri by rememberSaveable(station.uuid) { mutableStateOf(station.getStreamUri()) }
    var streamUriAccepted by remember(station.uuid) { mutableStateOf(true) }

    // Mirrors the old handleStationUriInput: the save button stays disabled until the
    // edited address has been confirmed to serve a supported stream type.
    LaunchedEffect(streamUri, editStreamUrisEnabled) {
        if (!editStreamUrisEnabled || streamUri == station.getStreamUri()) {
            streamUriAccepted = true
            return@LaunchedEffect
        }
        streamUriAccepted = false
        if (!streamUri.startsWith("http")) return@LaunchedEffect
        delay(400.milliseconds) // debounce while the user is still typing
        val contentType = withContext(Dispatchers.IO) {
            NetworkHelper.detectContentTypeSuspended(streamUri).type.lowercase(Locale.getDefault())
        }
        streamUriAccepted = Keys.MIME_TYPES_MPEG.contains(contentType) ||
            Keys.MIME_TYPES_OGG.contains(contentType) ||
            Keys.MIME_TYPES_AAC.contains(contentType) ||
            Keys.MIME_TYPES_HLS.contains(contentType)
    }

    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.dialog_edit_station_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (editStreamUrisEnabled) {
            OutlinedTextField(
                value = streamUri,
                onValueChange = { streamUri = it },
                label = { Text(stringResource(R.string.dialog_edit_stream_uri)) },
                singleLine = true,
                isError = !streamUriAccepted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            // No image button here anymore -- tapping the cover above opens the picker.
            IconButton(onClick = onPlaceOnHomeScreen) {
                Icon(
                    painter = painterResource(R.drawable.ic_home_24dp),
                    contentDescription = stringResource(R.string.shortcut_last_station_short_label),
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    painter = painterResource(R.drawable.ic_clear_24dp),
                    contentDescription = stringResource(R.string.dialog_generic_button_cancel),
                )
            }
            IconButton(
                onClick = { onSave(name, streamUri) },
                enabled = streamUriAccepted,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_24dp),
                    contentDescription = stringResource(R.string.dialog_generic_button_okay),
                )
            }
        }
    }
}
