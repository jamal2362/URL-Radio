/*
 * LayoutHolder.kt
 * Implements the LayoutHolder class
 * A LayoutHolder hold references to the main views
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.DateTimeHelper
import com.jamal2367.urlradio.helpers.ImageHelper
import com.jamal2367.urlradio.helpers.PreferencesHelper
import com.jamal2367.urlradio.helpers.UiHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


/*
 * LayoutHolder class
 */
data class LayoutHolder(var rootView: View) {

    /* Main class variables */
    var recyclerView: RecyclerView = rootView.findViewById(R.id.station_list)
    val layoutManager: LinearLayoutManager
    private var bottomSheet: ConstraintLayout = rootView.findViewById(R.id.bottom_sheet)

    //private var sheetMetadataViews: Group
    private var sleepTimerRunningViews: Group = rootView.findViewById(R.id.sleep_timer_running_views)
    private var downloadProgressIndicator: ProgressBar = rootView.findViewById(R.id.download_progress_indicator)
    private var stationImageView: ImageView = rootView.findViewById(R.id.station_icon)
    private var stationNameView: TextView = rootView.findViewById(R.id.player_station_name)
    private var metadataView: TextView = rootView.findViewById(R.id.player_station_metadata)
    var playButtonView: ImageButton = rootView.findViewById(R.id.player_play_button)
    private var bufferingIndicator: ProgressBar = rootView.findViewById(R.id.player_buffering_indicator)
    private var sheetStreamingLinkHeadline: TextView = rootView.findViewById(R.id.sheet_streaming_link_headline)
    private var sheetStreamingLinkView: TextView = rootView.findViewById(R.id.sheet_streaming_link)
    private var sheetMetadataHistoryHeadline: TextView = rootView.findViewById(R.id.sheet_metadata_headline)
    private var sheetMetadataHistoryView: TextView = rootView.findViewById(R.id.sheet_metadata_history)
    private var sheetNextMetadataView: ImageButton = rootView.findViewById(R.id.sheet_next_metadata_button)
    private var sheetPreviousMetadataView: ImageButton = rootView.findViewById(R.id.sheet_previous_metadata_button)
    private var sheetCopyMetadataButtonView: ImageButton = rootView.findViewById(R.id.copy_station_metadata_button)
    private var sheetShareLinkButtonView: ImageView = rootView.findViewById(R.id.sheet_share_link_button)
    private var sheetBitrateView: TextView = rootView.findViewById(R.id.sheet_bitrate_view)
    var sheetSleepTimerStartButtonView: ImageButton = rootView.findViewById(R.id.sleep_timer_start_button)
    var sheetSleepTimerCancelButtonView: ImageButton = rootView.findViewById(R.id.sleep_timer_cancel_button)
    private var sheetSleepTimerRemainingTimeView: TextView = rootView.findViewById(R.id.sleep_timer_remaining_time)
    private var onboardingLayout: ConstraintLayout = rootView.findViewById(R.id.onboarding_layout)
    private var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout> = BottomSheetBehavior.from(bottomSheet)
    private var metadataHistory: MutableList<String>
    private var metadataHistoryPosition: Int
    private var isBuffering: Boolean


    /* Init block */
    init {
        // find views
        //sheetMetadataViews = rootView.findViewById(R.id.sheet_metadata_views)
        metadataHistory = PreferencesHelper.loadMetadataHistory()
        metadataHistoryPosition = metadataHistory.size - 1
        isBuffering = false

        // set up RecyclerView
        layoutManager = CustomLayoutManager(rootView.context)
        recyclerView.layoutManager = layoutManager
        recyclerView.itemAnimator = DefaultItemAnimator()

        // set up metadata history next and previous buttons
        sheetPreviousMetadataView.setOnClickListener {
            if (metadataHistory.isNotEmpty()) {
                if (metadataHistoryPosition > 0) {
                    metadataHistoryPosition -= 1
                } else {
                    metadataHistoryPosition = metadataHistory.size - 1
                }
                sheetMetadataHistoryView.text = metadataHistory[metadataHistoryPosition]
            }
        }
        sheetNextMetadataView.setOnClickListener {
            if (metadataHistory.isNotEmpty()) {
                if (metadataHistoryPosition < metadataHistory.size - 1) {
                    metadataHistoryPosition += 1
                } else {
                    metadataHistoryPosition = 0
                }
                sheetMetadataHistoryView.text = metadataHistory[metadataHistoryPosition]
            }
        }
        sheetMetadataHistoryView.setOnLongClickListener {
            copyMetadataHistoryToClipboard()
            return@setOnLongClickListener true
        }
        sheetMetadataHistoryHeadline.setOnLongClickListener {
            copyMetadataHistoryToClipboard()
            return@setOnLongClickListener true
        }

        // set layout for player
        setupBottomSheet()
    }


    /* Updates the player views */
    fun updatePlayerViews(context: Context, station: Station, isPlaying: Boolean) {

        // set default metadata views, when playback has stopped
        if (!isPlaying) {
            metadataView.text = station.name
            sheetMetadataHistoryView.text = station.name
//            sheetMetadataHistoryView.isSelected = true
        }

        // update name
        stationNameView.text = station.name

        // toggle text scrolling (marquee) if necessary
        stationNameView.isSelected = isPlaying

        // reduce the shadow left and right because of scrolling (Marquee)
        stationNameView.setFadingEdgeLength(8)

        // update cover
        if (station.imageColor != -1) {
            stationImageView.setBackgroundColor(station.imageColor)
        }
        stationImageView.setImageBitmap(ImageHelper.getStationImage(context, station.smallImage))
        stationImageView.contentDescription = "${context.getString(R.string.descr_player_station_image)}: ${station.name}"

        // update streaming link
        sheetStreamingLinkView.text = station.getStreamUri()

        val bitrateText: CharSequence = if (station.codec.isNotEmpty()) {
            if (station.bitrate == 0) {
                // show only the codec when the bitrate is at "0" from radio-browser.info API
                station.codec
            } else {
                // show the bitrate and codec if the result is available in the radio-browser.info API
                buildString {
                    append(station.codec)
                    append(" | ")
                    append(station.bitrate)
                    append("kbps")
                }
            }
        } else {
            // do not show for M3U and PLS playlists as they do not include codec or bitrate
            ""
        }

        // update bitrate
        sheetBitrateView.text = bitrateText

        // update click listeners
        sheetStreamingLinkHeadline.setOnClickListener {
            copyToClipboard(
                context,
                sheetStreamingLinkView.text
            )
        }
        sheetStreamingLinkView.setOnClickListener {
            copyToClipboard(
                context,
                sheetStreamingLinkView.text
            )
        }
        sheetMetadataHistoryHeadline.setOnClickListener {
            copyToClipboard(
                context,
                sheetMetadataHistoryView.text
            )
        }
        sheetMetadataHistoryView.setOnClickListener {
            copyToClipboard(
                context,
                sheetMetadataHistoryView.text
            )
        }
        sheetCopyMetadataButtonView.setOnClickListener {
            copyToClipboard(
                context,
                sheetMetadataHistoryView.text
            )
        }
        sheetBitrateView.setOnClickListener {
            copyToClipboard(
                context,
                sheetBitrateView.text
            )
        }
        sheetShareLinkButtonView.setOnClickListener {
            val share = Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TITLE, stationNameView.text)
                putExtra(Intent.EXTRA_TEXT, sheetStreamingLinkView.text)
                type = "text/plain"
            }, null)
            context.startActivity(share)
        }
    }


    /* Copies given string to clipboard */
    private fun copyToClipboard(context: Context, clipString: CharSequence) {
        val clip: ClipData = ClipData.newPlainText("simple text", clipString)
        val cm: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(clip)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // since API 33 (TIRAMISU) the OS displays its own notification when content is copied to the clipboard
            Snackbar.make(rootView, R.string.toastmessage_copied_to_clipboard, Snackbar.LENGTH_LONG).show()
        }
    }


    /* Copies collected metadata to clipboard */
    private fun copyMetadataHistoryToClipboard() {
        val metadataHistory: MutableList<String> = PreferencesHelper.loadMetadataHistory()
        val stringBuilder: StringBuilder = StringBuilder()
        metadataHistory.forEach { stringBuilder.append("${it.trim()}\n") }
        copyToClipboard(rootView.context, stringBuilder.toString())
    }


    /* Updates the metadata views */
    fun updateMetadata(metadataHistoryList: MutableList<String>?) {
        if (!metadataHistoryList.isNullOrEmpty()) {
            metadataHistory = metadataHistoryList
            if (metadataHistory.last() != metadataView.text) {
                metadataHistoryPosition = metadataHistory.size - 1
                val metadataString = metadataHistory[metadataHistoryPosition]
                metadataView.text = metadataString
                sheetMetadataHistoryView.text = metadataString
            }
        }
    }


    /* Updates sleep timer views */
    fun updateSleepTimer(context: Context, timeRemaining: Long = 0L) {
        when (timeRemaining) {
            0L -> {
                sleepTimerRunningViews.isGone = true
            }
            else -> {
                sleepTimerRunningViews.isVisible = true
                val sleepTimerTimeRemaining = DateTimeHelper.convertToHoursMinutesSeconds(timeRemaining)
                sheetSleepTimerRemainingTimeView.text = sleepTimerTimeRemaining
                sheetSleepTimerRemainingTimeView.contentDescription = "${context.getString(R.string.descr_expanded_player_sleep_timer_remaining_time)}: $sleepTimerTimeRemaining"
                stationNameView.isSelected = false
            }
        }
    }


    /* Toggles play/pause button */
    fun togglePlayButton(isPlaying: Boolean) {
        if (isPlaying) {
            playButtonView.setImageResource(R.drawable.ic_audio_waves_animated)
            val animatedVectorDrawable = playButtonView.drawable as? AnimatedVectorDrawable
            animatedVectorDrawable?.start()
            sheetSleepTimerStartButtonView.isVisible = true
            // bufferingIndicator.isVisible = false
        } else {
            playButtonView.setImageResource(R.drawable.ic_player_play_symbol_42dp)
            sheetSleepTimerStartButtonView.isVisible = false
            // bufferingIndicator.isVisible = isBuffering
        }
    }


    /* Toggles buffering indicator */
    fun showBufferingIndicator(buffering: Boolean) {
        bufferingIndicator.isVisible = buffering
        isBuffering = buffering
    }


    /* Toggles visibility of player depending on playback state - hiding it when playback is stopped (not paused or playing) */
//    fun togglePlayerVisibility(context: Context, playbackState: Int): Boolean {
//        when (playbackState) {
//            PlaybackStateCompat.STATE_STOPPED -> return hidePlayer(context)
//            PlaybackStateCompat.STATE_NONE -> return hidePlayer(context)
//            PlaybackStateCompat.STATE_ERROR -> return hidePlayer(context)
//            else -> return showPlayer(context)
//        }
//    }


    /* Toggles visibility of the download progress indicator */
    fun toggleDownloadProgressIndicator() {
        when (PreferencesHelper.loadActiveDownloads()) {
            Keys.ACTIVE_DOWNLOADS_EMPTY -> downloadProgressIndicator.isGone = true
            else -> downloadProgressIndicator.isVisible = true
        }
    }


    /* Toggles visibility of the onboarding screen */
    fun toggleOnboarding(context: Context, collectionSize: Int): Boolean {
        return if (collectionSize == 0 && PreferencesHelper.loadCollectionSize() <= 0) {
            onboardingLayout.isVisible = true
            hidePlayer(context)
            true
        } else {
            onboardingLayout.isGone = true
            showPlayer(context)
            false
        }
    }


    /* Initiates the rotation animation of the play button  */
    fun animatePlaybackButtonStateTransition(context: Context, isPlaying: Boolean) {
        when (isPlaying) {
            true -> {
                val rotateClockwise = AnimationUtils.loadAnimation(context, R.anim.rotate_clockwise_slow)
                rotateClockwise.setAnimationListener(createAnimationListener(true))
                playButtonView.startAnimation(rotateClockwise)
            }
            false -> {
                val rotateCounterClockwise = AnimationUtils.loadAnimation(context, R.anim.rotate_counterclockwise_fast)
                rotateCounterClockwise.setAnimationListener(createAnimationListener(false))
                playButtonView.startAnimation(rotateCounterClockwise)
            }

        }
    }


    /* Shows player */
    fun showPlayer(context: Context): Boolean {
        UiHelper.setViewMargins(context, recyclerView, 0, 0, 0, Keys.BOTTOM_SHEET_PEEK_HEIGHT)
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN && onboardingLayout.visibility == View.GONE) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        return true
    }


    /* Hides player */
    private fun hidePlayer(context: Context): Boolean {
        UiHelper.setViewMargins(context, recyclerView, 0, 0, 0, 0)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        return true
    }


    /* Minimizes player sheet if expanded */
    fun minimizePlayerIfExpanded(): Boolean {
        return if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            true
        } else {
            false
        }
    }


    /* Creates AnimationListener for play button */
    private fun createAnimationListener(isPlaying: Boolean): Animation.AnimationListener {
        return object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                // set up button symbol and playback indicator afterwards
                togglePlayButton(isPlaying)
            }

            override fun onAnimationRepeat(animation: Animation) {}
        }
    }


    /* Sets up the player (BottomSheet) */
    private fun setupBottomSheet() {
        // show / hide the small player
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(view: View, slideOffset: Float) {
            }

            override fun onStateChanged(view: View, state: Int) {
                when (state) {
                    BottomSheetBehavior.STATE_COLLAPSED -> Unit // do nothing
                    BottomSheetBehavior.STATE_DRAGGING -> Unit // do nothing
                    BottomSheetBehavior.STATE_EXPANDED -> Unit // do nothing
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> Unit // do nothing
                    BottomSheetBehavior.STATE_SETTLING -> Unit // do nothing
                    BottomSheetBehavior.STATE_HIDDEN -> showPlayer(rootView.context)
                }
            }
        })
        // toggle collapsed state on tap
        bottomSheet.setOnClickListener { toggleBottomSheetState() }
        stationImageView.setOnClickListener { toggleBottomSheetState() }
        stationNameView.setOnClickListener { toggleBottomSheetState() }
        metadataView.setOnClickListener { toggleBottomSheetState() }
    }


    /* Toggle expanded/collapsed state of bottom sheet */
    private fun toggleBottomSheetState() {
        when (bottomSheetBehavior.state) {
            BottomSheetBehavior.STATE_COLLAPSED -> bottomSheetBehavior.state =
                BottomSheetBehavior.STATE_EXPANDED
            else -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }


    /*
     * Inner class: Custom LinearLayoutManager
     */
    private inner class CustomLayoutManager(context: Context) :
        LinearLayoutManager(context, VERTICAL, false) {
        override fun supportsPredictiveItemAnimations(): Boolean {
            return true
        }
    }
    /*
     * End of inner class
     */


}
