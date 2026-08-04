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
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.DateTimeHelper
import com.jamal2367.urlradio.playback.PlaybackUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerPane(
    station: Station,
    playback: PlaybackUiState,
    expanded: Boolean,
    metadataIndex: Int,
    onToggleExpanded: () -> Unit,
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
    val shownMetadata = playback.metadataHistory.getOrNull(metadataIndex)
        ?: station.name.ifEmpty { "" }

    Column(modifier = modifier.fillMaxWidth()) {
        // ---- compact row: always visible ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onToggleExpanded)
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    FilledIconButton(onClick = onTogglePlayback) {
                        Icon(
                            painter = painterResource(
                                if (playback.isPlaying) R.drawable.ic_player_stop_symbol_36dp
                                else R.drawable.ic_player_play_symbol_42dp
                            ),
                            contentDescription = stringResource(R.string.descr_player_playback_button),
                        )
                    }
                }
            }
        }

        // ---- expanded controls ----
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                HorizontalDivider()

                LabelledValue(
                    label = stringResource(R.string.player_sheet_h2_stream_url),
                    value = station.getStreamUri(),
                    onClick = { onCopy(station.getStreamUri()) },
                    trailing = {
                        IconButton(onClick = onShare) {
                            Icon(
                                painter = painterResource(R.drawable.ic_share_24dp),
                                contentDescription = null,
                            )
                        }
                    },
                )

                LabelledValue(
                    label = stringResource(R.string.player_sheet_h2_station_metadata),
                    value = shownMetadata,
                    onClick = { onCopy(shownMetadata) },
                    onLongClick = onCopyFullHistory,
                    leading = {
                        IconButton(onClick = onPreviousMetadata) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_left_24dp),
                                contentDescription = stringResource(R.string.descr_expanded_player_metadata_previous_button),
                            )
                        }
                    },
                    trailing = {
                        Row {
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

                val bitrateText = bitrateLabel(station)
                if (bitrateText.isNotEmpty()) {
                    Text(
                        text = bitrateText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .combinedClickable(onClick = { onCopy(bitrateText) })
                            .padding(vertical = 8.dp),
                    )
                }

                SleepTimerRow(
                    isPlaying = playback.isPlaying,
                    remainingMillis = playback.sleepTimerRemaining,
                    onStart = onStartSleepTimer,
                    onCancel = onCancelSleepTimer,
                )
            }
        }
    }
}

/* Builds the "codec | bitrate kbps" line. M3U and PLS playlists carry neither, so the
   row is left out entirely for them -- same rule the old LayoutHolder used. */
private fun bitrateLabel(station: Station): String = when {
    station.codec.isEmpty() -> ""
    station.bitrate == 0 -> station.codec
    else -> "${station.codec} | ${station.bitrate}kbps"
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LabelledValue(
    label: String,
    value: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun SleepTimerRow(
    isPlaying: Boolean,
    remainingMillis: Long,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
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

@Composable
private fun StationArtwork(station: Station, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (station.imageColor != -1) Color(station.imageColor)
                else MaterialTheme.colorScheme.surfaceVariant
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(station.smallImage)
                .memoryCacheKey("${station.smallImage}:${station.modificationDate.time}")
                .build(),
            contentDescription = "${stringResource(R.string.descr_player_station_image)}: ${station.name}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
