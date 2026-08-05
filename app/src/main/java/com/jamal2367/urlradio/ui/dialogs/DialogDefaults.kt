/*
 * DialogDefaults.kt
 * Shared width for every dialog in the app
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.ui.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * The platform decides how wide a dialog is by default, and its answer leaves a good deal of
 * the screen unused -- noticeably so for the station pickers and the settings dialogs, which
 * have rows of their own to fit. Handing the width back to the app is what
 * [DialogProperties.usePlatformDefaultWidth] `= false` does; [WideDialogModifier] then spends
 * everything but a margin on either side.
 *
 * Pass both together: the properties alone would leave the dialog sized to its content.
 */
val WideDialogProperties = DialogProperties(usePlatformDefaultWidth = false)

/**
 * Material still caps a dialog at 560dp inside [androidx.compose.material3.BasicAlertDialog],
 * so this widens dialogs on a phone without stretching them across a tablet.
 */
val WideDialogModifier: Modifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 16.dp)
