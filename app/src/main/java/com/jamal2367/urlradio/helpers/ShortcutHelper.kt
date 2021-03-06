/*
 * ShortcutHelper.kt
 * Implements the ShortcutHelper object
 * A ShortcutHelper creates and handles station shortcuts on the Home screen
 *
 * This file is part of
 * URL Radio - Radio App for Android
 *
 * Copyright (c) 2015-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.helpers

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.PlayerServiceStarterActivity
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station


/*
 * ShortcutHelper object
 */
object ShortcutHelper {


    /* Places shortcut on Home screen */
    fun placeShortcut(context: Context, station: Station) {
        // credit: https://medium.com/@BladeCoder/using-support-library-26-0-0-you-can-do-bb75911e01e8
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val shortcut: ShortcutInfoCompat = ShortcutInfoCompat.Builder(context, station.name)
                    .setShortLabel(station.name)
                    .setLongLabel(station.name)
                    .setIcon(createShortcutIcon(context, station.image, station.imageColor))
                    .setIntent(createShortcutIntent(context, station.uuid))
                    .build()
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        } else {
            Toast.makeText(context, R.string.toastmessage_shortcut_not_created, Toast.LENGTH_LONG).show()
        }
    }


    /* Creates Intent for a station shortcut */
    private fun createShortcutIntent(context: Context, stationUuid: String): Intent {
        val shortcutIntent = Intent(context, PlayerServiceStarterActivity::class.java)
        shortcutIntent.action = Keys.ACTION_START_PLAYER_SERVICE
        shortcutIntent.putExtra(Keys.EXTRA_STATION_UUID, stationUuid)
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return shortcutIntent
    }


    /* Create shortcut icon */
    private fun createShortcutIcon(context: Context, stationImage: String, stationImageColor: Int): IconCompat {
        val stationImageBitmap: Bitmap = ImageHelper.getScaledStationImage(context, stationImage,192)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            IconCompat.createWithAdaptiveBitmap(ImageHelper.createSquareImage(context, stationImageBitmap, stationImageColor, 192, true))
        } else {
            IconCompat.createWithAdaptiveBitmap(ImageHelper.createSquareImage(context, stationImageBitmap, stationImageColor, 192, false))
        }
    }

}
