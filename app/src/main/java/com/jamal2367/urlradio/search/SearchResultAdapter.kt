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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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

    /* ExoPlayer */
    private var exoPlayer: ExoPlayer? = null

    /* Listener Interface */
    interface SearchResultAdapterListener {
        fun onSearchResultTapped(result: Station)
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
            // Get the selected station from searchResults
            val selectedStation = searchResults[holder.adapterPosition]
            // Perform pre-playback here
            performPrePlayback(searchResultViewHolder.searchResultLayout.context, selectedStation.getStreamUri())
            // hand over station
            listener.onSearchResultTapped(searchResult)
        }
    }


    private fun performPrePlayback(context: Context, streamUri: String) {
        if (streamUri.contains(".m3u8")) {
            // Release previous player if it exists
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null

            // show toast when no playback is possible
            Toast.makeText(context, R.string.toastmessage_preview_playback_failed, Toast.LENGTH_SHORT).show()
        } else {
            exoPlayer?.release()
            exoPlayer = null

            // Create a new instance of ExoPlayer
            exoPlayer = ExoPlayer.Builder(context).build()

            // Create a MediaItem with the streamUri
            val mediaItem = MediaItem.fromUri(streamUri)

            // Set the MediaItem to the ExoPlayer
            exoPlayer?.setMediaItem(mediaItem)

            // Prepare and start the ExoPlayer
            exoPlayer?.prepare()
            exoPlayer?.play()

            // show toast when playback is possible
            Toast.makeText(context, R.string.toastmessage_preview_playback_started, Toast.LENGTH_SHORT).show()
        }
    }


    fun stopPrePlayback() {
        // Stop the ExoPlayer and release resources
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
    }


    /* Resets the selected position */
    fun resetSelection(clearAdapter: Boolean) {
        val currentlySelected: Int = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (clearAdapter) {
            searchResults = listOf()
            notifyDataSetChanged()
        } else {
            notifyItemChanged(currentlySelected)
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
