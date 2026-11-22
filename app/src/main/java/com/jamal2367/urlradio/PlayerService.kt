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
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.jamal2367.urlradio.core.Collection
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.AudioHelper
import com.jamal2367.urlradio.helpers.CollectionChanges
import com.jamal2367.urlradio.helpers.CollectionHelper
import com.jamal2367.urlradio.helpers.FileHelper
import com.jamal2367.urlradio.helpers.PreferencesHelper
import com.jamal2367.urlradio.recording.StreamRecorder
import com.jamal2367.urlradio.widget.requestTileUpdate
import com.jamal2367.urlradio.widget.RadioTileState
import com.jamal2367.urlradio.widget.RadioWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


/*
 * PlayerService class
 */
@UnstableApi
class PlayerService : MediaLibraryService() {

    /*
     * Value of MediaBrowserServiceCompat.BrowserRoot.EXTRA_RECENT, inlined so the app does
     * not need the legacy androidx.media library for a single string constant. The system
     * passes this when it asks for a resumable item after a reboot.
     */
    private val extraRecent: String = "android.service.media.extra.RECENT"

    /* Define log tag */
    private val tag: String = PlayerService::class.java.simpleName

    /* Main class variables */
    private lateinit var player: Player
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var sleepTimer: CountDownTimer
    var sleepTimerTimeRemaining: Long = 0L
    private var sleepTimerEndTime: Long = 0L
    private val librarySessionCallback = CustomMediaLibrarySessionCallback()
    // Lives as long as the service does. onDestroy cancels it, which is what takes the place
    // of unregistering the broadcast receiver this replaced.
    private val serviceScope = CoroutineScope(SupervisorJob() + Main)

    private var collection: Collection = Collection()
    private lateinit var metadataHistory: MutableList<String>
    private var bufferSizeMultiplier: Int = PreferencesHelper.loadBufferSizeMultiplier()
    private var playbackRestartCounter: Int = 0
    private var playLastStation: Boolean = false
    private var chip: String? = null // the tag tab (or voice tag) the station was played from
    private var manuallyCancelledSleepTimer = false
    private lateinit var recorder: StreamRecorder
    // set while a recording runs with a timer: when it ends (SystemClock.elapsedRealtime)
    private var recordingEndsAt: Long = 0L
    private val recordingTimerHandler = Handler(Looper.getMainLooper())
    private val recordingTimerRunnable = Runnable { stopRecording() }


    /* Overrides onCreate from Service */
    override fun onCreate() {
        super.onCreate()
        // load collection
        collection = FileHelper.readCollection(this)
        // the recorder has to exist before the player, whose audio chain feeds it
        recorder = StreamRecorder(this, recorderListener)
        // start listening for collection changes made elsewhere in the app
        observeCollectionChanges()
        // initialize player and session
        initializePlayer()
        initializeSession()
        // Media3 only shows the media notification for a session that has been added to the
        // service, and on its own adds it only once a controller connects - the app's screen,
        // the system's media controls. Started from the widget or the quick settings tile there
        // is none yet, so playback ran without notification until the app was opened.
        addSession(mediaLibrarySession)
        val notificationProvider: DefaultMediaNotificationProvider = CustomNotificationProvider()
        notificationProvider.setSmallIcon(R.drawable.ic_notification_app_icon_white_24dp)
        setMediaNotificationProvider(notificationProvider)
        // fetch the metadata history
        metadataHistory = PreferencesHelper.loadMetadataHistory()
        // start listening for changes in shared preferences - without this the
        // "large buffer size" setting only took effect after a process restart
        PreferencesHelper.registerPreferenceChangeListener(sharedPreferenceChangeListener)
    }


    /* Overrides onDestroy from Service */
    override fun onDestroy() {
        // stop listening for changes in shared preferences
        PreferencesHelper.unregisterPreferenceChangeListener(sharedPreferenceChangeListener)
        // stop listening for collection changes
        serviceScope.cancel()
        // saves a recording that is still running
        recordingTimerHandler.removeCallbacks(recordingTimerRunnable)
        recorder.release()
        // player.removeAnalyticsListener(analyticsListener)
        player.removeListener(playerListener)
        player.release()
        mediaLibrarySession.release()
        super.onDestroy()
    }


    /*
     * The widget and the quick settings tile start the service with an action of their own; anything
     * else - media buttons among it - goes to MediaSessionService as before.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Keys.ACTION_TOGGLE_PLAYBACK -> {
                togglePlayback()
                return START_NOT_STICKY
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }


    /* Play / pause - starting the station played last when nothing is loaded yet */
    private fun togglePlayback() {
        if (player.playWhenReady) {
            player.pause()
            return
        }
        if (player.currentMediaItem == null || player.playbackState == Player.STATE_IDLE) {
            if (collection.stations.isEmpty()) return
            player.setMediaItem(CollectionHelper.getRecent(this, collection))
        }
        player.prepare()
        player.play()
    }


    /* Shows the current station, state and title in the home screen widget and the quick settings tile */
    private fun updateWidgets() {
        val mediaId: String = player.currentMediaItem?.mediaId ?: PreferencesHelper.loadLastPlayedStationUuid()
        val station: Station? = collection.stations.firstOrNull { it.uuid == mediaId }
            ?: collection.stations.takeIf { it.isNotEmpty() }?.let { CollectionHelper.getStation(collection, mediaId) }
        // "On" from the moment playback is asked for, buffering included - the same the play
        // button in the app shows. Waiting for actual sound left widget and tile lagging behind.
        val isActive: Boolean = player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
        RadioWidget.update(this, station, isActive, metadataHistory.lastOrNull().orEmpty())
        RadioTileState.update(
            isActive,
            station?.let { CollectionHelper.nameWithoutTags(it.name) },
            metadataHistory.lastOrNull().orEmpty()
        )
        requestTileUpdate(this)
    }


    /* Overrides onTaskRemoved from Service */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady) {
            stopSelf()
        }
    }


    /* Overrides onGetSession from MediaSessionService */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }


    /* Initializes the ExoPlayer */
    private fun initializePlayer() {
        val exoPlayer: ExoPlayer = ExoPlayer.Builder(this, RecordingRenderersFactory()).apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            setLoadControl(createDefaultLoadControl(bufferSizeMultiplier))
            setMediaSourceFactory(
                DefaultMediaSourceFactory(this@PlayerService).setLoadErrorHandlingPolicy(
                    loadErrorHandlingPolicy
                )
            )
        }.build()
        exoPlayer.addAnalyticsListener(analyticsListener)
        exoPlayer.addListener(playerListener)

        // manually add seek to next and seek to previous since headphones issue them, and they are translated to next and previous station
        //
        // What those commands then do is shaped here rather than in
        // MediaSession.Callback.onPlayerCommandRequest, which is deprecated: the replacement
        // for changing what a player command does is the player itself.
        player = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon().add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS).build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return availableCommands.contains(command)
            }

            override fun getDuration(): Long {
                return C.TIME_UNSET // this will hide progress bar for HLS stations in the notification
            }

            /* Headphone "next" means the next station: it replaces the current one and starts. */
            override fun seekToNext() {
                setMediaItem(
                    CollectionHelper.getNextMediaItem(
                        this@PlayerService,
                        collection.copy(stations = CollectionHelper.getStations(collection, chip).toMutableList()),
                        currentMediaItem?.mediaId ?: String()
                    )
                )
                // super, not the overrides below: a fresh station is already at the
                // live edge, so it neither needs the resumption special case nor the seek.
                super.prepare()
                super.play()
            }

            /* Headphone "previous", likewise. */
            override fun seekToPrevious() {
                setMediaItem(
                    CollectionHelper.getPreviousMediaItem(
                        this@PlayerService,
                        collection.copy(stations = CollectionHelper.getStations(collection, chip).toMutableList()),
                        currentMediaItem?.mediaId ?: String()
                    )
                )
                super.prepare()
                super.play()
            }

            override fun prepare() {
                // Special case: the system asked for media resumption (see also
                // onGetLibraryRoot), so the last station is put in place first.
                if (playLastStation) {
                    addMediaItem(CollectionHelper.getRecent(this@PlayerService, collection))
                    playLastStation = false
                }
                super.prepare()
            }

            override fun play() {
                // A live stream that was paused would otherwise resume out of a stale buffer,
                // so playback returns to the start of the live window first.
                if (!isPlaying) seekTo(0)
                super.play()
            }
        }
    }


    /* Initializes the MediaSession */
    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntent(intent)
            getPendingIntent(0, if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_CANCEL_CURRENT)
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, librarySessionCallback).apply {
            setSessionActivity(pendingIntent)
        }.build()
    }


    /* Creates a LoadControl - increase buffer size by given factor */
    private fun createDefaultLoadControl(factor: Int): DefaultLoadControl {
        val builder = DefaultLoadControl.Builder()
        builder.setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
        builder.setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * factor,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * factor,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS * factor,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS * factor
        )
        return builder.build()
    }


    /* Starts sleep timer / adds default duration to running sleep timer */
    private fun startSleepTimer(selectedTimeMillis: Long) {
        // stop running timer
        if (sleepTimerTimeRemaining > 0L && this::sleepTimer.isInitialized) {
            sleepTimer.cancel()
        }

        // set the end time of the sleep timer
        sleepTimerEndTime = System.currentTimeMillis() + selectedTimeMillis

        // initialize timer
        sleepTimer = object : CountDownTimer(selectedTimeMillis, 1000) {
            override fun onFinish() {
                Log.v(tag, "Sleep timer finished. Sweet dreams.")
                sleepTimerTimeRemaining = 0L
                player.stop()
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
        if (this::sleepTimer.isInitialized) {
            if (manuallyCancelledSleepTimer) {
                sleepTimerTimeRemaining = 0L
                sleepTimer.cancel()
            }
            manuallyCancelledSleepTimer = false
        }
        // store timer state
        PreferencesHelper.saveSleepTimerRunning(isRunning = false)
    }


    /* Function to cancel the timer manually */
    fun manuallyCancelSleepTimer() {
        manuallyCancelledSleepTimer = true
        cancelSleepTimer()
    }


    /*
     * Starts recording the station that is playing. With a duration greater than zero the
     * recording stops on its own once that much time has passed.
     */
    private fun startRecording(durationMillis: Long = 0L) {
        if (recorder.isRecording || !player.playWhenReady) return
        val stationName: String = player.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty()
        recorder.start(
            stationName = stationName,
            format = PreferencesHelper.loadRecordingFormat(),
            bitrateKbps = PreferencesHelper.loadRecordingBitrate(),
        )
        if (durationMillis > 0L) {
            recordingEndsAt = SystemClock.elapsedRealtime() + durationMillis
            recordingTimerHandler.postDelayed(recordingTimerRunnable, durationMillis)
        }
        broadcastRecordingState()
    }


    /* Stops a running recording - what was recorded so far is saved */
    private fun stopRecording() {
        recordingTimerHandler.removeCallbacks(recordingTimerRunnable)
        recordingEndsAt = 0L
        if (!recorder.isRecording) return
        recorder.stop()
        broadcastRecordingState()
    }


    /* The recording state as handed to controllers */
    private fun recordingStateBundle(): Bundle = Bundle().apply {
        val remaining: Long = if (recorder.isRecording && recordingEndsAt > 0L) {
            (recordingEndsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else {
            0L
        }
        putBoolean(Keys.EXTRA_RECORDING_ACTIVE, recorder.isRecording)
        putLong(Keys.EXTRA_RECORDING_ELAPSED, recorder.elapsedMillis)
        putLong(Keys.EXTRA_RECORDING_REMAINING, remaining)
    }


    /* Pushes the recording state - plus an optional event - to every connected controller */
    private fun broadcastRecordingState(savedFile: String? = null, failed: Boolean = false) {
        if (!this::mediaLibrarySession.isInitialized) return
        val extras: Bundle = recordingStateBundle().apply {
            savedFile?.let { putString(Keys.EXTRA_RECORDING_SAVED_FILE, it) }
            putBoolean(Keys.EXTRA_RECORDING_FAILED, failed)
        }
        mediaLibrarySession.broadcastCustomCommand(
            SessionCommand(Keys.CMD_RECORDING_UPDATED, Bundle.EMPTY),
            extras
        )
    }


    /* Hears back from the recorder - main thread */
    private val recorderListener = object : StreamRecorder.Listener {
        override fun onRecordingSaved(fileName: String) {
            broadcastRecordingState(savedFile = fileName)
        }

        override fun onRecordingFailed() {
            // the recorder has stopped itself - a timer has nothing left to stop
            if (!recorder.isRecording) {
                recordingTimerHandler.removeCallbacks(recordingTimerRunnable)
                recordingEndsAt = 0L
            }
            broadcastRecordingState(failed = true)
        }
    }


    /*
     * Builds the player's renderers with a TeeAudioProcessor in the audio chain: it hands the
     * decoded audio to the recorder on its way to the speaker. Without a running recording
     * the recorder ignores it, so playback is unaffected.
     */
    private inner class RecordingRenderersFactory : DefaultRenderersFactory(this@PlayerService) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean
        ): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(TeeAudioProcessor(recorder)))
                .build()
        }
    }


    /*
     * Gives the playing station the name and bitrate its server announces - but only where
     * the station has none of its own: a station added by its bare address is called by that
     * address until now, and a name the user typed in is never replaced.
     */
    private fun adoptStreamInfo(headers: IcyHeaders) {
        val stationUuid: String = player.currentMediaItem?.mediaId ?: return
        val station: Station = collection.stations.firstOrNull { it.uuid == stationUuid } ?: return
        var changed = false
        val name: String = headers.name?.trim().orEmpty()
        if (name.isNotEmpty() && !station.nameManuallySet && station.name == station.getStreamUri()) {
            station.name = name
            changed = true
        }
        // IcyHeaders carries the bitrate in bit/s, the station keeps kbit/s
        if (station.bitrate == 0 && headers.bitrate > 0) {
            station.bitrate = headers.bitrate / 1000
            changed = true
        }
        if (changed) {
            updateWidgets()
            Log.i(tag, "Adopted stream info for ${station.uuid}: name=${station.name}, bitrate=${station.bitrate}")
            CollectionHelper.saveCollection(this, collection)
        }
    }


    /* Updates metadata */
    private fun updateMetadata(metadata: String = String()) {
        /*
         * Streams that send no title at all fall back to the name of the station. The name is
         * carried as the artist of the current media item (see CollectionHelper.buildMediaItem).
         * Reading it with toString() on a nullable used to put the literal string "null" into
         * the history whenever there was neither a title nor a loaded media item.
         */
        val stationName: String = player.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty()
        val metadataString: String = metadata.trim().ifEmpty { stationName.trim() }
        if (metadataString.isEmpty()) return
        // nothing to do when the stream repeats the entry that is already at the top
        if (metadataHistory.lastOrNull() == metadataString) return
        // remove duplicates
        if (metadataHistory.contains(metadataString)) {
            metadataHistory.removeAll { it == metadataString }
        }
        // append metadata to metadata history
        metadataHistory.add(metadataString)
        // trim metadata list
        if (metadataHistory.size > Keys.DEFAULT_SIZE_OF_METADATA_HISTORY) {
            metadataHistory.removeAt(0)
        }
        // save history
        PreferencesHelper.saveMetadataHistory(metadataHistory)
        // hand the new history to the UI
        broadcastMetadataHistory()
        if (this::metadataHistory.isInitialized && this::player.isInitialized) updateWidgets()
    }


    /*
     * Pushes the metadata history to every connected controller.
     *
     * Without this the UI only ever asked for the history when playback started or when it
     * (re)connected, so a track change during playback stayed invisible until the station was
     * restarted.
     */
    private fun broadcastMetadataHistory() {
        if (!this::mediaLibrarySession.isInitialized) return
        val extras = Bundle().apply {
            putStringArrayList(Keys.EXTRA_METADATA_HISTORY, ArrayList(metadataHistory))
        }
        mediaLibrarySession.broadcastCustomCommand(
            SessionCommand(Keys.CMD_METADATA_UPDATED, Bundle.EMPTY),
            extras
        )
    }


    /* Reads collection of stations from storage using GSON */
    private fun loadCollection(context: Context) {
        Log.v(tag, "Loading collection of stations from storage")
        CoroutineScope(Main).launch {
            // load collection on background thread
            val deferred: Deferred<Collection> =
                async(Dispatchers.Default) { FileHelper.readCollectionSuspended(context) }
            // wait for result and update collection
            collection = deferred.await()
            // a new logo or name shows in the widget right away
            updateWidgets()
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
    private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val updatedMediaItems: List<MediaItem> =
                mediaItems.map { mediaItem ->
                    val query: String? = mediaItem.requestMetadata.searchQuery
                    if (query == null) {
                        chip = mediaItem.mediaId.substringBefore("/", "").ifEmpty { null }
                        CollectionHelper.getItem(this@PlayerService, collection, mediaItem.mediaId)
                    } else {
                        // Voice request: "play jazz" plays the first #jazz station, "play Lush"
                        // the station whose name matches, "WFPK Louisville" the station sharing
                        // the most words (a word one letter off counts, "Lsh" -> "Lush"),
                        // otherwise the last played one.
                        // Either way next/previous then runs through all stations.
                        Log.v(tag, "Voice search: $query")
                        chip = null
                        val wordsOf = { s: String -> s.lowercase().split(Regex("[^\\p{L}\\d]+")).filter(String::isNotEmpty) }
                        val said: List<String> = wordsOf(query)
                        val words: List<String> = said - setOf("radio", "fm", "am", "the", "station") // too common to pick a station
                        val shared = { s: Station -> wordsOf(s.name).count { w -> words.any { it == w || it.length >= 3 && CollectionHelper.distance(it, w) <= it.length / 6 + 1 } } }
                        val voiceTag: String? = collection.stations.flatMap { CollectionHelper.getTags(it.name) }.firstOrNull { it.drop(1) in said }
                        val q: String = query.filter(Char::isLetterOrDigit)
                        (voiceTag?.let { CollectionHelper.getStations(collection, it).firstOrNull() } ?: collection.stations.firstOrNull {
                            val name: String = it.name.filter(Char::isLetterOrDigit)
                            q.isNotEmpty() && name.isNotEmpty() && (q.contains(name, true) || name.contains(q, true))
                        } ?: collection.stations.maxByOrNull(shared)?.takeIf { shared(it) > 0 })
                            ?.let { CollectionHelper.buildMediaItem(this@PlayerService, it) }
                            ?: CollectionHelper.getRecent(this@PlayerService, collection)
                    }
                }
            return Futures.immediateFuture(updatedMediaItems)


//            val updatedMediaItems = mediaItems.map { mediaItem ->
//                mediaItem.buildUpon().apply {
//                    setUri(mediaItem.requestMetadata.mediaUri)
//                }.build()
//            }
//            return Futures.immediateFuture(updatedMediaItems)
        }


        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // add custom commands
            val connectionResult: MediaSession.ConnectionResult = super.onConnect(session, controller)
            val builder: SessionCommands.Builder = connectionResult.availableSessionCommands.buildUpon()
            builder.add(SessionCommand(Keys.CMD_START_SLEEP_TIMER, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_REQUEST_METADATA_HISTORY, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_METADATA_UPDATED, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_PLAY_STREAM, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_START_RECORDING, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_STOP_RECORDING, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_REQUEST_RECORDING_STATE, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_RECORDING_UPDATED, Bundle.EMPTY))
            // A radio has no queue: Android Auto and the system (whose commands the media
            // notification controller sets) get no GET_TIMELINE, so no queue button. The app's
            // own screen keeps it, since it needs the current station to show and pause it.
            val ownScreen: Boolean = controller.packageName == packageName && !session.isMediaNotificationController(controller)
            val playerCommands: Player.Commands = if (ownScreen) connectionResult.availablePlayerCommands
                else connectionResult.availablePlayerCommands.buildUpon().remove(Player.COMMAND_GET_TIMELINE).build()
            return MediaSession.ConnectionResult.accept(builder.build(), playerCommands)
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val children: List<MediaItem> = CollectionHelper.getChildren(this@PlayerService, collection, parentId)
            session.notifyChildrenChanged(browser, parentId, children.size, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children: List<MediaItem> = CollectionHelper.getChildren(this@PlayerService, collection, parentId)
            return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return if (params?.extras?.containsKey(extraRecent) == true) {
                // special case: system requested media resumption via extraRecent
                playLastStation = true
                Futures.immediateFuture(LibraryResult.ofItem(CollectionHelper.getRecent(this@PlayerService, collection), params))
            } else {
                Futures.immediateFuture(LibraryResult.ofItem(CollectionHelper.getRootItem(), params))
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item: MediaItem = CollectionHelper.getItem(this@PlayerService, collection, mediaId)
            return Futures.immediateFuture(LibraryResult.ofItem(item, /* params = */ null))
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                Keys.CMD_START_SLEEP_TIMER -> {
                    val selectedTimeMillis = args.getLong(Keys.SLEEP_TIMER_DURATION)
                    startSleepTimer(selectedTimeMillis)
                }
                Keys.CMD_CANCEL_SLEEP_TIMER -> {
                    manuallyCancelSleepTimer()
                }
                Keys.CMD_START_RECORDING -> {
                    startRecording(args.getLong(Keys.EXTRA_RECORDING_DURATION, 0L))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, recordingStateBundle()))
                }
                Keys.CMD_STOP_RECORDING -> {
                    stopRecording()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, recordingStateBundle()))
                }
                Keys.CMD_REQUEST_RECORDING_STATE -> {
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, recordingStateBundle()))
                }
                Keys.CMD_PLAY_STREAM -> {
                    // plays a stream address that is not part of the collection - used by the
                    // exported com.jamal2367.urlradio.action.START intent (see EXTRA_STREAM_URI)
                    val streamUri: String = args.getString(Keys.KEY_STREAM_URI) ?: String()
                    if (streamUri.isEmpty()) {
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    }
                    val station = Station(
                        name = streamUri,
                        streamUris = mutableListOf(streamUri),
                        streamContent = Keys.MIME_TYPE_OCTET_STREAM
                    )
                    player.setMediaItem(CollectionHelper.buildMediaItem(this@PlayerService, station))
                    player.prepare()
                    player.play()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING -> {
                    val resultBundle = Bundle()
                    resultBundle.putLong(Keys.EXTRA_SLEEP_TIMER_REMAINING, sleepTimerTimeRemaining)
                    return Futures.immediateFuture(
                        SessionResult(
                            SessionResult.RESULT_SUCCESS,
                            resultBundle
                        )
                    )
                }
                Keys.CMD_REQUEST_METADATA_HISTORY -> {
                    val resultBundle = Bundle()
                    resultBundle.putStringArrayList(
                        Keys.EXTRA_METADATA_HISTORY,
                        ArrayList(metadataHistory)
                    )
                    return Futures.immediateFuture(
                        SessionResult(
                            SessionResult.RESULT_SUCCESS,
                            resultBundle
                        )
                    )
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

    }


    /*
    * NotificationProvider to customize Notification actions
    */
    private inner class CustomNotificationProvider :
        DefaultMediaNotificationProvider(this@PlayerService) {
        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean
        ): ImmutableList<CommandButton> {
            // ICON_UNDEFINED with a custom resource id, rather than one of the predefined
            // CommandButton.ICON_* constants: those would take precedence wherever they are
            // available and the app's own notification icons would stop being used. This is
            // what the deprecated Builder() + setIconResId pair did, spelled out.
            val seekToPreviousCommandButton =
                CommandButton.Builder(CommandButton.ICON_UNDEFINED).apply {
                    setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                    setCustomIconResId(R.drawable.ic_notification_skip_to_previous_36dp)
                    setEnabled(true)
                }.build()
            val playCommandButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED).apply {
                setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                setCustomIconResId(if (player.isPlaying) R.drawable.ic_notification_stop_36dp else R.drawable.ic_notification_play_36dp)
                setEnabled(true)
            }.build()
            val seekToNextCommandButton =
                CommandButton.Builder(CommandButton.ICON_UNDEFINED).apply {
                    setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                    setCustomIconResId(R.drawable.ic_notification_skip_to_next_36dp)
                    setEnabled(true)
                }.build()
            val commandButtons: MutableList<CommandButton> = mutableListOf(
                seekToPreviousCommandButton,
                playCommandButton,
                seekToNextCommandButton
            )
            return ImmutableList.copyOf(commandButtons)
        }
    }


    /*
     * Player.Listener: Called when one or more player states changed.
     */
    private var playerListener: Player.Listener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            // store state of playback
            val currentMediaId: String = player.currentMediaItem?.mediaId ?: String()
            PreferencesHelper.saveIsPlaying(isPlaying)
            PreferencesHelper.saveCurrentStationId(currentMediaId)
            // after saving: the quick settings tile reads the saved state
            updateWidgets()
            // reset restart counter
            playbackRestartCounter = 0
            // save collection and player state

            collection = CollectionHelper.savePlaybackState(
                this@PlayerService,
                collection,
                currentMediaId,
                isPlaying
            )
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

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            // widget and tile follow the request right away, not only once sound comes
            updateWidgets()
            if (!playWhenReady) {
                // paused by the user, the system or the end of the stream: the recording ends
                // here. A mere rebuffer does not change playWhenReady and keeps it running.
                stopRecording()
                when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> {
                        // playback reached end: stop / end playback
                    }
                    else -> {
                        // playback has been paused by user or OS: update media session and save state
                        // PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY or
                        // PLAY_WHEN_READY_CHANGE_REASON_REMOTE
                        // handlePlaybackChange(PlaybackStateCompat.STATE_PAUSED)
                    }
                }
            }
        }


        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            updateWidgets()
            // stopped (also by the sleep timer), failed or ended
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                stopRecording()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            // a recording belongs to one station
            stopRecording()
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Log.d(tag, "PlayerError occurred: ${error.errorCodeName}")
            // todo: test if playback needs to be restarted
        }


        override fun onMetadata(metadata: Metadata) {
            super.onMetadata(metadata)
            // what an Icecast / Shoutcast server says about itself, at the start of the stream
            (0 until metadata.length()).map { metadata.get(it) }.filterIsInstance<IcyHeaders>().firstOrNull()?.let {
                adoptStreamInfo(it)
            }
            val metadataString: String = AudioHelper.getMetadataString(metadata)
            // HLS segments also carry ID3 data without a title (timestamps and the like).
            // Only an actual title - or an Icecast title update, which may deliberately be
            // empty - changes what is shown; otherwise the display would flip back to the
            // station name with every such segment.
            if (metadataString.isNotEmpty() || (0 until metadata.length()).any { metadata.get(it) is IcyInfo }) {
                updateMetadata(metadataString)
            }
        }

    }


    /*
     * Custom LoadErrorHandlingPolicy that network drop-outs
     */
    private val loadErrorHandlingPolicy: DefaultLoadErrorHandlingPolicy = object: DefaultLoadErrorHandlingPolicy()  {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            // try to reconnect every 5 seconds - up to 20 times
            if (loadErrorInfo.errorCount <= Keys.DEFAULT_MAX_RECONNECTION_COUNT && loadErrorInfo.exception is HttpDataSource.HttpDataSourceException) {
                return Keys.RECONNECTION_WAIT_INTERVAL
//            } else {
//                CoroutineScope(Main).launch {
//                    player.stop()
//                }
            }
            return C.TIME_UNSET
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int {
            return Int.MAX_VALUE
        }
    }


    /*
     * Reloads the collection whenever anything else writes it -- the UI adding, editing or
     * removing a station, or a restored backup.
     */
    private fun observeCollectionChanges() {
        serviceScope.launch {
            CollectionChanges.events.collect { date ->
                if (date.after(collection.modificationDate)) {
                    Log.v(tag, "PlayerService - reload collection after change announced.")
                    loadCollection(this@PlayerService)
                }
            }
        }
    }


    /*
    * Defines the listener for changes in shared preferences
    */
    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                Keys.PREF_LARGE_BUFFER_SIZE -> {
                    val newMultiplier: Int = PreferencesHelper.loadBufferSizeMultiplier()
                    if (newMultiplier != bufferSizeMultiplier) {
                        bufferSizeMultiplier = newMultiplier
                        // only swap the player while it is idle - doing it during playback
                        // would cut the stream off mid-sentence
                        if (!player.isPlaying && !player.isLoading) {
                            recreatePlayer()
                        }
                    }
                }
            }
        }


    /*
     * Rebuilds the player with the current buffer size and hands it to the session.
     *
     * initializePlayer() on its own only reassigns the `player` field. The MediaLibrarySession
     * was built with the previous instance and would keep using it, so the new buffer size
     * would never reach playback and the old player would leak. Both are handled here.
     */
    private fun recreatePlayer() {
        val previousPlayer: Player = player
        previousPlayer.removeListener(playerListener)
        initializePlayer()
        mediaLibrarySession.player = player
        previousPlayer.release()
        Log.v(tag, "Player rebuilt with buffer size multiplier $bufferSizeMultiplier.")
    }


    /*
     * Custom AnalyticsListener that enables AudioFX equalizer integration
     */
    private val analyticsListener = object : AnalyticsListener {
        override fun onAudioSessionIdChanged(
            eventTime: AnalyticsListener.EventTime,
            audioSessionId: Int
        ) {
            super.onAudioSessionIdChanged(eventTime, audioSessionId)
            // integrate with system equalizer (AudioFX)
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            sendBroadcast(intent)
            // note: remember to broadcast AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, when not needed anymore
        }
    }
}
