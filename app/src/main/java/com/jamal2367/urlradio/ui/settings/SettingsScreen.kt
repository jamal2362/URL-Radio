/*
 * SettingsScreen.kt
 * The settings screen. Replaces SettingsFragment and androidx.preference.
 *
 * Every entry writes to the same SharedPreferences key it used before, so an update keeps
 * the user's existing settings.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.ui.settings

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.alarm.AlarmSettings
import com.jamal2367.urlradio.alarm.RadioAlarm
import com.jamal2367.urlradio.recording.RecordingFormat
import com.jamal2367.urlradio.ui.dialogs.ConfirmDialog
import com.jamal2367.urlradio.ui.dialogs.WideDialogModifier
import com.jamal2367.urlradio.ui.dialogs.WideDialogProperties
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SettingsCallbacks(
    val onThemeSelected: (String) -> Unit,
    val onUpdateStationImages: () -> Unit,
    val onRemoveAllStations: () -> Unit,
    val onImportPlaylist: () -> Unit,
    val onExportM3u: () -> Unit,
    val onExportPls: () -> Unit,
    val onBackup: () -> Unit,
    val onRestore: () -> Unit,
    val onLargeBufferChanged: (Boolean) -> Unit,
    val onRecordingFormatChanged: (RecordingFormat) -> Unit,
    val onRecordingBitrateChanged: (Int) -> Unit,
    val onAlarmChanged: (AlarmSettings) -> Unit,
    val onRequestAlarmPermission: () -> Unit,
    val onRequestNotificationPermission: () -> Unit,
    val onStationSearchChanged: (Boolean) -> Unit,
    val onEditStationsChanged: (Boolean) -> Unit,
    val onEditStreamUrisChanged: (Boolean) -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onCopyVersion: (String) -> Unit,
)

@Composable
fun SettingsScreen(
    versionSummary: String,
    themeSelection: String,
    themeLabel: String,
    largeBuffer: Boolean,
    recordingFormat: RecordingFormat,
    recordingBitrate: Int,
    alarm: AlarmSettings,
    /** Whether the app may post notifications - needed for the alarm's "Off" and "Snooze". */
    notificationsAllowed: Boolean,
    stationSearchEnabled: Boolean,
    /** UUID and name of every station, for picking the one to wake up with. */
    stations: List<Pair<String, String>>,
    editStations: Boolean,
    editStreamUris: Boolean,
    callbacks: SettingsCallbacks,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showExportFormatDialog by remember { mutableStateOf(false) }
    var showRemoveAllDialog by remember { mutableStateOf(false) }
    var showRecordingFormatDialog by remember { mutableStateOf(false) }
    var showRecordingBitrateDialog by remember { mutableStateOf(false) }
    var showAlarmTimeDialog by remember { mutableStateOf(false) }
    var showAlarmDaysDialog by remember { mutableStateOf(false) }
    var showAlarmStationDialog by remember { mutableStateOf(false) }
    var showAlarmSnoozeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.pref_app_version_title),
                    summary = versionSummary,
                    icon = R.drawable.ic_info_24dp,
                    onClick = { callbacks.onCopyVersion(versionSummary) },
                )
                SettingsRow(
                    title = stringResource(R.string.pref_license_title),
                    summary = stringResource(R.string.pref_license_summary),
                    icon = R.drawable.ic_library_24dp,
                    onClick = { callbacks.onOpenUrl(LICENSE_URL) },
                )
            }
        }

        item { CategoryHeader(stringResource(R.string.pref_general_title)) }
        item {
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.pref_theme_selection_title),
                    summary = "${stringResource(R.string.pref_theme_selection_summary)} $themeLabel",
                    icon = R.drawable.ic_brush_24dp,
                    onClick = { showThemeDialog = true },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.pref_station_search_title),
                    summary = stringResource(
                        if (stationSearchEnabled) R.string.pref_station_search_summary_enabled
                        else R.string.pref_station_search_summary_disabled
                    ),
                    icon = R.drawable.ic_search_24dp,
                    checked = stationSearchEnabled,
                    onCheckedChange = callbacks.onStationSearchChanged,
                )
            }
        }

        item { CategoryHeader(stringResource(R.string.pref_maintenance_title)) }
        item {
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.pref_update_station_images_title),
                    summary = stringResource(R.string.pref_update_station_images_summary),
                    icon = R.drawable.ic_image_24dp,
                    onClick = callbacks.onUpdateStationImages,
                )
                SettingsRow(
                    title = stringResource(R.string.pref_remove_all_stations_title),
                    summary = stringResource(R.string.pref_remove_all_stations_summary),
                    icon = R.drawable.ic_remove_circle_24dp,
                    onClick = { showRemoveAllDialog = true },
                )
            }
        }

        item { CategoryHeader(stringResource(R.string.pref_backup_import_export_title)) }
        item {
            SettingsGroup {
                // One entry for both playlist formats. Which of the two is written is asked
                // in a dialog rather than through two near-identical rows.
                SettingsRow(
                    title = stringResource(R.string.pref_playlist_export_title),
                    summary = stringResource(R.string.pref_playlist_export_summary),
                    icon = R.drawable.ic_playlist_export_24dp,
                    onClick = { showExportFormatDialog = true },
                )
                SettingsRow(
                    title = stringResource(R.string.pref_playlist_import_title),
                    summary = stringResource(R.string.pref_playlist_import_summary),
                    icon = R.drawable.ic_playlist_add_24dp,
                    onClick = callbacks.onImportPlaylist,
                )
                SettingsRow(
                    title = stringResource(R.string.pref_station_export_title),
                    summary = stringResource(R.string.pref_station_export_summary),
                    icon = R.drawable.ic_download_24dp,
                    onClick = callbacks.onBackup,
                )
                SettingsRow(
                    title = stringResource(R.string.pref_station_restore_title),
                    summary = stringResource(R.string.pref_station_restore_summary),
                    icon = R.drawable.ic_upload_24dp,
                    onClick = callbacks.onRestore,
                )
            }
        }

        item { CategoryHeader(stringResource(R.string.pref_alarm_title)) }
        item {
            // Android 12 lets the user take the right to set exact alarms away again.
            val permissionMissing: Boolean = alarm.enabled && !RadioAlarm.canSchedule(context)
            val nextTrigger: Long? = RadioAlarm.nextTrigger(alarm)
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.pref_alarm_enabled_title),
                    summary = when {
                        permissionMissing -> stringResource(R.string.pref_alarm_permission_missing)
                        nextTrigger != null -> stringResource(R.string.pref_alarm_next, formatAlarmTrigger(context, nextTrigger))
                        else -> stringResource(R.string.pref_alarm_off)
                    },
                    icon = R.drawable.ic_alarm_24dp,
                    checked = alarm.enabled,
                    onCheckedChange = { callbacks.onAlarmChanged(alarm.copy(enabled = it)) },
                )
                if (permissionMissing) {
                    SettingsRow(
                        title = stringResource(R.string.pref_alarm_permission_title),
                        summary = stringResource(R.string.pref_alarm_permission_summary),
                        icon = R.drawable.ic_alarm_24dp,
                        onClick = callbacks.onRequestAlarmPermission,
                    )
                }
                // Without notifications the alarm still rings, but "Off" and "Snooze" only
                // show in the media controls - worth a word while the alarm is on.
                if (alarm.enabled && !notificationsAllowed) {
                    SettingsRow(
                        title = stringResource(R.string.pref_alarm_permission_title),
                        summary = stringResource(R.string.pref_alarm_notification_hint),
                        icon = R.drawable.ic_alarm_24dp,
                        onClick = callbacks.onRequestNotificationPermission,
                    )
                }
                SettingsRow(
                    title = stringResource(R.string.pref_alarm_time_title),
                    summary = formatAlarmTime(context, alarm.hour, alarm.minute),
                    icon = R.drawable.ic_sleep_timer_24dp,
                    onClick = { showAlarmTimeDialog = true },
                )
                SettingsRow(
                    title = stringResource(R.string.pref_alarm_days_title),
                    summary = alarmDaysLabel(alarm.days),
                    icon = R.drawable.ic_alarm_24dp,
                    onClick = { showAlarmDaysDialog = true },
                )
                SettingsRow(
                    title = stringResource(R.string.pref_alarm_snooze_title),
                    summary = stringResource(R.string.pref_alarm_snooze_value, alarm.snoozeMinutes),
                    icon = R.drawable.ic_sleep_timer_24dp,
                    onClick = { showAlarmSnoozeDialog = true },
                )
                SettingsRow(
                    title = stringResource(R.string.pref_alarm_station_title),
                    summary = stations.firstOrNull { it.first == alarm.stationUuid }?.second
                        ?: stringResource(R.string.pref_alarm_station_last_played),
                    icon = R.drawable.ic_music_note_24dp,
                    onClick = { showAlarmStationDialog = true },
                )
            }
        }

        item { CategoryHeader(stringResource(R.string.pref_recording_title)) }
        item {
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.pref_recording_format_title),
                    summary = recordingFormatLabel(recordingFormat),
                    icon = R.drawable.ic_music_note_24dp,
                    onClick = { showRecordingFormatDialog = true },
                )
                // An uncompressed format has no bit rate to choose.
                SettingsRow(
                    title = stringResource(R.string.pref_recording_bitrate_title),
                    summary = if (recordingFormat.hasBitrate) stringResource(R.string.pref_recording_bitrate_value, recordingBitrate)
                        else stringResource(R.string.pref_recording_bitrate_not_applicable),
                    icon = R.drawable.ic_network_check_24dp,
                    enabled = recordingFormat.hasBitrate,
                    onClick = { showRecordingBitrateDialog = true },
                )
            }
        }

        item { CategoryHeader(stringResource(R.string.pref_advanced_title)) }
        item {
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.pref_buffer_size_title),
                    summary = stringResource(
                        if (largeBuffer) R.string.pref_buffer_size_summary_enabled
                        else R.string.pref_buffer_size_summary_disabled
                    ),
                    icon = R.drawable.ic_network_check_24dp,
                    checked = largeBuffer,
                    onCheckedChange = callbacks.onLargeBufferChanged,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.pref_edit_station_title),
                    summary = stringResource(
                        if (editStations) R.string.pref_edit_station_summary_enabled
                        else R.string.pref_edit_station_summary_disabled
                    ),
                    icon = R.drawable.ic_edit_24dp,
                    checked = editStations,
                    onCheckedChange = callbacks.onEditStationsChanged,
                )
                // Editing stream addresses only makes sense while editing is on at all --
                // the old screen disabled and unchecked this entry in the same way.
                SettingsSwitchRow(
                    title = stringResource(R.string.pref_edit_station_stream_title),
                    summary = stringResource(
                        if (editStreamUris) R.string.pref_edit_station_stream_summary_enabled
                        else R.string.pref_edit_station_stream_summary_disabled
                    ),
                    icon = R.drawable.ic_music_note_24dp,
                    checked = editStreamUris,
                    enabled = editStations,
                    onCheckedChange = callbacks.onEditStreamUrisChanged,
                )
            }
        }

        item { CategoryHeader(stringResource(R.string.pref_links_title)) }
        item {
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.pref_github_title),
                    summary = stringResource(R.string.pref_github_summary),
                    icon = R.drawable.ic_github_24dp,
                    onClick = { callbacks.onOpenUrl(GITHUB_URL) },
                )
                SettingsRow(
                    title = stringResource(R.string.pref_codeberg_title),
                    summary = stringResource(R.string.pref_codeberg_summary),
                    icon = R.drawable.ic_codeberg_24dp,
                    onClick = { callbacks.onOpenUrl(CODEBERG_URL) },
                )
            }
        }
    }

    if (showAlarmTimeDialog) {
        AlarmTimeDialog(
            hour = alarm.hour,
            minute = alarm.minute,
            onConfirm = { hour, minute ->
                showAlarmTimeDialog = false
                // setting a time is taken as wanting the alarm
                callbacks.onAlarmChanged(alarm.copy(hour = hour, minute = minute, enabled = true))
            },
            onDismiss = { showAlarmTimeDialog = false },
        )
    }

    if (showAlarmDaysDialog) {
        AlarmDaysDialog(
            days = alarm.days,
            onConfirm = { days ->
                showAlarmDaysDialog = false
                callbacks.onAlarmChanged(alarm.copy(days = days))
            },
            onDismiss = { showAlarmDaysDialog = false },
        )
    }

    if (showAlarmSnoozeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.pref_alarm_snooze_title),
            icon = R.drawable.ic_alarm_24dp,
            options = AlarmSettings.SNOOZE_OPTIONS.map { minutes ->
                ThemeOption(
                    value = minutes.toString(),
                    label = stringResource(R.string.pref_alarm_snooze_value, minutes),
                    summary = "",
                    icon = R.drawable.ic_sleep_timer_24dp,
                )
            },
            selected = alarm.snoozeMinutes.toString(),
            onSelect = { value ->
                showAlarmSnoozeDialog = false
                callbacks.onAlarmChanged(alarm.copy(snoozeMinutes = value.toInt()))
            },
            onDismiss = { showAlarmSnoozeDialog = false },
        )
    }

    if (showAlarmStationDialog) {
        ChoiceDialog(
            title = stringResource(R.string.pref_alarm_station_title),
            icon = R.drawable.ic_alarm_24dp,
            options = listOf(
                ThemeOption(
                    value = "",
                    label = stringResource(R.string.pref_alarm_station_last_played),
                    summary = stringResource(R.string.pref_alarm_station_last_played_summary),
                    icon = R.drawable.ic_sleep_timer_24dp,
                )
            ) + stations.map { (uuid, name) ->
                ThemeOption(value = uuid, label = name, summary = "", icon = R.drawable.ic_music_note_24dp)
            },
            selected = alarm.stationUuid.takeIf { uuid -> stations.any { it.first == uuid } }.orEmpty(),
            onSelect = { uuid ->
                showAlarmStationDialog = false
                callbacks.onAlarmChanged(alarm.copy(stationUuid = uuid))
            },
            onDismiss = { showAlarmStationDialog = false },
        )
    }

    if (showRecordingFormatDialog) {
        ChoiceDialog(
            title = stringResource(R.string.pref_recording_format_title),
            icon = R.drawable.ic_record_24dp,
            options = RecordingFormat.available.map { format ->
                ThemeOption(
                    value = format.key,
                    label = recordingFormatLabel(format),
                    summary = stringResource(
                        when (format) {
                            RecordingFormat.AAC -> R.string.pref_recording_format_aac_summary
                            RecordingFormat.OPUS -> R.string.pref_recording_format_opus_summary
                            RecordingFormat.WAV -> R.string.pref_recording_format_wav_summary
                        }
                    ),
                    icon = R.drawable.ic_music_note_24dp,
                )
            },
            selected = recordingFormat.key,
            onSelect = { key ->
                showRecordingFormatDialog = false
                callbacks.onRecordingFormatChanged(RecordingFormat.fromKey(key))
            },
            onDismiss = { showRecordingFormatDialog = false },
        )
    }

    if (showRecordingBitrateDialog) {
        ChoiceDialog(
            title = stringResource(R.string.pref_recording_bitrate_title),
            icon = R.drawable.ic_network_check_24dp,
            options = recordingFormat.bitratesKbps.map { kbps ->
                ThemeOption(
                    value = kbps.toString(),
                    label = stringResource(R.string.pref_recording_bitrate_value, kbps),
                    // kbit/s * 3600 s / 8 bit / 1000 kB = MB per hour
                    summary = stringResource(R.string.pref_recording_bitrate_size, kbps * 3600 / 8 / 1000),
                    icon = R.drawable.ic_network_check_24dp,
                )
            },
            selected = recordingBitrate.toString(),
            onSelect = { value ->
                showRecordingBitrateDialog = false
                callbacks.onRecordingBitrateChanged(value.toInt())
            },
            onDismiss = { showRecordingBitrateDialog = false },
        )
    }

    if (showThemeDialog) {
        // The dialog stays open on purpose - see ThemeChooserDialog.
        ThemeChooserDialog(
            current = themeSelection,
            onSelect = callbacks.onThemeSelected,
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showExportFormatDialog) {
        ExportFormatDialog(
            onExportM3u = {
                showExportFormatDialog = false
                callbacks.onExportM3u()
            },
            onExportPls = {
                showExportFormatDialog = false
                callbacks.onExportPls()
            },
            onDismiss = { showExportFormatDialog = false },
        )
    }

    if (showRemoveAllDialog) {
        // Wiping the collection is not undoable and there is no per-station swipe to fall
        // back on here, so it goes through the same confirmation the single delete uses.
        ConfirmDialog(
            title = stringResource(R.string.pref_remove_all_stations_title),
            message = stringResource(R.string.dialog_yes_no_message_remove_all_stations),
            confirmLabel = stringResource(R.string.dialog_yes_no_positive_button_remove_all_stations),
            onConfirm = callbacks.onRemoveAllStations,
            onDismiss = { showRemoveAllDialog = false },
        )
    }
}

/* Asks which playlist format to write, standing in for the two separate export entries. */
@Composable
private fun ExportFormatDialog(
    onExportM3u: () -> Unit,
    onExportPls: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = WideDialogModifier,
        properties = WideDialogProperties,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_playlist_export_24dp),
                contentDescription = null,
            )
        },
        title = { Text(stringResource(R.string.pref_playlist_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormatRow(
                    title = stringResource(R.string.pref_m3u_export_title),
                    summary = stringResource(R.string.pref_m3u_export_summary),
                    icon = R.drawable.ic_save_m3u_24dp,
                    onClick = onExportM3u,
                )
                ExportFormatRow(
                    title = stringResource(R.string.pref_pls_export_title),
                    summary = stringResource(R.string.pref_pls_export_summary),
                    icon = R.drawable.ic_save_pls_24dp,
                    onClick = onExportPls,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_generic_button_cancel))
            }
        },
    )
}

@Composable
private fun ExportFormatRow(title: String, summary: String, icon: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* Groups related entries into one rounded card, replacing the flat divider-separated list. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    summary: String,
    icon: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    SettingsRowLayout(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    summary: String,
    icon: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SettingsRowLayout(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
    )
}

@Composable
private fun SettingsRowLayout(
    title: String,
    summary: String,
    icon: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = contentAlpha),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = contentAlpha),
                modifier = Modifier
                    .padding(10.dp)
                    .size(24.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
        trailing?.invoke()
    }
}

/* The alarm time the way the device shows times - "06:30" or "6:30 AM" */
private fun formatAlarmTime(context: Context, hour: Int, minute: Int): String {
    val calendar: Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

/* "Mon, 24 Nov, 06:30" - the weekday matters most for an alarm */
private fun formatAlarmTrigger(context: Context, millis: Long): String {
    val pattern: String = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM")
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis)) + ", " +
        DateFormat.getTimeFormat(context).format(Date(millis))
}

/* Short weekday names from Monday to Sunday, in the user's language */
private fun weekdayNames(style: Int): List<String> {
    val names: Array<String> = DateFormatSymbols.getInstance(Locale.getDefault()).let {
        if (style == Calendar.SHORT) it.shortWeekdays else it.weekdays
    }
    // DateFormatSymbols counts from Sunday = 1
    return listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY).map { names[it] }
}

/* "Mon – Fri", "Every day", "Once" or a list of days */
@Composable
private fun alarmDaysLabel(days: Int): String = when (days) {
    0 -> stringResource(R.string.pref_alarm_days_once)
    AlarmSettings.EVERY_DAY -> stringResource(R.string.pref_alarm_days_every_day)
    AlarmSettings.WEEKDAYS -> stringResource(R.string.pref_alarm_days_weekdays)
    else -> weekdayNames(Calendar.SHORT).filterIndexed { index, _ -> days and (1 shl index) != 0 }.joinToString(", ")
}

/* The alarm time, in the device's 12 or 24 hour style */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTimeDialog(hour: Int, minute: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = DateFormat.is24HourFormat(context))
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pref_alarm_time_title)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.dialog_generic_button_okay))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_generic_button_cancel)) }
        },
    ) {
        TimePicker(state = state)
    }
}

/* Which weekdays the alarm repeats on - none ticked means once */
@Composable
private fun AlarmDaysDialog(days: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableIntStateOf(days) }
    val names: List<String> = weekdayNames(Calendar.LONG)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = WideDialogModifier,
        properties = WideDialogProperties,
        icon = { Icon(painter = painterResource(R.drawable.ic_alarm_24dp), contentDescription = null) },
        title = { Text(stringResource(R.string.pref_alarm_days_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                names.forEachIndexed { index, name ->
                    val bit: Int = 1 shl index
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .toggleable(
                                value = selected and bit != 0,
                                role = Role.Checkbox,
                                onValueChange = { selected = selected xor bit },
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(checked = selected and bit != 0, onCheckedChange = null)
                        Text(text = name, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                Text(
                    text = stringResource(R.string.pref_alarm_days_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.dialog_generic_button_okay)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_generic_button_cancel)) }
        },
    )
}

/* "AAC (.m4a)" and so on. */
@Composable
private fun recordingFormatLabel(format: RecordingFormat): String = stringResource(
    when (format) {
        RecordingFormat.AAC -> R.string.pref_recording_format_aac
        RecordingFormat.OPUS -> R.string.pref_recording_format_opus
        RecordingFormat.WAV -> R.string.pref_recording_format_wav
    }
)

/* A list of options to pick one from; picking closes the dialog. */
@Composable
private fun ChoiceDialog(
    title: String,
    icon: Int,
    options: List<ThemeOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = WideDialogModifier,
        properties = WideDialogProperties,
        icon = { Icon(painter = painterResource(icon), contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // seven bit rates do not fit on every screen
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    ThemeOptionRow(
                        option = option,
                        selected = option.value == selected,
                        onClick = { onSelect(option.value) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_generic_button_cancel))
            }
        },
    )
}

private data class ThemeOption(
    val value: String,
    val label: String,
    val summary: String,
    val icon: Int,
)

/*
 * Picking a theme applies it straight away and leaves the dialog open, so the three options
 * can be compared against the live app before settling on one. The old dialog closed on the
 * first tap and only offered a "cancel" button that did not undo anything.
 */
@Composable
private fun ThemeChooserDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        ThemeOption(
            value = Keys.STATE_THEME_FOLLOW_SYSTEM,
            label = stringResource(R.string.pref_theme_selection_mode_device_default),
            summary = stringResource(R.string.pref_theme_selection_mode_device_default_summary),
            icon = R.drawable.ic_smartphone_24dp,
        ),
        ThemeOption(
            value = Keys.STATE_THEME_LIGHT_MODE,
            label = stringResource(R.string.pref_theme_selection_mode_light),
            summary = stringResource(R.string.pref_theme_selection_mode_light_summary),
            icon = R.drawable.ic_light_mode_24dp,
        ),
        ThemeOption(
            value = Keys.STATE_THEME_DARK_MODE,
            label = stringResource(R.string.pref_theme_selection_mode_dark),
            summary = stringResource(R.string.pref_theme_selection_mode_dark_summary),
            icon = R.drawable.ic_dark_mode_24dp,
        ),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = WideDialogModifier,
        properties = WideDialogProperties,
        icon = {
            Icon(painter = painterResource(R.drawable.ic_brush_24dp), contentDescription = null)
        },
        title = { Text(stringResource(R.string.pref_theme_selection_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    ThemeOptionRow(
                        option = option,
                        selected = option.value == current,
                        onClick = { onSelect(option.value) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_generic_button_done))
            }
        },
    )
}

@Composable
private fun ThemeOptionRow(option: ThemeOption, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else Color.Transparent,
        label = "themeOptionBackground",
    )
    val iconBackground = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val iconTint = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBackground),
        ) {
            Icon(
                painter = painterResource(option.icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = option.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = null)
    }
}

private const val GITHUB_URL = "https://github.com/jamal2362/URL-Radio"
private const val CODEBERG_URL = "https://codeberg.org/y20k/transistor"
private const val LICENSE_URL = "https://github.com/jamal2362/URL-Radio/blob/master/LICENSE.md"
