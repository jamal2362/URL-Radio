/*
 * SearchResultAdapter.kt
 * Implements the SearchResultAdapter class
 * A SearchResultAdapter is a custom adapter providing search result views for a RecyclerView
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.search

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station


/*
 * SearchResultAdapter class
 */
class SearchResultAdapter(
    private val listener: SearchResultAdapterListener,
    var searchResults: List<Station>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /* Main class variables */
    private var selectedPosition: Int = RecyclerView.NO_POSITION
    private var exoPlayer: ExoPlayer? = null
    private var paused: Boolean = false
    private var isItemSelected: Boolean = false

    /* Listener Interface */
    interface SearchResultAdapterListener {
        fun onSearchResultTapped(result: Station)
        fun activateAddButton()
        fun deactivateAddButton()
    }


    init {
        setHasStableIds(true)
    }


    /* Overrides onCreateViewHolder from RecyclerView.Adapter */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.element_search_result, parent, false)
        return SearchResultViewHolder(v)
    }


    /* Overrides getItemCount from RecyclerView.Adapter */
    override fun getItemCount(): Int {
        return searchResults.size
    }


    /* Overrides getItemCount from RecyclerView.Adapter */
    override fun getItemId(position: Int): Long = position.toLong()


    /* Overrides onBindViewHolder from RecyclerView.Adapter */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // get reference to ViewHolder
        val searchResultViewHolder: SearchResultViewHolder = holder as SearchResultViewHolder
        val searchResult: Station = searchResults[position]

        // update text
        searchResultViewHolder.nameView.text = searchResult.name
        searchResultViewHolder.streamView.text = searchResult.getStreamUri()

        if (searchResult.codec.isNotEmpty()) {
            if (searchResult.bitrate == 0) {
                // show only the codec when the bitrate is at "0" from radio-browser.info API
                searchResultViewHolder.bitrateView.text = searchResult.codec
            } else {
                // show the bitrate and codec if the result is available in the radio-browser.info API
                searchResultViewHolder.bitrateView.text = buildString {
                    append(searchResult.codec)
                    append(" | ")
                    append(searchResult.bitrate)
                    append("kbps")}
            }
        } else {
            // do not show for M3U and PLS playlists as they do not include codec or bitrate
            searchResultViewHolder.bitrateView.visibility = View.GONE
        }

        // mark selected if necessary
        val isSelected = selectedPosition == holder.adapterPosition
        searchResultViewHolder.searchResultLayout.isSelected = isSelected

        // toggle text scrolling (marquee) if necessary
        searchResultViewHolder.nameView.isSelected = isSelected
        searchResultViewHolder.streamView.isSelected = isSelected

        // reduce the shadow left and right because of scrolling (Marquee)
        searchResultViewHolder.nameView.setFadingEdgeLength(10)
        searchResultViewHolder.streamView.setFadingEdgeLength(10)

        // attach touch listener
        searchResultViewHolder.searchResultLayout.setOnClickListener {
            // move marked position
            val previousSelectedPosition = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(previousSelectedPosition)
            notifyItemChanged(selectedPosition)

            // check if the selected position is the same as before
            val samePositionSelected = previousSelectedPosition == selectedPosition

            if (samePositionSelected) {
                // if the same position is selected again, reset the selection
                resetSelection(false)
            } else {
                // get the selected station from searchResults
                val selectedStation = searchResults[holder.adapterPosition]
                // perform pre-playback here
                performPrePlayback(searchResultViewHolder.searchResultLayout.context, selectedStation.getStreamUri())
                // hand over station
                listener.onSearchResultTapped(searchResult)
            }

            // update isItemSelected based on the selection
            isItemSelected = !samePositionSelected

            // enable/disable the Add button based on isItemSelected
            if (isItemSelected) {
                listener.activateAddButton()
            } else {
                listener.deactivateAddButton()
            }
        }
    }


    private fun performPrePlayback(context: Context, streamUri: String) {
        if (streamUri.contains(".m3u8")) {
            // release previous player if it exists
            stopPrePlayback()

            // show toast when no playback is possible
            Toast.makeText(context, R.string.toastmessage_preview_playback_failed, Toast.LENGTH_SHORT).show()
        } else {
            stopRadioPlayback(context)

            // release previous player if it exists
            stopPrePlayback()

            // create a new instance of ExoPlayer
            exoPlayer = ExoPlayer.Builder(context).build()

            // create a MediaItem with the streamUri
            val mediaItem = MediaItem.fromUri(streamUri)

            // set the MediaItem to the ExoPlayer
            exoPlayer?.setMediaItem(mediaItem)

            // prepare and start the ExoPlayer
            exoPlayer?.prepare()
            exoPlayer?.play()

            // show toast when playback is possible
            Toast.makeText(context, R.string.toastmessage_preview_playback_started, Toast.LENGTH_SHORT).show()

            // listen for app pause events
            val lifecycle = (context as AppCompatActivity).lifecycle
            val lifecycleObserver = object : DefaultLifecycleObserver {
                override fun onPause(owner: LifecycleOwner) {
                    if (!paused) {
                        paused = true
                        stopPrePlayback()
                    }
                }
            }
            lifecycle.addObserver(lifecycleObserver)
        }
    }


    fun stopPrePlayback() {
        // stop the ExoPlayer and release resources
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
    }


    private fun stopRadioPlayback(context: Context) {
        // stop radio playback when one is active
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes)
                .build()

            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            // For older versions where AudioFocusRequest is not available
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }


    /* Resets the selected position */
    fun resetSelection(clearAdapter: Boolean) {
        val currentlySelected: Int = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (clearAdapter) {
            val previousItemCount = itemCount
            searchResults = emptyList()
            notifyItemRangeRemoved(0, previousItemCount)
        } else {
            notifyItemChanged(currentlySelected)
            stopPrePlayback()
        }
    }


    /*
     * Inner class: ViewHolder for a radio station search result
     */
    private inner class SearchResultViewHolder(var searchResultLayout: View) :
        RecyclerView.ViewHolder(searchResultLayout) {
        val nameView: MaterialTextView = searchResultLayout.findViewById(R.id.station_name)
        val streamView: MaterialTextView = searchResultLayout.findViewById(R.id.station_url)
        val bitrateView: MaterialTextView = searchResultLayout.findViewById(R.id.station_bitrate)
    }

}
