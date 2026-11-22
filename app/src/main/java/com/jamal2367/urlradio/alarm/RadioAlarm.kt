/*
 * RadioAlarm.kt
 * The radio alarm: its settings, its scheduling, and its rescheduling after a reboot
 *
 * The alarm is set with AlarmManager.setAlarmClock: it fires on time even in Doze, shows
 * the alarm symbol in the status bar, and may start the player service from the background.
 * When it fires, PlayerService plays the station (see PlayerService.startAlarmPlayback).
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.MainActivity
import com.jamal2367.urlradio.PlayerService
import com.jamal2367.urlradio.helpers.PreferencesHelper
import java.util.Calendar


/* The alarm as the user set it. days: bit 0 = Monday ... bit 6 = Sunday, 0 = only once. */
data class AlarmSettings(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    val days: Int = WEEKDAYS,
    /** Empty: the station played last. */
    val stationUuid: String = String(),
) {
    companion object {
        const val WEEKDAYS: Int = 0b0011111
        const val EVERY_DAY: Int = 0b1111111
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
object RadioAlarm {

    private val tag: String = RadioAlarm::class.java.simpleName

    private const val REQUEST_CODE_FIRE = 4711
    private const val REQUEST_CODE_SHOW = 4712


    /* When the alarm next goes off, in milliseconds - null when it is off. */
    fun nextTrigger(settings: AlarmSettings, now: Calendar = Calendar.getInstance()): Long? {
        if (!settings.enabled) return null
        val candidate: Calendar = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, settings.hour)
            set(Calendar.MINUTE, settings.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (offset in 0..7) {
            val day: Calendar = (candidate.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            if (!day.after(now)) continue
            if (settings.days == 0 || settings.days and (1 shl mondayIndex(day)) != 0) {
                return day.timeInMillis
            }
        }
        return null
    }


    /* 0 for Monday ... 6 for Sunday */
    fun mondayIndex(calendar: Calendar): Int = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7


    /*
     * Whether the app may set exact alarms. Android 13 and newer grant USE_EXACT_ALARM with
     * the installation; on Android 12 the user can take SCHEDULE_EXACT_ALARM away again.
     */
    fun canSchedule(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }


    /* Sets the alarm from the saved settings - or cancels it when it is off. */
    fun schedule(context: Context) {
        val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
        val fire: PendingIntent = firePendingIntent(context)
        alarmManager.cancel(fire)
        val settings: AlarmSettings = PreferencesHelper.loadAlarmSettings()
        val trigger: Long = nextTrigger(settings) ?: return
        if (!canSchedule(context)) {
            Log.w(tag, "Not allowed to schedule exact alarms - radio alarm not set.")
            return
        }
        // tapping the alarm symbol in the status bar opens the app
        val show: PendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_SHOW,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), fire)
        Log.i(tag, "Radio alarm set for $trigger")
    }


    /* Called by PlayerService when the alarm has gone off: the next one, or off if it was a one-time alarm. */
    fun onFired(context: Context) {
        val settings: AlarmSettings = PreferencesHelper.loadAlarmSettings()
        if (settings.days == 0) {
            PreferencesHelper.saveAlarmSettings(settings.copy(enabled = false))
        }
        schedule(context)
    }


    /* Starts PlayerService with the alarm action - as a foreground service, which an alarm clock may do. */
    private fun firePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PlayerService::class.java).setAction(Keys.ACTION_RADIO_ALARM)
        val flags: Int = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, REQUEST_CODE_FIRE, intent, flags)
        } else {
            PendingIntent.getService(context, REQUEST_CODE_FIRE, intent, flags)
        }
    }
}


/*
 * Alarms do not survive a reboot, and a changed clock or time zone moves "07:00".
 * This sets the radio alarm again in those cases.
 */
class RadioAlarmRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> RadioAlarm.schedule(context)
        }
    }
}
