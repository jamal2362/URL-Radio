/*
 * RadioTileService.kt
 * The quick settings tile: radio on / off, with the station and the current title
 *
 * The tile shows the saved player state. PlayerService asks Android to refresh it on every
 * change (requestUpdate), so it never needs a connection of its own just to stay current.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.MainActivity
import com.jamal2367.urlradio.PlayerService
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.helpers.CollectionHelper
import com.jamal2367.urlradio.helpers.FileHelper
import com.jamal2367.urlradio.helpers.PreferencesHelper


/*
 * What the tile shows, as PlayerService last reported it - kept in memory, so a refresh needs
 * no file read. Empty (null) only when the app process was just started; the tile then falls
 * back to the saved player state.
 */
object RadioTileState {
    @Volatile var active: Boolean? = null
        private set
    @Volatile var stationName: String? = null
        private set
    @Volatile var title: String = String()
        private set

    fun update(active: Boolean, stationName: String?, title: String) {
        this.active = active
        this.stationName = stationName
        this.title = title
    }
}


/* Asks Android to refresh the tile - it calls onStartListening when the tile is shown */
fun requestTileUpdate(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
    runCatching {
        TileService.requestListeningState(context, ComponentName(context, RadioTileService::class.java))
    }
}


@RequiresApi(Build.VERSION_CODES.N)
@androidx.annotation.OptIn(UnstableApi::class)
class RadioTileService : TileService() {

    private val tag: String = RadioTileService::class.java.simpleName


    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }


    override fun onClick() {
        super.onClick()
        if (RadioTileState.stationName == null && FileHelper.readCollection(this).stations.isEmpty()) {
            // nothing to play yet - the app is where stations are added
            openApp()
            return
        }
        val active: Boolean = RadioTileState.active ?: PreferencesHelper.loadIsPlaying()
        // switch right away - the service confirms (or corrects) a moment later
        showState(!active)
        val intent = Intent(this, PlayerService::class.java).setAction(Keys.ACTION_TOGGLE_PLAYBACK)
        try {
            if (active) {
                // the service runs in the foreground while playing: a plain start command stops it
                startService(intent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: IllegalStateException) {
            // Android refused to start playback from here - the app can always do it
            Log.w(tag, "Unable to start playback from the tile: $e")
            showState(active)
            openApp()
        }
    }


    private fun showState(active: Boolean) {
        val tile: Tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }


    private fun updateTile() {
        val tile: Tile = qsTile ?: return
        val isPlaying: Boolean = RadioTileState.active ?: PreferencesHelper.loadIsPlaying()
        val stationName: String = RadioTileState.stationName ?: run {
            val collection = FileHelper.readCollection(this)
            collection.stations.takeIf { it.isNotEmpty() }
                ?.let { CollectionHelper.nameWithoutTags(CollectionHelper.getStation(collection, PreferencesHelper.loadLastPlayedStationUuid()).name) }
                .orEmpty()
        }
        tile.state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification_app_icon_white_24dp)
        tile.label = stationName.ifEmpty { getString(R.string.app_name) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val title: String = (if (RadioTileState.active != null) RadioTileState.title
                else PreferencesHelper.loadMetadataHistory().lastOrNull().orEmpty()).trim()
            tile.subtitle = when {
                !isPlaying -> getString(R.string.widget_nothing_playing)
                title.isNotEmpty() && title != "null" && title != stationName -> title
                else -> getString(R.string.widget_playing)
            }
        }
        tile.updateTile()
    }


    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

}
