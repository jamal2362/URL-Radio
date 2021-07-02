/*
 * URLRadio.kt
 * Implements the URLRadio class
 * URLRadio is the base Application class that sets up day and night theme
 *
 * This file is part of
 * URL Radio - Radio App for Android
 *
 * Copyright (c) 2015-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio

import android.app.Application
import com.jamal2367.urlradio.helpers.AppThemeHelper
import com.jamal2367.urlradio.helpers.LogHelper
import com.jamal2367.urlradio.helpers.PreferencesHelper
import com.jamal2367.urlradio.helpers.PreferencesHelper.initPreferences


/**
 * URLRadio.class
 */
class URLRadio: Application () {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(URLRadio::class.java)


    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        LogHelper.v(TAG, "URLRadio application started.")
        initPreferences()
        // set Dark / Light theme state
        AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())
    }


    /* Implements onTerminate */
    override fun onTerminate() {
        super.onTerminate()
        LogHelper.v(TAG, "URLRadio application terminated.")
    }

}
