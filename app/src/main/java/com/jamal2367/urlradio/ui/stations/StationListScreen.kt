/*
 * StationListScreen.kt
 * The station list, its swipe actions, drag-to-reorder and the onboarding state
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.ui.stations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station

/**
 * @param onDeleteRequest asks the host to confirm before anything is removed. Deletion never
 *   happens straight from the swipe -- that was one of the ways the old list could drop a
 *   station without cleaning up its images or saving the collection.
 */
@Composable
fun StationListScreen(
    stations: List<Station>,
    playingStationUuid: String,
    expandedStationUuid: String,
    editStationsEnabled: Boolean,
    editStreamUrisEnabled: Boolean,
    showOnboarding: Boolean,
    hasActiveDownloads: Boolean,
    onTogglePlayback: (Station) -> Unit,
    onToggleEditor: (Station) -> Unit,
    onSaveStation: (Station, String, String) -> Unit,
    onCancelEdit: () -> Unit,
    onChangeImage: (Station) -> Unit,
    onPlaceOnHomeScreen: (Station) -> Unit,
    onDeleteRequest: (Station) -> Unit,
    onToggleStarred: (Station) -> Unit,
    onMove: (Int, Int) -> Boolean,
    onMoveFinished: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(visible = hasActiveDownloads) {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (showOnboarding) {
            OnboardingPane(modifier = Modifier.fillMaxSize())
            return@Column
        }

        val listState = rememberLazyListState()

        // Drag-to-reorder state. The dragged row is tracked by uuid rather than by index:
        // the index changes underneath us on every successful swap, and reading a stale one
        // was why only the row that happened to start at the top could be moved.
        var draggingUuid by remember { mutableStateOf<String?>(null) }
        var dragOffsetY by remember { mutableFloatStateOf(0f) }

        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = stations,
                key = { _, station -> station.uuid },
            ) { index, station ->
                val isEditorOpen = station.uuid == expandedStationUuid
                val isDragging = station.uuid == draggingUuid

                // Reordering is disabled while an editor is open, matching the old
                // isLongPressDragEnabled() rule. Sits on the row as a whole; the cover and
                // the station name claim their own long-press for opening the editor, so
                // this only ever fires on the free area around them.
                val dragModifier = if (isEditorOpen) Modifier else Modifier.pointerInput(station.uuid) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingUuid = station.uuid
                            dragOffsetY = 0f
                        },
                        onDragEnd = {
                            draggingUuid = null
                            dragOffsetY = 0f
                            onMoveFinished()
                        },
                        onDragCancel = {
                            draggingUuid = null
                            dragOffsetY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount.y

                            // Where the dragged row is actually drawn right now: its laid-out
                            // position plus the offset the finger has added.
                            val items = listState.layoutInfo.visibleItemsInfo
                            val dragged = items.firstOrNull { it.key == draggingUuid } ?: return@detectDragGesturesAfterLongPress
                            val draggedCentre = dragged.offset + dragged.size / 2 + dragOffsetY

                            // Swap with whichever row that centre now sits inside. Comparing
                            // against real layout positions handles rows of differing height
                            // and lets a fast drag cross several of them.
                            val target = items.firstOrNull { other ->
                                other.index != dragged.index &&
                                    draggedCentre >= other.offset &&
                                    draggedCentre <= other.offset + other.size
                            } ?: return@detectDragGesturesAfterLongPress

                            if (onMove(dragged.index, target.index)) {
                                // The row is about to be laid out at the target's position,
                                // so drop the same amount from the offset and the row stays
                                // put under the finger.
                                dragOffsetY -= (target.offset - dragged.offset)
                            }
                        },
                    )
                }

                SwipeableStationRow(
                    station = station,
                    isPlaying = station.uuid == playingStationUuid,
                    isEditorOpen = isEditorOpen,
                    editStationsEnabled = editStationsEnabled,
                    editStreamUrisEnabled = editStreamUrisEnabled,
                    onTogglePlayback = { onTogglePlayback(station) },
                    onToggleEditor = { onToggleEditor(station) },
                    onSave = { name, uri -> onSaveStation(station, name, uri) },
                    onCancelEdit = onCancelEdit,
                    onChangeImage = { onChangeImage(station) },
                    onPlaceOnHomeScreen = { onPlaceOnHomeScreen(station) },
                    onDeleteRequest = { onDeleteRequest(station) },
                    onToggleStarred = { onToggleStarred(station) },
                    dragModifier = dragModifier,
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f },
                )
            }
        }
    }
}

@Composable
private fun SwipeableStationRow(
    station: Station,
    isPlaying: Boolean,
    isEditorOpen: Boolean,
    editStationsEnabled: Boolean,
    editStreamUrisEnabled: Boolean,
    onTogglePlayback: () -> Unit,
    onToggleEditor: () -> Unit,
    onSave: (String, String) -> Unit,
    onCancelEdit: () -> Unit,
    onChangeImage: () -> Unit,
    onPlaceOnHomeScreen: () -> Unit,
    onDeleteRequest: () -> Unit,
    onToggleStarred: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                // Swipe towards the end (left in LTR) asks to delete. The row always
                // springs back; the actual removal waits for the confirmation dialog.
                SwipeToDismissBoxValue.EndToStart -> {
                    onDeleteRequest()
                    false
                }
                // Swipe towards the start (right in LTR) toggles the favourite mark.
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggleStarred()
                    false
                }

                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        // dismissDirection follows the raw swipe offset, unlike targetValue which only
        // flips once the row is dragged past the threshold. Keying the background on the
        // latter is what made the colour appear only from halfway across.
        backgroundContent = { SwipeBackground(dismissState.dismissDirection) },
        modifier = modifier,
    ) {
        StationCard(
            station = station,
            isPlaying = isPlaying,
            isEditorOpen = isEditorOpen,
            editStationsEnabled = editStationsEnabled,
            editStreamUrisEnabled = editStreamUrisEnabled,
            onTogglePlayback = onTogglePlayback,
            onToggleEditor = onToggleEditor,
            onSave = onSave,
            onCancelEdit = onCancelEdit,
            onChangeImage = onChangeImage,
            onPlaceOnHomeScreen = onPlaceOnHomeScreen,
            dragModifier = dragModifier,
        )
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val isDelete = direction == SwipeToDismissBoxValue.EndToStart
    val isStar = direction == SwipeToDismissBoxValue.StartToEnd
    if (!isDelete && !isStar) return

    val background = if (isDelete) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.primaryContainer
    val tint = if (isDelete) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isDelete) Arrangement.End else Arrangement.Start,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .padding(horizontal = 24.dp),
    ) {
        Icon(
            painter = painterResource(
                if (isDelete) R.drawable.ic_remove_circle_24dp else R.drawable.ic_favorite_24dp
            ),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun OnboardingPane(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.onboarding_app_get_started),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            painter = painterResource(R.drawable.ic_audio_listening),
            contentDescription = stringResource(R.string.descr_app_icon),
            tint = androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier
                .padding(top = 32.dp)
                .size(192.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_app_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
