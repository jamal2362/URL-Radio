/*
 * ImportHelper.kt
 * Implements the ImportHelper object
 * A ImportHelper provides methods for integrating station files from URLRadio v3
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.helpers

import android.content.Context
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.core.Collection


/*
 * ImportHelper object
 */
object ImportHelper {


    /* */
    fun removeDefaultStationImageUris(context: Context) {
        val collection: Collection = FileHelper.readCollection(context)
        collection.stations.forEach { station ->
            if (station.image == Keys.LOCATION_DEFAULT_STATION_IMAGE) {
                station.image = String()
            }
            if (station.smallImage == Keys.LOCATION_DEFAULT_STATION_IMAGE) {
                station.smallImage = String()
            }
        }
        CollectionHelper.saveCollection(context, collection, async = false)
    }

}
