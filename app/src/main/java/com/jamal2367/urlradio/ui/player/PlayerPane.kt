/*
 * PlayerPane.kt
 * The player: a compact bar that expands into the full controls
 *
 * Replaces bottom_sheet_playback_controls.xml and the view wiring in LayoutHolder.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.DateTimeHelper
import com.jamal2367.urlradio.playback.PlaybackUiState
import com.jamal2367.urlradio.playback.sanitizedMetadata

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerPane(
    station: Station,
    playback: PlaybackUiState,
    expanded: Boolean,
    metadataIndex: Int,
    onToggleExpanded: () -> Unit,
    onSetExpanded: (Boolean) -> Unit,
    onTogglePlayback: () -> Unit,
    onPreviousMetadata: () -> Unit,
    onNextMetadata: () -> Unit,
    onCopy: (CharSequence) -> Unit,
    onCopyFullHistory: () -> Unit,
    onShare: () -> Unit,
    onStartSleepTimer: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Stations that send no track title fall back to their own name rather than to a blank line.
    val shownMetadata = playback.metadataHistory.getOrNull(metadataIndex)
        ?.sanitizedMetadata().orEmpty().ifEmpty { station.name }
    val playbackButtonDescription = stringResource(R.string.descr_player_playback_button)
    val accentColor = if (playback.isPlaying && station.imageColor != -1) Color(station.imageColor)
    else MaterialTheme.colorScheme.primary

    // The player stays visually silent under the finger: no ripple, no hover or focus
    // highlight, anywhere inside it. A null RippleConfiguration switches off the ripple that
    // the Material components (the play button, the icon buttons) request themselves; the
    // plain clickable below additionally pass indication = null, which is what the theme's
    // default indication would otherwise supply.
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Column(
            // Dragging the player up opens it, dragging down closes it. Sits on the whole panel
            // rather than the compact row so a downward swipe anywhere over the expanded
            // controls closes it too. The threshold is in pixels, so it is compared against the
            // drag total rather than a dp.
            modifier = modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var dragTotal = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onDragEnd = {
                            if (dragTotal < -SWIPE_THRESHOLD_PX) onSetExpanded(true)
                            else if (dragTotal > SWIPE_THRESHOLD_PX) onSetExpanded(false)
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragTotal += amount
                        },
                    )
                }
        ) {
            // ---- compact row: always visible ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Less horizontal inset than before: the artwork sits closer to the start
                // edge and the play button closer to the end edge instead of both being
                // pulled in toward the center.
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onToggleExpanded,
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                StationArtwork(station = station, modifier = Modifier.size(56.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        // The old view enabled marquee only while playing; keep that.
                        modifier = if (playback.isPlaying) Modifier.basicMarquee() else Modifier,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (playback.isPlaying) playback.currentMetadata.ifEmpty { station.name }
                        else station.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box(contentAlignment = Alignment.Center) {
                    if (playback.isBuffering) {
                        ContainedLoadingIndicator()
                    } else {
                        FilledIconButton(
                            onClick = onTogglePlayback,
                            // Same light-circle-plus-full-color-icon treatment as the starred
                            // heart in the station list: a 33%-opacity wash of the accent color
                            // behind it, the icon itself at full strength on top.
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = accentColor.copy(alpha = 0.33f),
                                contentColor = accentColor,
                            ),
                        ) {
                            if (playback.isPlaying) {
                                EqualizerIcon(
                                    color = LocalContentColor.current,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .semantics {
                                            contentDescription = playbackButtonDescription
                                        },
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_player_play_symbol_42dp),
                                    contentDescription = playbackButtonDescription,
                                )
                            }
                        }
                    }
                }
            }

            // ---- expanded controls ----
            // Styled after the settings screen's SettingsGroup/SettingsRow: a rounded card a
            // shade lighter than the player's own surface, each entry a circular icon badge
            // plus a label/value pair, so the two places share one visual language instead of
            // the player looking like a different, older piece of the app.
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    PlayerInfoGroup {
                        PlayerInfoRow(
                            icon = R.drawable.ic_network_check_24dp,
                            label = stringResource(R.string.player_sheet_h2_stream_url),
                            value = station.getStreamUri(),
                            onClick = { onCopy(station.getStreamUri()) },
                            marqueeValue = true,
                            trailing = {
                                IconButton(onClick = onShare) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_share_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                        )

                        PlayerInfoRow(
                            icon = R.drawable.ic_music_note_24dp,
                            label = stringResource(R.string.player_sheet_h2_station_metadata),
                            value = shownMetadata,
                            onClick = { onCopy(shownMetadata) },
                            onLongClick = onCopyFullHistory,
                            trailing = {
                                Row {
                                    IconButton(onClick = onPreviousMetadata) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_chevron_left_24dp),
                                            contentDescription = stringResource(R.string.descr_expanded_player_metadata_previous_button),
                                        )
                                    }
                                    IconButton(onClick = onNextMetadata) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_chevron_right_24dp),
                                            contentDescription = stringResource(R.string.descr_expanded_player_metadata_next_button),
                                        )
                                    }
                                    IconButton(onClick = { onCopy(shownMetadata) }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_copy_content_24dp),
                                            contentDescription = stringResource(R.string.descr_expanded_player_metadata_copy_button),
                                        )
                                    }
                                }
                            },
                        )

                        // Codec/bitrate on the left, sleep timer opposite it on the right --
                        // a plain control strip rather than another icon-badged row, since
                        // neither half is a single labeled value.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            // No end padding: the sleep timer's own IconButtons already carry
                            // a 48dp touch target, so the row's own inset was just extra
                            // space pushing them further from the edge than that.
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            val bitrateText = bitrateLabel(station)
                            Text(
                                text = bitrateText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        interactionSource = null,
                                        indication = null,
                                        onClick = { onCopy(bitrateText) },
                                    )
                                    .padding(vertical = 8.dp),
                            )

                            SleepTimerControls(
                                isPlaying = playback.isPlaying,
                                remainingMillis = playback.sleepTimerRemaining,
                                onStart = onStartSleepTimer,
                                onCancel = onCancelSleepTimer,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val SWIPE_THRESHOLD_PX = 40f

/* Builds the "codec | bitrate Kb/s" line. M3U and PLS playlists carry neither, so the
   row is left out entirely for them -- same rule the old LayoutHolder used. */
private fun bitrateLabel(station: Station): String = when {
    station.codec.isEmpty() -> ""
    station.bitrate == 0 -> station.codec
    else -> "${station.codec} | ${station.bitrate} Kb/s"
}

/* Groups related entries into one rounded card -- the player's equivalent of the settings
   screen's SettingsGroup, one shade lighter than the player's own surface so it still reads
   as sitting on top of it. */
@Composable
private fun PlayerInfoGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

/* The player's equivalent of the settings screen's SettingsRow: a circular icon badge, then
   a label/value pair. Unlike a settings row the whole thing is also copy-on-tap, so the
   label and value keep their own combinedClickable rather than the row claiming one click. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlayerInfoRow(
    icon: Int,
    label: String,
    value: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    // The stream URL is the one value here long enough to routinely not fit -- marquee lets
    // it scroll through on its own rather than being cut down to a fragment with an ellipsis.
    marqueeValue: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // No end padding: trailing's own IconButtons already carry a 48dp touch target, so
        // the row's own inset was just extra space pushing them further from the edge than
        // that -- share, copy and the prev/next controls all sit closer to it now.
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(10.dp)
                    .size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
                .combinedClickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (marqueeValue) it.basicMarquee() else it },
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun SleepTimerControls(
    isPlaying: Boolean,
    remainingMillis: Long,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The start button only appears during playback, exactly as before: a sleep timer
        // without playback has nothing to stop.
        if (isPlaying) {
            IconButton(onClick = onStart) {
                Icon(
                    painter = painterResource(R.drawable.ic_sleep_timer_24dp),
                    contentDescription = stringResource(R.string.descr_expanded_player_sleep_timer_start_button),
                )
            }
        }
        if (remainingMillis > 0L) {
            Text(
                text = DateTimeHelper.convertToHoursMinutesSeconds(remainingMillis),
                style = MaterialTheme.typography.labelLarge,
            )
            IconButton(onClick = onCancel) {
                Icon(
                    painter = painterResource(R.drawable.ic_clear_24dp),
                    contentDescription = stringResource(R.string.descr_expanded_player_sleep_timer_cancel_button),
                )
            }
        }
    }
}

/* Three bars bouncing out of phase, standing in for the old ic_audio_waves_animated AVD
   that lived on the play button while a station was playing. */
@Composable
private fun EqualizerIcon(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val bars = listOf(420, 560, 500).mapIndexed { index, duration ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, delayMillis = index * 90, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "equalizerBar$index",
        )
    }

    // Drawn rather than laid out. The bar heights are read here, inside the draw phase, so a
    // frame of this animation costs one redraw of the icon. Sizing three Boxes by the same
    // values read them while composing, which recomposed and re-measured the icon on every
    // frame for as long as a station was playing -- work the main thread was doing while the
    // station list was trying to scroll.
    Canvas(modifier = modifier) {
        val barWidth = 4.dp.toPx()
        val gap = 2.dp.toPx()
        val corner = CornerRadius(1.dp.toPx())
        // Centred, not left-aligned: the three bars are narrower than the 24dp icon slot, so
        // starting at zero pushed the whole group off-center inside the round button.
        var x = (size.width - (barWidth * bars.size + gap * (bars.size - 1))) / 2f
        bars.forEach { bar ->
            val barHeight = size.height * bar.value
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
            x += barWidth + gap
        }
    }
}

@Composable
private fun StationArtwork(station: Station, modifier: Modifier = Modifier) {
    // Same default station image the list uses for stations without their own artwork.
    val placeholder = painterResource(R.drawable.ic_default_station_image_72dp)
    val context = LocalContext.current
    // The player recomposes on every metadata and sleep-timer tick, so the request is built
    // once per station rather than once per pass.
    val request = remember(station.smallImage, station.modificationDate) {
        ImageRequest.Builder(context)
            .data(station.smallImage.ifEmpty { null })
            .memoryCacheKey("${station.smallImage}:${station.modificationDate.time}")
            .build()
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (station.imageColor != -1) Color(station.imageColor)
                else MaterialTheme.colorScheme.surfaceVariant
            ),
    ) {
        AsyncImage(
            model = request,
            contentDescription = "${stringResource(R.string.descr_player_station_image)}: ${station.name}",
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
