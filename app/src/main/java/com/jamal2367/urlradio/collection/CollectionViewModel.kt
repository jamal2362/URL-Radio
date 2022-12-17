/*
 * CollectionViewModel.kt
 * Implements the CollectionViewModel class
 * A CollectionViewModel stores the collection of stations as live data
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.collection

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.launch
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.core.Collection
import com.jamal2367.urlradio.helpers.FileHelper
import java.util.*


/*
 * CollectionViewModel.class
 */
class CollectionViewModel(application: Application) : AndroidViewModel(application) {

    /* Define log tag */
    private val TAG: String = CollectionViewModel::class.java.simpleName

    /* Main class variables */
    val collectionLiveData: MutableLiveData<Collection> = MutableLiveData<Collection>()
    val collectionSizeLiveData: MutableLiveData<Int> = MutableLiveData<Int>()
    private var modificationDateViewModel: Date = Date()
    private var collectionChangedReceiver: BroadcastReceiver


    /* Init constructor */
    init {
        // load collection
        loadCollection()
        // create and register collection changed receiver
        collectionChangedReceiver = createCollectionChangedReceiver()
        LocalBroadcastManager.getInstance(application).registerReceiver(collectionChangedReceiver, IntentFilter(Keys.ACTION_COLLECTION_CHANGED))
    }


    /* Overrides onCleared */
    override fun onCleared() {
        super.onCleared()
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(collectionChangedReceiver)
    }


    /* Creates the collectionChangedReceiver - handles Keys.ACTION_COLLECTION_CHANGED */
    private fun createCollectionChangedReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.hasExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE)) {
                    val date = Date(intent.getLongExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE, 0L))
                    // check if reload is necessary
                    if (date.after(modificationDateViewModel)) {
                        Log.v(TAG, "CollectionViewModel - reload collection after broadcast received.")
                        loadCollection()
                    }
                }
            }
        }
    }


    /* Reads collection of radio stations from storage using GSON */
    private fun loadCollection() {
        Log.v(TAG, "Loading collection of stations from storage")
        viewModelScope.launch {
            // load collection on background thread
            val collection: Collection = FileHelper.readCollectionSuspended(getApplication())
            // get updated modification date
            modificationDateViewModel = collection.modificationDate
            // update collection view model
            collectionLiveData.value = collection
            // update collection sie
            collectionSizeLiveData.value = collection.stations.size
        }
    }

}
