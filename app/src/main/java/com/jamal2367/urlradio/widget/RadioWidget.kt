/*
 * RadioWidget.kt
 * The home screen widget: station logo, name, current title and a play / stop button
 *
 * PlayerService pushes every change (station, playing or not, title) through update().
 * When the launcher asks on its own - the widget was just placed, or the device restarted -
 * the widget reads the same from the saved player state.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.MainActivity
import com.jamal2367.urlradio.PlayerService
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.CollectionHelper
import com.jamal2367.urlradio.helpers.FileHelper
import com.jamal2367.urlradio.helpers.PreferencesHelper


@androidx.annotation.OptIn(UnstableApi::class)
class RadioWidget : AppWidgetProvider() {

    /* The launcher asks for content: the widget was placed, resized, or the device restarted */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val collection = FileHelper.readCollection(context)
        val station: Station? = collection.stations.takeIf { it.isNotEmpty() }
            ?.let { CollectionHelper.getStation(collection, PreferencesHelper.loadLastPlayedStationUuid()) }
        val title: String = if (PreferencesHelper.loadIsPlaying()) PreferencesHelper.loadMetadataHistory().lastOrNull().orEmpty() else ""
        appWidgetManager.updateAppWidget(
            appWidgetIds,
            buildViews(context, station, PreferencesHelper.loadIsPlaying(), title)
        )
    }


    companion object {

        private val tag: String = RadioWidget::class.java.simpleName

        private const val REQUEST_CODE_OPEN = 5001
        private const val REQUEST_CODE_TOGGLE = 5002
        private const val LOGO_SIZE_PX = 160


        /* Shows the given state in every placed widget - called by PlayerService */
        fun update(context: Context, station: Station?, isPlaying: Boolean, title: String) {
            val manager: AppWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val ids: IntArray = manager.getAppWidgetIds(ComponentName(context, RadioWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, buildViews(context, station, isPlaying, title))
        }


        private fun buildViews(context: Context, station: Station?, isPlaying: Boolean, title: String): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_player)
            val openApp: PendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)

            if (station == null) {
                // nothing to play yet - the button leads into the app instead
                views.setTextViewText(R.id.widget_station, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_no_station))
                views.setImageViewResource(R.id.widget_logo, R.drawable.ic_default_station_image_72dp)
                views.setOnClickPendingIntent(R.id.widget_play, openApp)
                return views
            }

            views.setTextViewText(R.id.widget_station, CollectionHelper.nameWithoutTags(station.name))
            views.setTextViewText(
                R.id.widget_title,
                title.trim().takeIf { isPlaying && it.isNotEmpty() && it != "null" && it != station.name }
                    ?: context.getString(if (isPlaying) R.string.widget_playing else R.string.widget_nothing_playing)
            )
            val logo: Bitmap? = loadLogo(context, station)
            if (logo != null) views.setImageViewBitmap(R.id.widget_logo, logo)
            else views.setImageViewResource(R.id.widget_logo, R.drawable.ic_default_station_image_72dp)

            views.setImageViewResource(
                R.id.widget_play,
                if (isPlaying) R.drawable.ic_widget_stop_24dp else R.drawable.ic_widget_play_24dp
            )
            views.setContentDescription(
                R.id.widget_play,
                context.getString(R.string.descr_player_playback_button)
            )
            views.setOnClickPendingIntent(R.id.widget_play, togglePendingIntent(context, isPlaying))
            return views
        }


        /*
         * Play / stop. To start playback the service has to come up as a foreground service,
         * which a tap on a widget is allowed to do. While something plays the service is in
         * the foreground already, and a plain start command is all it takes to stop it.
         */
        private fun togglePendingIntent(context: Context, isPlaying: Boolean): PendingIntent {
            val intent = Intent(context, PlayerService::class.java).setAction(Keys.ACTION_TOGGLE_PLAYBACK)
            val flags: Int = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (!isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, REQUEST_CODE_TOGGLE, intent, flags)
            } else {
                PendingIntent.getService(context, REQUEST_CODE_TOGGLE, intent, flags)
            }
        }


        /* The station's own small image, scaled down - a widget update must stay small */
        private fun loadLogo(context: Context, station: Station): Bitmap? {
            val image: String = station.smallImage.ifEmpty { station.image }
            if (image.isEmpty()) return null
            return try {
                val uri = image.toUri()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= LOGO_SIZE_PX && bounds.outHeight / (sample * 2) >= LOGO_SIZE_PX) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?.scale(LOGO_SIZE_PX, LOGO_SIZE_PX)
            } catch (e: Exception) {
                Log.w(tag, "Unable to load the widget logo: $e")
                null
            }
        }
    }
}
