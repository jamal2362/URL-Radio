/*
 * FindStationDialog.kt
 * Implements the FindStationDialog class
 * A FindStationDialog shows a dialog with search box and list of results
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.dialogs

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.search.RadioBrowserResult
import com.jamal2367.urlradio.search.RadioBrowserResultAdapter
import com.jamal2367.urlradio.search.RadioBrowserSearch


/*
 * FindStationDialog class
 */
class FindStationDialog (private var context: Context, private var listener: FindFindStationDialogListener): RadioBrowserResultAdapter.RadioBrowserResultAdapterListener, RadioBrowserSearch.RadioBrowserSearchListener {

    /* Interface used to communicate back to activity */
    interface FindFindStationDialogListener {
        fun onFindStationDialog(remoteStationLocation: String, station: Station) {
        }
    }


    /* Main class variables */
    private lateinit var dialog: AlertDialog
    private lateinit var stationSearchBoxView: SearchView
    private lateinit var searchRequestProgressIndicator: ProgressBar
    private lateinit var noSearchResultsTextView: MaterialTextView
    private lateinit var stationSearchResultList: RecyclerView
    private lateinit var searchResultAdapter: RadioBrowserResultAdapter
    private lateinit var radioBrowserSearch: RadioBrowserSearch
    private var currentSearchString: String = String()
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var remoteStationLocation: String = String()
    private var station: Station = Station()


    /* Overrides onSearchResultTapped from RadioBrowserResultAdapterListener */
    override fun onSearchResultTapped(radioBrowserResult: RadioBrowserResult) {
        station = radioBrowserResult.toStation()
        // hide keyboard
        val imm: InputMethodManager = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(stationSearchBoxView.windowToken, 0)
        // make add button clickable
        activateAddButton()
    }


    /* Overrides onRadioBrowserSearchResults from RadioBrowserSearchListener */
    @SuppressLint("NotifyDataSetChanged")
    override fun onRadioBrowserSearchResults(results: Array<RadioBrowserResult>) {
        if (results.isNotEmpty()) {
            searchResultAdapter.searchResults = results
            searchResultAdapter.notifyDataSetChanged()
            resetLayout(clearAdapter = false)
        } else {
            showNoResultsError()
        }
    }


    /* Construct and show dialog */
    fun show() {
        // initialize a radio browser search
        radioBrowserSearch = RadioBrowserSearch(this)

        // prepare dialog builder
        val builder = MaterialAlertDialogBuilder(context)

        // set title
        builder.setTitle(R.string.dialog_find_station_title)

        // get views
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_find_station, null)
        stationSearchBoxView = view.findViewById(R.id.station_search_box_view)
        searchRequestProgressIndicator = view.findViewById(R.id.search_request_progress_indicator)
        stationSearchResultList = view.findViewById(R.id.station_search_result_list)
        noSearchResultsTextView = view.findViewById(R.id.no_results_text_view)
        noSearchResultsTextView.isGone = true

        // set up list of search results
        setupRecyclerView(context)

        // add okay ("import") button
        builder.setPositiveButton(R.string.dialog_find_station_button_add) { _, _ ->
            // listen for click on add button
            (listener).onFindStationDialog(remoteStationLocation, station)
        }
        // add cancel button
        builder.setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
            // listen for click on cancel button
            radioBrowserSearch.stopSearchRequest()
        }
        // handle outside-click as "no"
        builder.setOnCancelListener {
            radioBrowserSearch.stopSearchRequest()
        }

        // listen for input
        stationSearchBoxView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String): Boolean {
                handleSearchBoxLiveInput(context, query)
                return true
            }
            override fun onQueryTextSubmit(query: String): Boolean {
                handleSearchBoxInput(context, query)
                return true
            }
        })

        // set dialog view
        builder.setView(view)

        // create and display dialog
        dialog = builder.create()
        dialog.show()

        // initially disable "Add" button
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isAllCaps = true
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isAllCaps = true

    }


    /* Sets up list of results (RecyclerView) */
    private fun setupRecyclerView(context: Context) {
        searchResultAdapter = RadioBrowserResultAdapter(this, arrayOf())
        stationSearchResultList.adapter = searchResultAdapter
        val layoutManager: LinearLayoutManager = object: LinearLayoutManager(context) {
            override fun supportsPredictiveItemAnimations(): Boolean {
                return true
            }
        }
        stationSearchResultList.layoutManager = layoutManager
        stationSearchResultList.itemAnimator = DefaultItemAnimator()
    }


    /* Handles user input into search box - user has to submit the search */
    private fun handleSearchBoxInput(context: Context, query: String) {
        when {
            // handle empty search box input
            query.isEmpty() -> {
                resetLayout(clearAdapter = true)
            }
            // handle direct URL input
            query.startsWith("http") -> {
                remoteStationLocation = query
                activateAddButton()
            }
            // handle search string input
            else -> {
                showProgressIndicator()
                radioBrowserSearch.searchStation(context, query, Keys.SEARCH_TYPE_BY_KEYWORD)
            }
        }
    }


    /* Handles live user input into search box */
    private fun handleSearchBoxLiveInput(context: Context, query: String) {
        currentSearchString = query
        if (query.startsWith("htt")) {
            // handle direct URL input
            remoteStationLocation = query
            activateAddButton()
        } else if (query.contains(" ") || query.length > 1 ) {
            // show progress indicator
            showProgressIndicator()
            // handle search string input - delay request to manage server load (not sure if necessary)
            handler.postDelayed({
                // only start search if query is the same as one second ago
                if (currentSearchString == query) radioBrowserSearch.searchStation(context, query, Keys.SEARCH_TYPE_BY_KEYWORD)
            }, 100)
        } else if (query.isEmpty()) {
            resetLayout(clearAdapter = true)
        }
    }


    /* Makes the "Add" button clickable */
    private fun activateAddButton() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
        searchRequestProgressIndicator.isGone = true
        noSearchResultsTextView.isGone = true
    }


    /* Resets the dialog layout to default state */
    private fun resetLayout(clearAdapter: Boolean = false) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        searchRequestProgressIndicator.isGone = true
        noSearchResultsTextView.isGone = true
        searchResultAdapter.resetSelection(clearAdapter)
    }


    /* Display the "No Results" error - hide other unneeded views */
    private fun showNoResultsError() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        searchRequestProgressIndicator.isGone = true
        noSearchResultsTextView.isVisible = true
    }


    /* Display the "No Results" error - hide other unneeded views */
    private fun showProgressIndicator() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        searchRequestProgressIndicator.isVisible = true
        noSearchResultsTextView.isGone = true
    }

}
