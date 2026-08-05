/*
 * URLRadio.kt
 * Implements the URLRadio class
 * URLRadio is the base Application class that sets up day and night theme
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio

import android.app.Application
import android.util.Log
import com.jamal2367.urlradio.helpers.PreferencesHelper.initPreferences


/**
 * URLRadio.class
 */
class URLRadio : Application() {

    /* Define log tag */
    private val tag: String = URLRadio::class.java.simpleName

    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        Log.v(tag, "URLRadio application started.")
        initPreferences()
        // The light/dark decision is no longer pushed into AppCompatDelegate here. The
        // Compose theme reads the preference itself, so it also follows a change made in
        // settings without recreating the activity.
    }


    /* Implements onTerminate */
    override fun onTerminate() {
        super.onTerminate()
        Log.v(tag, "URLRadio application terminated.")
    }

}
