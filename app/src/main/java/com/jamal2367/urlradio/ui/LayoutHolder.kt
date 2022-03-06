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
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.*


/*
 * LayoutHolder class
 */
data class LayoutHolder(var rootView: View) {

    /* Main class variables */
    var recyclerView: RecyclerView = rootView.findViewById(R.id.station_list)
    val layoutManager: LinearLayoutManager
    private var bottomSheet: ConstraintLayout = rootView.findViewById(R.id.bottom_sheet)

    //private var sheetMetadataViews: Group
    var sleepTimerRunningViews: Group = rootView.findViewById(R.id.sleep_timer_running_views)
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
    private var sheetNextMetadataView: ImageView = rootView.findViewById(R.id.sheet_next_metadata_button)
    private var sheetPreviousMetadataView: ImageView = rootView.findViewById(R.id.sheet_previous_metadata_button)
    var sheetSleepTimerStartButtonView: ImageView = rootView.findViewById(R.id.sleep_timer_start_button)
    var sheetSleepTimerCancelButtonView: ImageView = rootView.findViewById(R.id.sleep_timer_cancel_button)
    private var sheetSleepTimerRemainingTimeView: TextView = rootView.findViewById(R.id.sleep_timer_remaining_time)
    private var onboardingLayout: ConstraintLayout = rootView.findViewById(R.id.onboarding_layout)
    private var onboardingQuoteViews: Group = rootView.findViewById(R.id.onboarding_quote_views)
    private var onboardingImportViews: Group = rootView.findViewById(R.id.onboarding_import_views)
    private var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout> = BottomSheetBehavior.from(bottomSheet)
    private var metadataHistory: MutableList<String>
    private var metadataHistoryPosition: Int


    /* Init block */
    init {
        // find views
        //sheetMetadataViews = rootView.findViewById(R.id.sheet_metadata_views)
        metadataHistory = PreferencesHelper.loadMetadataHistory()
        metadataHistoryPosition = metadataHistory.size - 1

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
    fun updatePlayerViews(context: Context, station: Station, playbackState: Int) {

        // set default metadata views, when playback has stopped
        if (playbackState != PlaybackStateCompat.STATE_PLAYING) {
            metadataView.text = station.name
            sheetMetadataHistoryView.text = station.name
            sheetMetadataHistoryView.isSelected = true
        }

        // toggle buffering indicator
        bufferingIndicator.isVisible = playbackState == PlaybackStateCompat.STATE_BUFFERING

        // update name
        stationNameView.text = station.name

        // update cover
        if (station.imageColor != -1) {
            stationImageView.setBackgroundColor(station.imageColor)
        }
        stationImageView.setImageBitmap(ImageHelper.getStationImage(context, station.smallImage))
        stationImageView.contentDescription = "${context.getString(R.string.descr_player_station_image)}: ${station.name}"

        // update streaming link
        sheetStreamingLinkView.text = station.getStreamUri()

        // update click listeners
        sheetStreamingLinkHeadline.setOnClickListener{ copyToClipboard(context, sheetStreamingLinkView.text) }
        sheetStreamingLinkView.setOnClickListener{ copyToClipboard(context, sheetStreamingLinkView.text) }
        sheetMetadataHistoryHeadline.setOnClickListener { copyToClipboard(context, sheetMetadataHistoryView.text) }
        sheetMetadataHistoryView.setOnClickListener { copyToClipboard(context, sheetMetadataHistoryView.text) }

    }


    /* Copies given string to clipboard */
    private fun copyToClipboard(context: Context, clipString: CharSequence) {
        val clip: ClipData = ClipData.newPlainText("simple text", clipString)
        val cm: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(clip)
        Toast.makeText(context, R.string.toastmessage_copied_to_clipboard, Toast.LENGTH_LONG).show()
    }


    /* Copies collected metadata to clipboard */
    private fun copyMetadataHistoryToClipboard() {
        val metadataHistory: MutableList<String> = PreferencesHelper.loadMetadataHistory()
        val stringBuilder: StringBuilder = StringBuilder()
        metadataHistory.forEach { stringBuilder.append("${it.trim()}\n")}
        copyToClipboard(rootView.context, stringBuilder.toString())
    }


    /* Updates the metadata views */
    fun updateMetadata(metadataHistoryList: MutableList<String>) {
        if (metadataHistoryList.isNotEmpty()) {
            metadataHistory = metadataHistoryList
            if (metadataHistory.last() != metadataView.text) {
                metadataHistoryPosition = metadataHistory.size - 1
                val metadataString = metadataHistory[metadataHistoryPosition]
                metadataView.text = metadataString
                sheetMetadataHistoryView.text = metadataString
                sheetMetadataHistoryView.isSelected = true
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
                val sleepTimerTimeRemaining = DateTimeHelper.convertToMinutesAndSeconds(timeRemaining)
                sheetSleepTimerRemainingTimeView.text = sleepTimerTimeRemaining
                sheetSleepTimerRemainingTimeView.contentDescription = "${context.getString(R.string.descr_expanded_player_sleep_timer_remaining_time)}: $sleepTimerTimeRemaining"            }
        }
    }


    /* Toggles play/pause button */
    fun togglePlayButton(playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> {
                playButtonView.setImageResource(R.drawable.ic_player_stop_symbol_36dp)
            }
            else -> {
                playButtonView.setImageResource(R.drawable.ic_player_play_symbol_36dp)
            }
        }
    }


    /* Toggle the Import Running indicator  */
    fun toggleImportingStationViews() {
        if (onboardingImportViews.visibility == View.INVISIBLE) {
            onboardingImportViews.isVisible = true
            onboardingQuoteViews.isVisible = false
        } else {
            onboardingImportViews.isVisible = false
            onboardingQuoteViews.isVisible = true
        }
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
    fun animatePlaybackButtonStateTransition(context: Context, playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> {
                val rotateClockwise = AnimationUtils.loadAnimation(context, R.anim.rotate_clockwise_slow)
                rotateClockwise.setAnimationListener(createAnimationListener(playbackState))
                playButtonView.startAnimation(rotateClockwise)
            }

            else -> {
                val rotateCounterClockwise = AnimationUtils.loadAnimation(context, R.anim.rotate_counterclockwise_fast)
                rotateCounterClockwise.setAnimationListener(createAnimationListener(playbackState))
                playButtonView.startAnimation(rotateCounterClockwise)
            }

        }
    }


    /* Shows player */
    private fun showPlayer(context: Context): Boolean {
        UiHelper.setViewMargins(context, recyclerView, 0,0,0, Keys.BOTTOM_SHEET_PEEK_HEIGHT)
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN && onboardingLayout.visibility == View.GONE) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        return true
    }


    /* Hides player */
    private fun hidePlayer(context: Context): Boolean {
        UiHelper.setViewMargins(context, recyclerView, 0,0,0, 0)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        return true
    }


    fun minimizePlayerIfExpanded(): Boolean {
        return if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            true
        } else {
            false
        }
    }


    /* Creates AnimationListener for play button */
    private fun createAnimationListener(playbackState: Int): Animation.AnimationListener {
        return object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                // set up button symbol and playback indicator afterwards
                togglePlayButton(playbackState)
            }
            override fun onAnimationRepeat(animation: Animation) {}
        }
    }


    /* Sets up the player (BottomSheet) */
    private fun setupBottomSheet() {
        // show / hide the small player
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.addBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(view: View, slideOffset: Float) {
                if (slideOffset < 0.25f) {
                    // showPlayerViews()
                } else {
                    // hidePlayerViews()
                }
            }
            override fun onStateChanged(view: View, state: Int) {
                when (state) {
                    BottomSheetBehavior.STATE_COLLAPSED -> Unit // do nothing
                    BottomSheetBehavior.STATE_DRAGGING -> Unit // do nothing
                    BottomSheetBehavior.STATE_EXPANDED -> Unit // do nothing
                    BottomSheetBehavior.STATE_HALF_EXPANDED ->  Unit // do nothing
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
            BottomSheetBehavior.STATE_COLLAPSED -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            else -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }


    /*
     * Inner class: Custom LinearLayoutManager
     */
    private inner class CustomLayoutManager(context: Context): LinearLayoutManager(context, VERTICAL, false) {
        override fun supportsPredictiveItemAnimations(): Boolean {
            return true
        }
    }
    /*
     * End of inner class
     */


}
