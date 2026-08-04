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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.R

data class SettingsCallbacks(
    val onThemeSelected: (String) -> Unit,
    val onUpdateStationImages: () -> Unit,
    val onExportM3u: () -> Unit,
    val onExportPls: () -> Unit,
    val onBackup: () -> Unit,
    val onRestore: () -> Unit,
    val onLargeBufferChanged: (Boolean) -> Unit,
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
    editStations: Boolean,
    editStreamUris: Boolean,
    callbacks: SettingsCallbacks,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var showThemeDialog by remember { mutableStateOf(false) }

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
            }
        }

        item { CategoryHeader(stringResource(R.string.pref_backup_import_export_title)) }
        item {
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.pref_m3u_export_title),
                    summary = stringResource(R.string.pref_m3u_export_summary),
                    icon = R.drawable.ic_save_m3u_24dp,
                    onClick = callbacks.onExportM3u,
                )
                SettingsRow(
                    title = stringResource(R.string.pref_pls_export_title),
                    summary = stringResource(R.string.pref_pls_export_summary),
                    icon = R.drawable.ic_save_pls_24dp,
                    onClick = callbacks.onExportPls,
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

    if (showThemeDialog) {
        ThemeChooserDialog(
            current = themeSelection,
            onSelect = {
                callbacks.onThemeSelected(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
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
) {
    SettingsRowLayout(
        title = title,
        summary = summary,
        icon = icon,
        modifier = Modifier.clickable(onClick = onClick),
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

@Composable
private fun ThemeChooserDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        Keys.STATE_THEME_FOLLOW_SYSTEM to stringResource(R.string.pref_theme_selection_mode_device_default),
        Keys.STATE_THEME_LIGHT_MODE to stringResource(R.string.pref_theme_selection_mode_light),
        Keys.STATE_THEME_DARK_MODE to stringResource(R.string.pref_theme_selection_mode_dark),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pref_theme_selection_title)) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            RadioButton(selected = value == current, onClick = { onSelect(value) })
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) },
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

private const val GITHUB_URL = "https://github.com/jamal2362/URL-Radio"
private const val CODEBERG_URL = "https://codeberg.org/y20k/transistor"
private const val LICENSE_URL = "https://github.com/jamal2362/URL-Radio/blob/master/LICENSE.md"
