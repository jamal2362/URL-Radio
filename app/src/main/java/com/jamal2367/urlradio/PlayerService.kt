/*
 * PlayerService.kt
 * Implements the PlayerService class
 * PlayerService is URLRadio's foreground service that plays radio station audio
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.CountDownTimer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.*
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.*
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import com.jamal2367.urlradio.core.Collection
import com.jamal2367.urlradio.helpers.*
import java.util.*


/*
 * PlayerService class
 */
class PlayerService: MediaSessionService() {


    /* Main class variables */
    private lateinit var player: Player
    private lateinit var mediaSession: MediaSession
    private lateinit var sleepTimer: CountDownTimer
    var sleepTimerTimeRemaining: Long = 0L
    private var collection: Collection = Collection()
    private lateinit var metadataHistory: MutableList<String>
    private var playbackActive = false // todo remove


    /* Overrides onCreate from Service */
    override fun onCreate() {
        super.onCreate()
        // load collection
        collection = FileHelper.readCollection(this)
        // create and register collection changed receiver
        LocalBroadcastManager.getInstance(application).registerReceiver(collectionChangedReceiver, IntentFilter(Keys.ACTION_COLLECTION_CHANGED))
        // initialize player and session
        initializePlayer()
        initializeSession()
        setMediaNotificationProvider(CustomNotificationProvider())
        // fetch the metadata history
        metadataHistory = PreferencesHelper.loadMetadataHistory()
    }


    /* Overrides onDestroy from Service */
    override fun onDestroy() {
        // player.removeAnalyticsListener(analyticsListener)
        player.removeListener(playerListener)
        player.release()
        mediaSession.release()
        super.onDestroy()
    }


    /* Overrides onGetSession from MediaSessionService */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }


    /* Initializes the ExoPlayer */
    private fun initializePlayer() {
        val exoPlayer: ExoPlayer = ExoPlayer.Builder(this).apply {
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            setHandleAudioBecomingNoisy(true)
//            setLoadControl(CustomLoadControl())
        }.build()
        // exoPlayer.addAnalyticsListener(analyticsListener)
        exoPlayer.addListener(playerListener)

        // manually add seek to next and seek to previous since headphones issue them and they are translated to skip 30 sec forward / 10 sec back
        player = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon().add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_PREVIOUS).build()
            }
        }
    }


    /* todo remove */
    inner class CustomLoadControl: DefaultLoadControl() {
        override fun shouldContinueLoading(playbackPositionUs: Long, bufferedDurationUs: Long, playbackSpeed: Float): Boolean {
            LogHelper.e("playbackActive = $playbackActive // playbackPositionUs = $playbackPositionUs") // todo remove
//            if (playbackActive) {
//                return super.shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed)
//            } else {
//                return false
//            }
            return super.shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed)
        }
    }


    /* todo remove */
    private fun createLoadControl(): LoadControl {
        return DefaultLoadControl.Builder()
//            .setBackBuffer(0, false)
            .setBufferDurationsMs(0, 0, 0, 0)
            .build()
    }


    /* Initializes the MediaSession */
    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntent(intent)
            getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        mediaSession = MediaSession.Builder(this, player).apply {
            setSessionActivity(pendingIntent)
            setCallback(CustomSessionCallback())
        }.build()
    }


    /* Starts sleep timer / adds default duration to running sleeptimer */
    private fun startSleepTimer() {
        // stop running timer
        if (sleepTimerTimeRemaining > 0L && this::sleepTimer.isInitialized) {
            sleepTimer.cancel()
        }
        // initialize timer
        sleepTimer = object: CountDownTimer(Keys.SLEEP_TIMER_DURATION + sleepTimerTimeRemaining, Keys.SLEEP_TIMER_INTERVAL) {
            override fun onFinish() {
                LogHelper.v("Sleep timer finished. Sweet dreams.")
                sleepTimerTimeRemaining = 0L
                player.pause()
            }
            override fun onTick(millisUntilFinished: Long) {
                sleepTimerTimeRemaining = millisUntilFinished
            }
        }
        // start timer
        sleepTimer.start()
        // store timer state
        PreferencesHelper.saveSleepTimerRunning(isRunning = true)
    }


    /* Cancels sleep timer */
    private fun cancelSleepTimer() {
        if (this::sleepTimer.isInitialized && sleepTimerTimeRemaining > 0L) {
            sleepTimerTimeRemaining = 0L
            sleepTimer.cancel()
        }
        // store timer state
        PreferencesHelper.saveSleepTimerRunning(isRunning = false)
    }

    /* Updates metadata */
    private fun updateMetadata(metadata: String = String()) {
        // get metadata string
        val metadataString: String = metadata.ifEmpty {
            player.currentMediaItem?.mediaMetadata?.title.toString()
        }
        // remove duplicates
        if (metadataHistory.contains(metadataString)) {
            metadataHistory.removeIf { it == metadataString }
        }
        // append metadata to metadata history
        metadataHistory.add(metadataString)
        // trim metadata list
        if (metadataHistory.size > Keys.DEFAULT_SIZE_OF_METADATA_HISTORY) {
            metadataHistory.removeAt(0)
        }
        // update notification
        // TODO implement
        // this will hide the NotificationUtil.setNotification(applicationContext, Keys.NOW_PLAYING_NOTIFICATION_ID, null)
        // save history
        PreferencesHelper.saveMetadataHistory(metadataHistory)
    }



    /* Gets the most current metadata string */
    private fun getCurrentMetadata(): String {
        val metadataString: String = if (metadataHistory.isEmpty()) {
            player.currentMediaItem?.mediaMetadata?.title.toString()
        } else {
            metadataHistory.last()
        }
        return metadataString
    }


    /* Reads collection of stations from storage using GSON */
    private fun loadCollection(context: Context) {
        LogHelper.v("Loading collection of stations from storage")
        CoroutineScope(Main).launch {
            // load collection on background thread
            val deferred: Deferred<Collection> = async(Dispatchers.Default) { FileHelper.readCollectionSuspended(context) }
            // wait for result and update collection
            collection = deferred.await()
//            // special case: trigger metadata view update for stations that have no metadata
//            if (player.isPlaying && station.name == getCurrentMetadata()) {
//                station = CollectionHelper.getStation(collection, station.uuid)
//                updateMetadata(null)
//            }
        }
    }


    /*
     * Custom MediaSession Callback that handles player commands
     */
    private inner class CustomSessionCallback: MediaSession.Callback {

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<List<MediaItem>> {
            val updatedMediaItems = mediaItems.map { mediaItem ->
                mediaItem.buildUpon().apply {
                    setUri(mediaItem.requestMetadata.mediaUri)
                }.build()
            }
            return Futures.immediateFuture(updatedMediaItems)
        }

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            // add custom commands
            val connectionResult: MediaSession.ConnectionResult  = super.onConnect(session, controller)
            val builder: SessionCommands.Builder = connectionResult.availableSessionCommands.buildUpon()
            builder.add(SessionCommand(Keys.CMD_START_SLEEP_TIMER, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_REQUEST_METADATA_HISTORY, Bundle.EMPTY))
            return MediaSession.ConnectionResult.accept(builder.build(), connectionResult.availablePlayerCommands)
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                Keys.CMD_START_SLEEP_TIMER -> {
                    startSleepTimer()
                }
                Keys.CMD_CANCEL_SLEEP_TIMER -> {
                    cancelSleepTimer()
                }
                Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING -> {
                    val resultBundle = Bundle()
                    resultBundle.putLong(Keys.EXTRA_SLEEP_TIMER_REMAINING, sleepTimerTimeRemaining)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
                }
                Keys.CMD_REQUEST_METADATA_HISTORY -> {
                    val resultBundle = Bundle()
                    resultBundle.putStringArrayList(Keys.EXTRA_METADATA_HISTORY, ArrayList(metadataHistory))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
            // playerCommand = one of COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_SEEK_TO_DEFAULT_POSITION, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD, COMMAND_SET_SPEED_AND_PITCH, COMMAND_SET_SHUFFLE_MODE, COMMAND_SET_REPEAT_MODE, COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_MEDIA_ITEMS_METADATA, COMMAND_SET_MEDIA_ITEMS_METADATA, COMMAND_CHANGE_MEDIA_ITEMS, COMMAND_GET_AUDIO_ATTRIBUTES, COMMAND_GET_VOLUME, COMMAND_GET_DEVICE_VOLUME, COMMAND_SET_VOLUME, COMMAND_SET_DEVICE_VOLUME, COMMAND_ADJUST_DEVICE_VOLUME, COMMAND_SET_VIDEO_SURFACE, COMMAND_GET_TEXT, COMMAND_SET_TRACK_SELECTION_PARAMETERS or COMMAND_GET_TRACK_INFOS. */
            // emulate headphone buttons
            // start/pause: adb shell input keyevent 85
            // next: adb shell input keyevent 87
            // prev: adb shell input keyevent 88
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT ->  {
                    // todo implememt
                    LogHelper.e("COMMAND_SEEK_TO_NEXT") // todo remove
                    return SessionResult.RESULT_INFO_SKIPPED
                }
                Player.COMMAND_SEEK_TO_PREVIOUS ->  {
                    // todo implememt
                    LogHelper.e("COMMAND_SEEK_TO_PREVIOUS") // todo remove
                    return SessionResult.RESULT_INFO_SKIPPED
                }
                Player.COMMAND_PLAY_PAUSE -> {
                    // override pause with stop, to prevent unnecessary buffering
                    LogHelper.e("COMMAND_PLAY_PAUSE") // todo remove
                    return if (player.isPlaying) {
                        player.playWhenReady = false
//                        player.stop()
                        SessionResult.RESULT_INFO_SKIPPED
                    } else {
                        super.onPlayerCommandRequest(session, controller, playerCommand)
                    }
                }
                else -> {
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            }
        }
    }
    /*
     * End of inner class
     */


    /*
     * Custom NotificationProvider that sets tailored Notification
     */
    private inner class CustomNotificationProvider: MediaNotification.Provider {

        override fun createNotification(session: MediaSession, customLayout: ImmutableList<CommandButton>, actionFactory: MediaNotification.ActionFactory,  onNotificationChangedCallback: MediaNotification.Provider.Callback): MediaNotification {
            return MediaNotification(Keys.NOW_PLAYING_NOTIFICATION_ID, NotificationHelper(this@PlayerService).getNotification(session, actionFactory))
        }

        override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean {
            TODO("Not yet implemented")
        }
    }
    /*
     * End of inner class
     */

    /*
     * Player.Listener: Called when one or more player states changed.
     */
    private var playerListener: Player.Listener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            // store state of playback
            val currentMediaId: String = player.currentMediaItem?.mediaId ?: String()
            playbackActive = isPlaying
            PreferencesHelper.saveIsPlaying(isPlaying)
            PreferencesHelper.saveCurrentStationId(currentMediaId)
            // reset restart counter
            //playbackRestartCounter = 0
            // save collection and player state

            collection = CollectionHelper.savePlaybackState(this@PlayerService, collection, currentMediaId, isPlaying)
            //updatePlayerState(station, playbackState)

            if (isPlaying) {
                // playback is active
            } else {
                // cancel sleep timer
                cancelSleepTimer()
                // reset metadata
                updateMetadata()

                // playback is not active
                // Not playing because playback is paused, ended, suppressed, or the player
                // is buffering, stopped or failed. Check player.getPlayWhenReady,
                // player.getPlaybackState, player.getPlaybackSuppressionReason and
                // player.getPlaybackError for details.
                when (player.playbackState) {
                    // player is able to immediately play from its current position
                    Player.STATE_READY -> {
                        LogHelper.e("PAUSED") // todo remove
                        // todo
                    }
                    // buffering - data needs to be loaded
                    Player.STATE_BUFFERING -> {
                        // todo
                    }
                    // player finished playing all media
                    Player.STATE_ENDED -> {
                        // todo
                    }
                    // initial state or player is stopped or playback failed
                    Player.STATE_IDLE -> {
                        // todo
                    }
                }
            }
        }

        override fun onMetadata(metadata: Metadata) {
            super.onMetadata(metadata)
            updateMetadata(AudioHelper.getMetadataString(metadata))
        }

    }
    /*
     * End of declaration
     */


    /*
     * Custom that handles Keys.ACTION_COLLECTION_CHANGED
     */
    private val collectionChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.hasExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE)) {
                val date = Date(intent.getLongExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE, 0L))

                if (date.after(collection.modificationDate)) {
                    LogHelper.v("PlayerService - reload collection after broadcast received.")
                    loadCollection(context)
                }
            }
        }
    }
    /*
     * End of declaration
     */


    /*
     * Custom AnalyticsListener that enables AudioFX equalizer integration
     */
    private val analyticsListener = object: AnalyticsListener {
        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            super.onAudioSessionIdChanged(eventTime, audioSessionId)
            // integrate with system equalizer (AudioFX)
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            sendBroadcast(intent)
        }
    }
    /*
     * End of declaration
     */

//    /*
//     * EventListener: Listener for ExoPlayer Events
//     */
//    private val playerListener = object : Player.Listener {
//
//        override fun onIsPlayingChanged(isPlaying: Boolean){
//            if (isPlaying) {
//                // active playback
//                handlePlaybackChange(PlaybackStateCompat.STATE_PLAYING)
//            } else {
//                // playback stopped by user
//                handlePlaybackChange(PlaybackStateCompat.STATE_STOPPED)
//            }
//        }
//
//        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
//            super.onPlayWhenReadyChanged(playWhenReady, reason)
//            if (!playWhenReady) {
//                // detect dismiss action
//                if (player.mediaItemCount == 0) {
//                    stopSelf()
//                }
//                when (reason) {
//                    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> {
//                        // playback reached end: try to resume
//                        handlePlaybackEnded()
//                    }
//                    else -> {
//                        // playback has been paused by OS
//                        // PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST or
//                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS or
//                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY or
//                        // PLAY_WHEN_READY_CHANGE_REASON_REMOTE
//                        handlePlaybackChange(PlaybackStateCompat.STATE_STOPPED)
//                    }
//                }
//            } else if (playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
//                handlePlaybackChange(PlaybackStateCompat.STATE_BUFFERING)
//            }
//        }
//
//
//        override fun onMetadata(metadata: Metadata) {
//            super.onMetadata(metadata)
//            for (i in 0 until metadata.length()) {
//                val entry = metadata[i]
//                // extract IceCast metadata
//                if (entry is IcyInfo) {
//                    val icyInfo: IcyInfo = entry as IcyInfo
//                    updateMetadata(icyInfo.title)
//                } else if (entry is IcyHeaders) {
//                    val icyHeaders = entry as IcyHeaders
//                    LogHelper.i(TAG, "icyHeaders:" + icyHeaders.name + " - " + icyHeaders.genre)
//                } else {
//                    LogHelper.w(TAG, "Unsupported metadata received (type = ${entry.javaClass.simpleName})")
//                    updateMetadata(null)
//                }
//                // TODO implement HLS metadata extraction (Id3Frame / PrivFrame)
//                // https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/metadata/Metadata.Entry.html
//            }
//        }
//    }
//
//    /*
//     * End of declaration
//     */

//
//
//    /*
//     * NotificationListener: handles foreground state of service
//     */
//    private val notificationListener = object : PlayerNotificationManager.NotificationListener {
//        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
//            super.onNotificationCancelled(notificationId, dismissedByUser)
//            stopForeground(true)
//            isForegroundService = false
//            stopSelf()
//        }
//
//        override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
//            super.onNotificationPosted(notificationId, notification, ongoing)
//            if (ongoing && !isForegroundService) {
//                ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, this@PlayerService.javaClass))
//                startForeground(Keys.NOW_PLAYING_NOTIFICATION_ID, notification)
//                isForegroundService = true
//            }
//        }
//    }
//    /*
//     * End of declaration
//     */



//    /*
//     * PlaybackPreparer: Handles prepare and play requests - as well as custom commands like sleep timer control
//     */
//    private val preparer = object : MediaSessionConnector.PlaybackPreparer {
//
//        override fun getSupportedPrepareActions(): Long =
//            PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
//                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
//                    PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH or
//                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
//
//        override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle?) = Unit
//
//        override fun onPrepare(playWhenReady: Boolean) {
//            if (station.isValid()) {
//                preparePlayer(playWhenReady)
//            } else {
//                val currentStationUuid: String = PreferencesHelper.loadLastPlayedStationUuid()
//                onPrepareFromMediaId(currentStationUuid, playWhenReady, null)
//            }
//        }
//
//        override fun onPrepareFromMediaId(mediaId: String, playWhenReady: Boolean, extras: Bundle?) {
//            // get station and start playback
//            station = CollectionHelper.getStation(collection, mediaId ?: String())
//            preparePlayer(playWhenReady)
//        }
//
//        override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?) {
//
//            // SPECIAL CASE: Empty query - user provided generic string e.g. 'Play music'
//            if (query.isEmpty()) {
//                // try to get first station
//                val stationMediaItem: MediaBrowserCompat.MediaItem? = collectionProvider.getFirstStation()
//                if (stationMediaItem != null) {
//                    onPrepareFromMediaId(stationMediaItem.mediaId!!, playWhenReady = true, extras = null)
//                } else {
//                    // unable to get the first station - notify user
//                    Toast.makeText(this@PlayerService, R.string.toastmessage_error_no_station_found, Toast.LENGTH_LONG).show()
//                    LogHelper.e(TAG, "Unable to start playback. Please add a radio station first. (Collection size = ${collection.stations.size} | provider initialized = ${collectionProvider.isInitialized()})")
//                }
//            }
//            // NORMAL CASE: Try to match station name and voice query
//            else {
//                val queryLowercase: String = query.lowercase(Locale.getDefault())
//                collectionProvider.stationListByName.forEach { mediaItem ->
//                    // get station name (here -> title)
//                    val stationName: String = mediaItem.description.title.toString().lowercase(Locale.getDefault())
//                    // FIRST: try to match the whole query
//                    if (stationName == queryLowercase) {
//                        // start playback
//                        onPrepareFromMediaId(mediaItem.description.mediaId!!, playWhenReady = true, extras = null)
//                        return
//                    }
//                    // SECOND: try to match parts of the query
//                    else {
//                        val words: List<String> = queryLowercase.split(" ")
//                        words.forEach { word ->
//                            if (stationName.contains(word)) {
//                                // start playback
//                                onPrepareFromMediaId(mediaItem.description.mediaId!!, playWhenReady = true, extras = null)
//                                return
//                            }
//                        }
//                    }
//                }
//                // NO MATCH: unable to match query - notify user
//                Toast.makeText(this@PlayerService, R.string.toastmessage_error_no_station_matches_search, Toast.LENGTH_LONG).show()
//                LogHelper.e(TAG, "Unable to find a station that matches your search query: $query")
//            }
//        }
//
//        override fun onCommand(player: Player,  command: String, extras: Bundle?, cb: ResultReceiver?): Boolean {
//            when (command) {
//                Keys.CMD_RELOAD_PLAYER_STATE -> {
//                    playerState = PreferencesHelper.loadPlayerState()
//                    return true
//                }
//                Keys.CMD_REQUEST_PROGRESS_UPDATE -> {
//                    if (cb != null) {
//                        // check if station is valid - assumes that then the player has been prepared as well
//                        if (station.isValid()) {
//                            val playbackProgressBundle: Bundle = bundleOf(Keys.RESULT_DATA_METADATA to metadataHistory)
//                            if (sleepTimerTimeRemaining > 0L) {
//                                playbackProgressBundle.putLong(Keys.RESULT_DATA_SLEEP_TIMER_REMAINING, sleepTimerTimeRemaining)
//                            }
//                            cb.send(Keys.RESULT_CODE_PERIODIC_PROGRESS_UPDATE, playbackProgressBundle)
//                            return true
//                        } else {
//                            return false
//                        }
//                    } else {
//                        return false
//                    }
//                }
//                Keys.CMD_START_SLEEP_TIMER -> {
//                    startSleepTimer()
//                    return true
//                }
//                Keys.CMD_CANCEL_SLEEP_TIMER -> {
//                    cancelSleepTimer()
//                    return true
//                }
//                Keys.CMD_PLAY_STREAM -> {
//                    // get station and start playback
//                    val streamUri: String = extras?.getString(Keys.KEY_STREAM_URI) ?: String()
//                    station = CollectionHelper.getStationWithStreamUri(collection, streamUri)
//                    preparePlayer(true)
//                    return true
//                }
//                else -> {
//                    return false
//                }
//            }
//        }
//    }
//    /*
//     * End of declaration
//     */





//    /* Overrides onCreate from Service */
//    override fun onCreate() {
//        super.onCreate()
//
//        // load modification date of collection
//        modificationDate = PreferencesHelper.loadCollectionModificationDate()
//
//        // get the package validator // todo can be local?
//        packageValidator = PackageValidator(this, R.xml.allowed_media_browser_callers)
//
//        // fetch the player state
//        playerState = PreferencesHelper.loadPlayerState()
//
//        // fetch the metadata history
//        metadataHistory = PreferencesHelper.loadMetadataHistory()
//
//        // create a new MediaSession
//        createMediaSession()
//
//        // create custom ForwardingPlayer used in Notification and playback control
//        forwardingPlayer = createForwardingPlayer()
//
//        // ExoPlayer manages MediaSession
//        mediaSessionConnector = MediaSessionConnector(mediaSession)
//        mediaSessionConnector.setPlaybackPreparer(preparer)
//        mediaSessionConnector.setQueueNavigator(object : TimelineQueueNavigator(mediaSession) {
//            override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
//                // create media description - used in notification
//                 return CollectionHelper.buildStationMediaDescription(this@PlayerService, station, getCurrentMetadata())
//            }
//        })
//
//        // initialize notification helper
//        notificationHelper = NotificationHelper(this, mediaSession.sessionToken, notificationListener)
////        notificationHelper.showNotificationForPlayer(forwardingPlayer)
//
//        // create and register collection changed receiver
//        collectionChangedReceiver = createCollectionChangedReceiver()
//        LocalBroadcastManager.getInstance(application).registerReceiver(collectionChangedReceiver, IntentFilter(Keys.ACTION_COLLECTION_CHANGED))
//
//        // load collection
//        collection = FileHelper.readCollection(this)
//    }


//    /* Overrides onStartCommand from Service */
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        super.onStartCommand(intent, flags, startId)
//        // handle start/stop requests issued via Intent - used for example by the home screen shortcuts
//        if (intent != null && intent.action == Keys.ACTION_STOP) {
//            player.stop()
//        }
//        if (intent != null && intent.action == Keys.ACTION_START) {
//            if (intent.hasExtra(Keys.EXTRA_STATION_UUID)) {
//                val stationUuid: String = intent.getStringExtra(Keys.EXTRA_STATION_UUID) ?: String()
//                station = CollectionHelper.getStation(collection, stationUuid)
//            } else if(intent.hasExtra(Keys.EXTRA_STREAM_URI)) {
//                val streamUri: String = intent.getStringExtra(Keys.EXTRA_STREAM_URI) ?: String()
//                station = CollectionHelper.getStationWithStreamUri(collection, streamUri)
//            } else {
//                station = CollectionHelper.getStation(collection, playerState.stationUuid)
//            }
//            if (station.isValid()) {
//                preparePlayer(true)
//            }
//        }
//        return Service.START_STICKY_COMPATIBILITY
//    }



//    /* Overrides onDestroy from Service */
//    override fun onDestroy() {
//        // set playback state if necessary
//        if (player.isPlaying) {
//            handlePlaybackChange(PlaybackStateCompat.STATE_STOPPED)
//        }
//        // release media session
//        mediaSession.run {
//            isActive = false
//            release()
//        }
//        // release player
//        player.removeAnalyticsListener(analyticsListener)
//        player.removeListener(playerListener)
//        player.release()
//    }





//    /* Overrides onGetRoot from MediaBrowserService */ // todo: implement a hierarchical structure -> https://github.com/googlesamples/android-UniversalMusicPlayer/blob/47da058112cee0b70442bcd0370c1e46e830c66b/media/src/main/java/com/example/android/uamp/media/library/BrowseTree.kt
//    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
//        // Credit: https://github.com/googlesamples/android-UniversalMusicPlayer (->  MusicService)
//        // LogHelper.d(TAG, "OnGetRoot: clientPackageName=$clientPackageName; clientUid=$clientUid ; rootHints=$rootHints")
//        // to ensure you are not allowing any arbitrary app to browse your app's contents, you need to check the origin
//        if (!packageValidator.isKnownCaller(clientPackageName, clientUid)) {
//            // request comes from an untrusted package
//            LogHelper.i(TAG, "OnGetRoot: Browsing NOT ALLOWED for unknown caller. "
//                    + "Returning empty browser root so all apps can use MediaController."
//                    + clientPackageName)
//            return BrowserRoot(Keys.MEDIA_BROWSER_ROOT_EMPTY, null)
//        } else {
//            // content style extras: see https://developer.android.com/training/cars/media#apply_content_style
//            val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
//            val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
//            val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
//            val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
//            val CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2
//            val rootExtras = bundleOf(
//                    CONTENT_STYLE_SUPPORTED to true,
//                    CONTENT_STYLE_BROWSABLE_HINT to CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
//                    CONTENT_STYLE_PLAYABLE_HINT to CONTENT_STYLE_LIST_ITEM_HINT_VALUE
//            )
//            // check if rootHints contained EXTRA_RECENT - return BrowserRoot with MEDIA_BROWSER_ROOT_RECENT in that case
//            val isRecentRequest = rootHints?.getBoolean(BrowserRoot.EXTRA_RECENT) ?: false
//            val browserRootPath: String = if (isRecentRequest) Keys.MEDIA_BROWSER_ROOT_RECENT else Keys.MEDIA_BROWSER_ROOT
//            return BrowserRoot(browserRootPath, rootExtras)
//        }
//    }
//
//
//    /* Overrides onLoadChildren from MediaBrowserService */
//    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
//        if (!collectionProvider.isInitialized()) {
//            // use result.detach to allow calling result.sendResult from another thread:
//            result.detach()
//            collectionProvider.retrieveMedia(this, collection, object: CollectionProvider.CollectionProviderCallback {
//                override fun onStationListReady(success: Boolean) {
//                    if (success) {
//                        loadChildren(parentId, result)
//                    }
//                }
//            })
//        } else {
//            // if music catalog is already loaded/cached, load them into result immediately
//            loadChildren(parentId, result)
//        }
//    }


//    /* Updates media session and save state */
//    private fun handlePlaybackChange(playbackState: Int) {
//        // reset restart counter
//        playbackRestartCounter = 0
//        // save collection state and player state
//        collection = CollectionHelper.savePlaybackState(this, collection, station, playbackState)
//        updatePlayerState(station, playbackState)
//        if (player.isPlaying) {
//            notificationHelper.showNotificationForPlayer(forwardingPlayer)
//        } else {
//            updateMetadata(null)
//        }
//    }


//    /* Try to restart Playback */
//    private fun handlePlaybackEnded() {
//        // restart playback for up to five times
//        if (playbackRestartCounter < 5) {
//            playbackRestartCounter++
//            player.stop()
//            player.play()
//        } else {
//            player.stop()
//            Toast.makeText(this, this.getString(R.string.toastmessage_error_restart_playback_failed), Toast.LENGTH_LONG).show()
//        }
//    }


//    /* Prepares player with media source created from current station */
//    private fun preparePlayer(playWhenReady: Boolean) {
//        // sanity check
//        if (!station.isValid()) {
//            LogHelper.e(TAG, "Unable to start playback. No radio station has been loaded.")
//            return
//        }
//
//        // stop playback if necessary
//        if (player.isPlaying) { player.stop() }
//
//        // build media item.
//        val mediaItem: MediaItem = MediaItem.fromUri(station.getStreamUri())
//
//        // create DataSource.Factory - produces DataSource instances through which media data is loaded
//        val dataSourceFactory: DataSource.Factory = DefaultHttpDataSource.Factory().apply {
//            setUserAgent(Util.getUserAgent(this, Keys.APPLICATION_NAME))
//            // follow http redirects
//            setAllowCrossProtocolRedirects(true)
//        }
//
//        // create MediaSource
//        val mediaSource: MediaSource
//        if (station.streamContent in Keys.MIME_TYPE_HLS || station.streamContent in Keys.MIME_TYPES_M3U) {
//            // HLS media source
//            //Toast.makeText(this, this.getString(R.string.toastmessage_stream_may_not_work), Toast.LENGTH_LONG).show()
//            mediaSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
//        } else {
//            // MPEG or OGG media source
//            mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).setContinueLoadingCheckIntervalBytes(32).createMediaSource(mediaItem)
//        }
//
//        // set source and prepare player
//        player.setMediaSource(mediaSource)
//        // player.setMediaItem() - unable to use here, because different media items may need different MediaSourceFactories to work properly
//        player.prepare()
//
//        // update media session connector using custom player
//        mediaSessionConnector.setPlayer(forwardingPlayer)
//
//        // reset metadata to station name
//        updateMetadata(station.name)
//
//        // set playWhenReady state
//        player.playWhenReady = playWhenReady
//    }


//    /* Starts sleep timer / adds default duration to running sleeptimer */
//    private fun startSleepTimer() {
//        // stop running timer
//        if (sleepTimerTimeRemaining > 0L && this::sleepTimer.isInitialized) {
//            sleepTimer.cancel()
//        }
//        // initialize timer
//        sleepTimer = object:CountDownTimer(Keys.SLEEP_TIMER_DURATION + sleepTimerTimeRemaining, Keys.SLEEP_TIMER_INTERVAL) {
//            override fun onFinish() {
//                LogHelper.v(TAG, "Sleep timer finished. Sweet dreams.")
//                // reset time remaining
//                sleepTimerTimeRemaining = 0L
//                // stop playback
//                player.stop()
//            }
//            override fun onTick(millisUntilFinished: Long) {
//                sleepTimerTimeRemaining = millisUntilFinished
//            }
//        }
//        // start timer
//        sleepTimer.start()
//    }


//    /* Cancels sleep timer */
//    private fun cancelSleepTimer() {
//        if (this::sleepTimer.isInitialized) {
//            sleepTimerTimeRemaining = 0L
//            sleepTimer.cancel()
//        }
//    }


//    /* Loads media items into result - assumes that collectionProvider is initialized */
//    private fun loadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
//        val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
//        when (parentId) {
//            Keys.MEDIA_BROWSER_ROOT -> {
//                collectionProvider.stationListByName.forEach { item ->
//                    mediaItems.add(item)
//                }
//            }
//            Keys.MEDIA_BROWSER_ROOT_RECENT -> {
////                // un-comment (and implement ;-) ), if you want the media resumption notification to be shown
////                val recentStation = collectionProvider.getFirstStation() // todo get last played station
////                if (recentStation != null) mediaItems.add(recentStation)
//            }
//            Keys.MEDIA_BROWSER_ROOT_EMPTY -> {
//                // do nothing
//            }
//            else -> {
//                // log error
//                LogHelper.w(TAG, "Skipping unmatched parentId: $parentId")
//            }
//        }
//        result.sendResult(mediaItems)
//    }
//
//



//    /* Reads collection of stations from storage using GSON */
//    private fun loadCollection(context: Context) {
//        LogHelper.v(TAG, "Loading collection of stations from storage")
//        CoroutineScope(Main).launch {
//            // load collection on background thread
//            val deferred: Deferred<Collection> = async(Dispatchers.Default) { FileHelper.readCollectionSuspended(context) }
//            // wait for result and update collection
//            collection = deferred.await()
//            // special case: trigger metadata view update for stations that have no metadata
//            if (playerState.playbackState == PlaybackState.STATE_PLAYING && station.name == getCurrentMetadata()) {
//                station = CollectionHelper.getStation(collection, station.uuid)
//                updateMetadata(null)
//            }
//        }
//    }
//
//
//    /* Updates and saves the state of the player ui */
//    private fun updatePlayerState(station: Station, playbackState: Int) {
//        if (station.isValid()) {
//            playerState.stationUuid = station.uuid
//        }
//        playerState.playbackState = playbackState
//        PreferencesHelper.savePlayerState(playerState)
//    }



}
