/*
 * AlarmNotification.kt
 * The notification that belongs to a running wake-up: "Off" and "Snooze" without opening the app
 *
 * While the alarm plays it is a high-priority alarm notification that shows on the lock
 * screen. During a snooze it turns into a quiet one that says until when, with "Off" to
 * cancel the second wake-up.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.MainActivity
import com.jamal2367.urlradio.PlayerService
import com.jamal2367.urlradio.R
import java.util.Date


@androidx.annotation.OptIn(UnstableApi::class)
object AlarmNotification {

    private const val CHANNEL_ALARM = "radio_alarm"
    private const val CHANNEL_SNOOZE = "radio_alarm_snooze"
    private const val NOTIFICATION_ID = 4720

    private const val REQUEST_CODE_STOP = 4721
    private const val REQUEST_CODE_SNOOZE = 4722
    private const val REQUEST_CODE_OPEN = 4723
    private const val REQUEST_CODE_SNOOZE_CANCEL = 4724


    /* The alarm is playing: "Off" and "Snooze" */
    fun showRinging(context: Context, stationName: String) {
        val time: String = DateFormat.getTimeFormat(context).format(Date())
        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification_app_icon_white_24dp)
            .setContentTitle(context.getString(R.string.alarm_notification_title, time))
            .setContentText(stationName)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true) // the radio is the sound
            .setContentIntent(openPendingIntent(context))
            // swiping it away (possible on newer Android versions) counts as "Off"
            .setDeleteIntent(servicePendingIntent(context, Keys.ACTION_ALARM_STOP, REQUEST_CODE_STOP))
            .addAction(
                R.drawable.ic_alarm_24dp,
                context.getString(R.string.alarm_action_snooze),
                servicePendingIntent(context, Keys.ACTION_ALARM_SNOOZE, REQUEST_CODE_SNOOZE)
            )
            .addAction(
                R.drawable.ic_clear_24dp,
                context.getString(R.string.alarm_action_stop),
                servicePendingIntent(context, Keys.ACTION_ALARM_STOP, REQUEST_CODE_STOP)
            )
            .build()
        post(context, CHANNEL_ALARM, notification)
    }


    /* The alarm is snoozing: until when, and "Off" to skip the second wake-up */
    fun showSnoozed(context: Context, until: Long) {
        val time: String = DateFormat.getTimeFormat(context).format(Date(until))
        val cancel: PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SNOOZE_CANCEL,
            Intent(context, RadioAlarmRescheduleReceiver::class.java).setAction(Keys.ACTION_ALARM_SNOOZE_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SNOOZE)
            .setSmallIcon(R.drawable.ic_notification_app_icon_white_24dp)
            .setContentTitle(context.getString(R.string.alarm_notification_snoozed, time))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPendingIntent(context))
            .addAction(R.drawable.ic_clear_24dp, context.getString(R.string.alarm_action_stop), cancel)
            .build()
        post(context, CHANNEL_SNOOZE, notification)
    }


    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }


    @SuppressLint("MissingPermission") // checked right before
    private fun post(context: Context, channel: String, notification: android.app.Notification) {
        createChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // not allowed: the media notification's controls still stop the alarm
            return
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }


    private fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, context.getString(R.string.alarm_channel_ringing), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SNOOZE, context.getString(R.string.alarm_channel_snoozed), NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }


    /* The service is in the foreground while the alarm plays, so a plain start command reaches it */
    private fun servicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, PlayerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )


    private fun openPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE_OPEN,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
