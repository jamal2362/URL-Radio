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
import androidx.compose.foundation.layout.widthIn
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

/** Nothing is gained by letting a dialog grow past this; Material uses the same figure. */
private val DialogMaxWidth = 560.dp

/**
 * A margin on either side, then everything that is left up to [DialogMaxWidth]. In portrait
 * the cap never binds and the dialog spans the screen; in landscape it stops at 560dp instead
 * of stretching across the whole window.
 *
 * The order is what makes the cap work, and it is easy to get wrong. `fillMaxWidth` turns the
 * width constraint into a fixed one, and a `widthIn` after it is coerced into that fixed
 * constraint and does nothing -- which is also why the 560dp `sizeIn` that
 * [androidx.compose.material3.BasicAlertDialog] applies internally has no effect here. The cap
 * has to be in place before the width is filled.
 */
val WideDialogModifier: Modifier = Modifier
    .padding(horizontal = 16.dp)
    .widthIn(max = DialogMaxWidth)
    .fillMaxWidth()
