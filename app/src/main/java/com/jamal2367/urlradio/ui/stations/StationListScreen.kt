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
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

/** How close a dragged row has to get to the top/bottom of the viewport to auto-scroll it. */
private val DragAutoScrollEdge = 64.dp

/** Pixels of underlying scroll per pixel the dragged row sits inside the edge strip. */
private const val DragAutoScrollSpeedFactor = 0.5f

private const val DragAutoScrollIntervalMillis = 16L

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
    header: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(visible = hasActiveDownloads) {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (showOnboarding) {
            // Nothing here scrolls, so there is no "sticky" to avoid -- the header just sits
            // above it like any other fixed screen. It carries no horizontal padding of its
            // own (see StationsTopBar), hence the explicit padding here.
            Box(modifier = Modifier.padding(horizontal = 12.dp)) { header() }
            OnboardingPane(modifier = Modifier.fillMaxSize())
            return@Column
        }

        val listState = rememberLazyListState()

        // Drag-to-reorder state.
        //
        // Everything is measured against the slot the row started in: dragStartOffset plus
        // the distance the finger has traveled since. That total is never corrected when a
        // swap goes through, which is the point. Correcting it -- subtracting the distance
        // between the two slots on every move -- meant a move that the collection had already
        // applied, but that the list had not been laid out for yet, fed a position back into
        // the next drag event that no longer described anything on screen. The row then
        // swapped again off that stale reading, and again, which is what made it jump around.
        var draggedIndex by remember { mutableStateOf<Int?>(null) }
        var draggedDistance by remember { mutableFloatStateOf(0f) }
        var dragStartOffset by remember { mutableIntStateOf(0) }
        var dragStartSize by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current

        LaunchedEffect(draggedIndex) {
            if (draggedIndex == null) return@LaunchedEffect
            val edgePx = with(density) { DragAutoScrollEdge.toPx() }
            while (isActive) {
                val viewportHeight = listState.layoutInfo.viewportSize.height
                val top = dragStartOffset + draggedDistance
                val bottom = top + dragStartSize
                val intoTopEdge = (edgePx - top).coerceAtLeast(0f)
                val intoBottomEdge = (edgePx - (viewportHeight - bottom)).coerceAtLeast(0f)
                val scrollAmount = when {
                    intoTopEdge > 0f -> -intoTopEdge
                    intoBottomEdge > 0f -> intoBottomEdge
                    else -> 0f
                }
                if (scrollAmount != 0f) {
                    listState.scrollBy(scrollAmount * DragAutoScrollSpeedFactor)
                }
                delay(DragAutoScrollIntervalMillis.milliseconds)
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = draggedIndex == null,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { header() }

            // The header above is lazy item 0, which pushes every station row one slot further
            // down in the list's own (absolute) item indices. draggedIndex, `index` from
            // itemsIndexed and the indices onMove expects all stay in "stations list" terms --
            // this offset is only added back in when reading or matching against
            // visibleItemsInfo, which counts the header.
            val stationsIndexOffset = 1

            itemsIndexed(
                items = stations,
                key = { _, station -> station.uuid },
            ) { index, station ->
                val isEditorOpen = station.uuid == expandedStationUuid
                val isDragging = draggedIndex == index

                // Reordering is disabled while an editor is open, matching the old
                // isLongPressDragEnabled() rule. Sits on the row as a whole; the cover and
                // the station name claim their own long-press for opening the editor, so
                // this only ever fires on the free area around them.
                val dragModifier =
                    if (isEditorOpen) Modifier else Modifier.pointerInput(station.uuid) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val info = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == station.uuid }
                                draggedIndex = info?.let { it.index - stationsIndexOffset } ?: index
                                dragStartOffset = info?.offset ?: 0
                                dragStartSize = info?.size ?: 0
                                draggedDistance = 0f
                            },
                            onDragEnd = {
                                draggedIndex = null
                                draggedDistance = 0f
                                onMoveFinished()
                            },
                            onDragCancel = {
                                draggedIndex = null
                                draggedDistance = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggedDistance += dragAmount.y

                                val from = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                val fromLayoutIndex = from + stationsIndexOffset
                                val items = listState.layoutInfo.visibleItemsInfo
                                val dragged = items.firstOrNull { it.index == fromLayoutIndex }
                                    ?: return@detectDragGesturesAfterLongPress

                                // The strip the row now covers on screen.
                                val top = dragStartOffset + draggedDistance
                                val bottom = top + dragStartSize
                                val movingDown = top > dragged.offset

                                // A row is taken over only once it has been cleared completely --
                                // downwards past its bottom edge, upwards past its top one. Going
                                // by the midpoint instead let a row swap back and forth while the
                                // finger sat still on the boundary. The header (layout index 0)
                                // can never be a target -- there is nothing to swap it with.
                                items.firstOrNull { other ->
                                    other.index != fromLayoutIndex &&
                                            other.index >= stationsIndexOffset &&
                                            other.offset + other.size >= top &&
                                            other.offset <= bottom &&
                                            (
                                                    if (movingDown) bottom > other.offset + other.size
                                                    else top < other.offset
                                                    )
                                }?.let { target ->
                                    // moveStation refuses to mix favourites with the rest, so the
                                    // index only follows the row where the move was accepted.
                                    val targetIndex = target.index - stationsIndexOffset
                                    if (onMove(from, targetIndex)) draggedIndex = targetIndex
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
                    // Everything here is scoped to an actual drag. Ordinary scrolling gets a
                    // bare Modifier: no render layer, no z-order, and above all no placement
                    // animation, so the reorder feature costs a plain scroll nothing at all.
                    modifier = when {
                        isDragging -> Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                // Read at draw time, not during composition: the row keeps
                                // following the finger through the frames where the list is
                                // still settling into the new order. Once it has been laid
                                // out in the slot it was dragged to, its own offset cancels
                                // the distance out and the translation shrinks to nothing.
                                val laidOut = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == index + stationsIndexOffset }
                                    ?.offset ?: dragStartOffset
                                translationY = dragStartOffset + draggedDistance - laidOut
                            }

                        // Placement only, and only while a row is actually being dragged: the
                        // rows making way for it slide into their new slot instead of
                        // snapping. The fade animateItem otherwise adds ran for every row at
                        // once when the collection finished loading.
                        draggedIndex != null ->
                            Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)

                        else -> Modifier
                    },
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
    val dismissState = rememberSwipeToDismissBoxState()

    // Neither swipe ever removes the row: a swipe is a shortcut for an action, and the card
    // returns to its place afterward.
    //
    // This used to be a confirmValueChange that vetoed every state change, which is
    // deprecated -- the recommendation is to leave disallowed states out of the anchor set
    // instead, and an anchor set of one would mean the row could not be swiped at all. So the
    // swipe is let through and undone here: reset animates the card back from wherever the
    // dismiss left it. settledValue rather than currentValue, so the action fires once the
    // gesture is over rather than while the finger is still moving across the row.
    LaunchedEffect(dismissState.settledValue) {
        when (dismissState.settledValue) {
            // Towards the end (left in LTR) asks to delete. The removal itself waits for the
            // confirmation dialog.
            SwipeToDismissBoxValue.EndToStart -> {
                onDeleteRequest()
                dismissState.reset()
            }
            // Towards the start (right in LTR) toggles the favourite mark.
            SwipeToDismissBoxValue.StartToEnd -> {
                onToggleStarred()
                dismissState.reset()
            }

            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        // dismissDirection follows the raw swipe offset, unlike targetValue which only
        // flips once the row is dragged past the threshold. Keying the background on the
        // latter is what made the color appear only from halfway across.
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
