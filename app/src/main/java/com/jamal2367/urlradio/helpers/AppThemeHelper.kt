/*
 * AppThemeHelper.kt
 * Turns the stored theme preference into something the UI can use
 *
 * AppCompatDelegate.setDefaultNightMode() is gone: the app no longer depends on AppCompat,
 * and the Compose theme resolves light/dark straight from the preference (see MainActivity).
 * getColor() is gone too - it read attributes out of an uninitialised TypedValue and so
 * always returned 0, which is why the settings screen used to paint its navigation bar black.
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.helpers

import android.content.Context
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.R


/*
 * AppThemeHelper object
 */
object AppThemeHelper {

    /* Returns a readable String for the currently selected app theme */
    fun getCurrentTheme(context: Context): String {
        return when (PreferencesHelper.loadThemeSelection()) {
            Keys.STATE_THEME_LIGHT_MODE -> context.getString(R.string.pref_theme_selection_mode_light)
            Keys.STATE_THEME_DARK_MODE -> context.getString(R.string.pref_theme_selection_mode_dark)
            else -> context.getString(R.string.pref_theme_selection_mode_device_default)
        }
    }

}
