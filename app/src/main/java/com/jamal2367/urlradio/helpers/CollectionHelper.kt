/*
 * CollectionHelper.kt
 * Implements the CollectionHelper object
 * A CollectionHelper provides helper methods for the collection of stations
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
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.gson.GsonBuilder
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Collection
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.search.RadioBrowserResult
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import java.io.File
import java.net.URL
import java.util.*


/*
 * CollectionHelper object
 */
object CollectionHelper {

    /* Define log tag */
    private val TAG: String = CollectionHelper::class.java.simpleName

    /* Checks if station is already in collection */
    private fun isNewStation(collection: Collection, station: Station): Boolean {
        collection.stations.forEach {
            if (it.getStreamUri() == station.getStreamUri()) return false
        }
        return true
    }


    /* Checks if station is already in collection */
    fun isNewStation(collection: Collection, remoteStationLocation: String): Boolean {
        collection.stations.forEach {
            if (it.remoteStationLocation == remoteStationLocation) return false
        }
        return true
    }


    /* Checks if enough time passed since last update */
    fun hasEnoughTimePassedSinceLastUpdate(): Boolean {
        val lastUpdate: Date = PreferencesHelper.loadLastUpdateCollection()
        val currentDate: Date = Calendar.getInstance().time
        return currentDate.time - lastUpdate.time > Keys.MINIMUM_TIME_BETWEEN_UPDATES
    }


    /* Checks if a newer collection of radio stations is available on storage */
    fun isNewerCollectionAvailable(date: Date): Boolean {
        var newerCollectionAvailable = false
        val modificationDate: Date = PreferencesHelper.loadCollectionModificationDate()
        if (modificationDate.after(date) || date == Keys.DEFAULT_DATE) {
            newerCollectionAvailable = true
        }
        return newerCollectionAvailable
    }


    /* Creates station from previously downloaded playlist file */
    fun createStationFromPlaylistFile(
        context: Context,
        localFileUri: Uri,
        remoteFileLocation: String
    ): Station {
        // read station playlist
        val station: Station =
            FileHelper.readStationPlaylist(context.contentResolver.openInputStream(localFileUri))
        if (station.name.isEmpty()) {
            // construct name from file name - strips file extension
            station.name = FileHelper.getFileName(context, localFileUri).substringBeforeLast(".")
        }
        station.remoteStationLocation = remoteFileLocation
        station.remoteImageLocation = getFaviconAddress(remoteFileLocation)
        station.modificationDate = GregorianCalendar.getInstance().time
        return station
    }


    /* Updates radio station in collection */
    fun updateStation(context: Context, collection: Collection, station: Station): Collection {
        var updatedCollection: Collection = collection

        // CASE: Update station retrieved from radio browser
        if (station.radioBrowserStationUuid.isNotEmpty()) {
            updatedCollection.stations.forEach {
                if (it.radioBrowserStationUuid == station.radioBrowserStationUuid) {
                    // update station in collection with values from new station
                    it.streamUris[it.stream] = station.getStreamUri()
                    it.streamContent = station.streamContent
                    it.remoteImageLocation = station.remoteImageLocation
                    it.remoteStationLocation = station.remoteStationLocation
                    it.homepage = station.homepage
                    // update name - if not changed previously by user
                    if (!it.nameManuallySet) it.name = station.name
                    // re-download station image - if new URL and not changed previously by user
                    if (!it.imageManuallySet && it.remoteImageLocation != station.remoteImageLocation) DownloadHelper.updateStationImage(
                        context,
                        it
                    )
                }
            }
            // sort and save collection
            updatedCollection = sortCollection(updatedCollection)
            saveCollection(context, updatedCollection, false)
        }

        // CASE: Update station retrieved via playlist
        else if (station.remoteStationLocation.isNotEmpty()) {
            updatedCollection.stations.forEach {
                if (it.remoteStationLocation == station.remoteStationLocation) {
                    // update stream uri, mime type and station image url
                    it.streamUris[it.stream] = station.getStreamUri()
                    it.streamContent = station.streamContent
                    it.remoteImageLocation = station.remoteImageLocation
                    // update name - if not changed previously by user
                    if (!it.nameManuallySet) it.name = station.name
                    // re-download station image - if not changed previously by user
                    if (!it.imageManuallySet) DownloadHelper.updateStationImage(context, it)
                }
            }
            // sort and save collection
            updatedCollection = sortCollection(updatedCollection)
            saveCollection(context, updatedCollection, false)
        }

        return updatedCollection
    }


    /* Adds new radio station to collection */
    fun addStation(context: Context, collection: Collection, newStation: Station): Collection {
        // check validity
        if (!newStation.isValid()) {
            Toast.makeText(context, R.string.toastmessage_station_not_valid, Toast.LENGTH_LONG)
                .show()
            return collection
        }
        // duplicate check
        else if (!isNewStation(collection, newStation)) {
            // update station
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.toastmessage_station_duplicate, Toast.LENGTH_LONG)
                    .show()
            }
            return collection
        }
        // all clear -> add station
        else {
            var updatedCollection: Collection = collection
            val updatedStationList: MutableList<Station> = collection.stations.toMutableList()
            // add station
            updatedStationList.add(newStation)
            updatedCollection.stations = updatedStationList
            // sort and save collection
            updatedCollection = sortCollection(updatedCollection)
            saveCollection(context, updatedCollection, false)
            // download station image
            DownloadHelper.updateStationImage(context, newStation)
            // return updated collection
            return updatedCollection
        }
    }


    /* Sets station image - determines station by remote image file location */
    fun setStationImageWithRemoteLocation(
        context: Context,
        collection: Collection,
        tempImageFileUri: String,
        remoteFileLocation: String,
        imageManuallySet: Boolean = false
    ): Collection {
        collection.stations.forEach { station ->
            // compare image location protocol-agnostic (= without http / https)
            if (station.remoteImageLocation.substringAfter(":") == remoteFileLocation.substringAfter(
                    ":"
                )
            ) {
                station.smallImage = FileHelper.saveStationImage(
                    context,
                    station.uuid,
                    tempImageFileUri.toUri(),
                    Keys.SIZE_STATION_IMAGE_CARD,
                    Keys.STATION_IMAGE_FILE
                ).toString()
                station.image = FileHelper.saveStationImage(
                    context,
                    station.uuid,
                    tempImageFileUri.toUri(),
                    Keys.SIZE_STATION_IMAGE_MAXIMUM,
                    Keys.STATION_IMAGE_FILE
                ).toString()
                station.imageColor = ImageHelper.getMainColor(context, tempImageFileUri.toUri())
                station.imageManuallySet = imageManuallySet
            }
        }
        // save and return collection
        saveCollection(context, collection)
        return collection
    }


    /* Sets station image - determines station by remote image file location */
    fun setStationImageWithStationUuid(
        context: Context,
        collection: Collection,
        tempImageFileUri: Uri,
        stationUuid: String,
        imageManuallySet: Boolean = false
    ): Collection {
        collection.stations.forEach { station ->
            // find station by uuid
            if (station.uuid == stationUuid) {
                station.smallImage = FileHelper.saveStationImage(
                    context,
                    station.uuid,
                    tempImageFileUri,
                    Keys.SIZE_STATION_IMAGE_CARD,
                    Keys.STATION_IMAGE_FILE
                ).toString()
                station.image = FileHelper.saveStationImage(
                    context,
                    station.uuid,
                    tempImageFileUri,
                    Keys.SIZE_STATION_IMAGE_MAXIMUM,
                    Keys.STATION_IMAGE_FILE
                ).toString()
                station.imageColor = ImageHelper.getMainColor(context, tempImageFileUri)
                station.imageManuallySet = imageManuallySet
            }
        }
        // save and return collection
        saveCollection(context, collection)
        return collection
    }


    /* Clears an image folder for a given station */
    fun clearImagesFolder(context: Context, station: Station) {
        // clear image folder
        val imagesFolder = File(
            context.getExternalFilesDir(""),
            FileHelper.determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, station.uuid)
        )
        FileHelper.clearFolder(imagesFolder, 0)
    }


    /* Deletes Images of a given station */
    fun deleteStationImages(context: Context, station: Station) {
        val imagesFolder = File(
            context.getExternalFilesDir(""),
            FileHelper.determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, station.uuid)
        )
        FileHelper.clearFolder(imagesFolder, 0, true)
    }


    /* Get station from collection for given UUID */
    fun getStation(collection: Collection, stationUuid: String): Station {
        collection.stations.forEach { station ->
            if (station.uuid == stationUuid) {
                return station
            }
        }
        // fallback: return first station
        return if (collection.stations.isNotEmpty()) {
            collection.stations.first()
        } else {
            Station()
        }
    }


    /* Get station from collection for given Stream Uri */
    fun getStationWithStreamUri(collection: Collection, streamUri: String): Station {
        collection.stations.forEach { station ->
            if (station.getStreamUri() == streamUri) {
                return station
            }
        }
        // fallback: return first station
        return if (collection.stations.isNotEmpty()) {
            collection.stations.first()
        } else {
            Station()
        }
    }


    /* Gets MediaIem for next station within collection */
    fun getNextMediaItem(context: Context, collection: Collection, stationUuid: String): MediaItem {
        val currentStationPosition: Int = getStationPosition(collection, stationUuid)
        return if (collection.stations.isEmpty() || currentStationPosition == -1) {
            buildMediaItem(context, Station())
        } else if (currentStationPosition < collection.stations.size -1) {
            buildMediaItem(context, collection.stations[currentStationPosition + 1])
        } else {
            buildMediaItem(context, collection.stations.first())
        }
    }


    /* Gets MediaIem for previous station within collection */
    fun getPreviousMediaItem(context: Context, collection: Collection, stationUuid: String): MediaItem {
        val currentStationPosition: Int = getStationPosition(collection, stationUuid)
        return if (collection.stations.isEmpty() || currentStationPosition == -1) {
            buildMediaItem(context, Station())
        } else if (currentStationPosition > 0) {
            buildMediaItem(context, collection.stations[currentStationPosition - 1])
        } else {
            buildMediaItem(context, collection.stations.last())
        }
    }


    /* Get the position from collection for given UUID */
    fun getStationPosition(collection: Collection, stationUuid: String): Int {
        collection.stations.forEachIndexed { stationId, station ->
            if (station.uuid == stationUuid) {
                return stationId
            }
        }
        return -1
    }


    /* Get the position from collection for given radioBrowserStationUuid */
    fun getStationPositionFromRadioBrowserStationUuid(
        collection: Collection,
        radioBrowserStationUuid: String
    ): Int {
        collection.stations.forEachIndexed { stationId, station ->
            if (station.radioBrowserStationUuid == radioBrowserStationUuid) {
                return stationId
            }
        }
        return -1
    }


    /* Get name of station from collection for given UUID */
    fun getStationName(collection: Collection, stationUuid: String): String {
        collection.stations.forEach { station ->
            if (station.uuid == stationUuid) {
                return station.name
            }
        }
        return String()
    }


    /* Returns the children stations under under root (simple media library structure: root > stations) */
    fun getChildren(context: Context, collection: Collection): List<MediaItem> {
        val mediaItems: MutableList<MediaItem> = mutableListOf()
        collection.stations.forEach { station ->
            mediaItems.add(buildMediaItem(context, station))
        }
        return mediaItems
    }


    /* Returns media item for given station id */
    fun getItem(context: Context, collection: Collection, stationUuid: String): MediaItem {
        return buildMediaItem(context, getStation(collection, stationUuid))
    }


    /* Returns media item for last played station */
    fun getRecent(context: Context, collection: Collection): MediaItem {
        return buildMediaItem(context, getStation(collection, PreferencesHelper.loadLastPlayedStationUuid()))
    }


    /* Returns the library root item */
    fun getRootItem(): MediaItem {
        val metadata: MediaMetadata = MediaMetadata.Builder()
            .setTitle("Root Folder")
            .setIsPlayable(false)
            .setIsBrowsable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()
        return MediaItem.Builder()
            .setMediaId("[rootID]")
            .setMediaMetadata(metadata)
            .build()
    }


    /* Saves the playback state of a given station */
    fun savePlaybackState(
        context: Context,
        collection: Collection,
        stationUuid: String,
        isPlaying: Boolean
    ): Collection {
        collection.stations.forEach {
            // reset playback state everywhere
            it.isPlaying = false
            // set given playback state at this station
            if (it.uuid == stationUuid) {
                it.isPlaying = isPlaying
            }
        }
        // save collection and store modification date
        collection.modificationDate = saveCollection(context, collection)
        return collection
    }


    /* Saves collection of radio stations */
    fun saveCollection(context: Context, collection: Collection, async: Boolean = true): Date {
        Log.v(
            TAG,
            "Saving collection of radio stations to storage. Async = ${async}. Size = ${collection.stations.size}"
        )
        // get modification date
        val date: Date = Calendar.getInstance().time
        collection.modificationDate = date
        // save collection to storage
        when (async) {
            true -> {
                CoroutineScope(IO).launch {
                    // save collection on background thread
                    FileHelper.saveCollectionSuspended(context, collection, date)
                    // broadcast collection update
                    sendCollectionBroadcast(context, date)
                }
            }
            false -> {
                // save collection
                FileHelper.saveCollection(context, collection, date)
                // broadcast collection update
                sendCollectionBroadcast(context, date)
            }
        }
        // return modification date
        return date
    }


    /* Export collection of stations as M3U */
    fun exportCollectionM3u(context: Context, collection: Collection) {
        Log.v(TAG, "Exporting collection of stations as M3U")
        // export collection as M3U - launch = fire & forget (no return value from save collection)
        if (collection.stations.size > 0) {
            CoroutineScope(IO).launch {
                FileHelper.backupCollectionAsM3uSuspended(
                    context,
                    collection
                )
            }
        }
    }


    /* Create M3U string from collection of stations */
    fun createM3uString(collection: Collection): String {
        val m3uString = StringBuilder()
        /* Extended M3U Format
        #EXTM3U
        #EXTINF:-1,My Cool Stream
        http://www.site.com:8000/listen.pls
         */

        // add opening tag
        m3uString.append("#EXTM3U")
        m3uString.append("\n")

        // add name and stream address
        collection.stations.forEach { station ->
            m3uString.append("\n")
            m3uString.append("#EXTINF:-1,")
            m3uString.append(station.name)
            m3uString.append("\n")
            m3uString.append(station.getStreamUri())
            m3uString.append("\n")
        }

        return m3uString.toString()
    }


    /* Export collection of stations as PLS */
    fun exportCollectionPls(context: Context, collection: Collection) {
        Log.v(TAG, "Exporting collection of stations as PLS")
        // export collection as PLS - launch = fire & forget (no return value from save collection)
        if (collection.stations.size > 0) {
            CoroutineScope(IO).launch {
                FileHelper.backupCollectionAsPlsSuspended(
                    context,
                    collection
                )
            }
        }
    }


    /* Create PLS string from collection of stations */
    fun createPlsString(collection: Collection): String {
        /* Extended PLS Format
        [playlist]

        Title1=My Cool Stream
        File1=http://www.site.com:8000/listen.pls
        Length1=-1

        NumberOfEntries=1
        Version=2
         */

        val plsString = StringBuilder()
        var counter = 1

        // add opening tag
        plsString.append("[playlist]")
        plsString.append("\n")

        // add name and stream address
        collection.stations.forEach { station ->
            plsString.append("\n")
            plsString.append("Title$counter=")
            plsString.append(station.name)
            plsString.append("\n")
            plsString.append("File$counter=")
            plsString.append(station.getStreamUri())
            plsString.append("\n")
            plsString.append("Length$counter=-1")
            plsString.append("\n")
            counter++
        }

        // add ending tag
        plsString.append("\n")
        plsString.append("NumberOfEntries=${collection.stations.size}")
        plsString.append("\n")
        plsString.append("Version=2")

        return plsString.toString()
    }


    /* Sends a broadcast containing the collection as parcel */
    fun sendCollectionBroadcast(context: Context, modificationDate: Date) {
        Log.v(TAG, "Broadcasting that collection has changed.")
        val collectionChangedIntent = Intent()
        collectionChangedIntent.action = Keys.ACTION_COLLECTION_CHANGED
        collectionChangedIntent.putExtra(
            Keys.EXTRA_COLLECTION_MODIFICATION_DATE,
            modificationDate.time
        )
        LocalBroadcastManager.getInstance(context).sendBroadcast(collectionChangedIntent)
    }


    //    /* Creates MediaMetadata for a single station - used in media session*/
//    fun buildStationMediaMetadata(context: Context, station: Station, metadata: String): MediaMetadataCompat {
//        return MediaMetadataCompat.Builder().apply {
//            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, station.name)
//            putString(MediaMetadataCompat.METADATA_KEY_TITLE, metadata)
//            putString(MediaMetadataCompat.METADATA_KEY_ALBUM, context.getString(R.string.app_name))
//            putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, station.getStreamUri())
//            putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, ImageHelper.getScaledStationImage(context, station.image, Keys.SIZE_COVER_LOCK_SCREEN))
//            //putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, station.image)
//        }.build()
//    }
//
//
//    /* Creates MediaItem for a station - used by collection provider */
//    fun buildStationMediaMetaItem(context: Context, station: Station): MediaBrowserCompat.MediaItem {
//        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
//        mediaDescriptionBuilder.setMediaId(station.uuid)
//        mediaDescriptionBuilder.setTitle(station.name)
//        mediaDescriptionBuilder.setIconBitmap(ImageHelper.getScaledStationImage(context, station.image, Keys.SIZE_COVER_LOCK_SCREEN))
//        // mediaDescriptionBuilder.setIconUri(station.image.toUri())
//        return MediaBrowserCompat.MediaItem(mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
//    }
//
//
//    /* Creates description for a station - used in MediaSessionConnector */
//    fun buildStationMediaDescription(context: Context, station: Station, metadata: String): MediaDescriptionCompat {
//        val coverBitmap: Bitmap = ImageHelper.getScaledStationImage(context, station.image, Keys.SIZE_COVER_LOCK_SCREEN)
//        val extras: Bundle = Bundle()
//        extras.putParcelable(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap)
//        extras.putParcelable(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, coverBitmap)
//        return MediaDescriptionCompat.Builder().apply {
//            setMediaId(station.uuid)
//            setIconBitmap(coverBitmap)
//            setIconUri(station.image.toUri())
//            setTitle(metadata)
//            setSubtitle(station.name)
//            setExtras(extras)
//        }.build()
//    }


    /* Creates a MediaItem with MediaMetadata for a single radio station - used to prepare player */
    fun buildMediaItem(context: Context, station: Station): MediaItem {
        // todo implement HLS MediaItems
        // put uri in RequestMetadata - credit: https://stackoverflow.com/a/70103460
        val requestMetadata = MediaItem.RequestMetadata.Builder().apply {
            setMediaUri(station.getStreamUri().toUri())
        }.build()
        // build MediaMetadata
        val mediaMetadata = MediaMetadata.Builder().apply {
            setArtist(station.name)
            //setTitle(station.name)
            /* check for "file://" prevents a crash when an old backup was restored */
            if (station.image.isNotEmpty() && station.image.startsWith("file://")) {
                //setArtworkUri(station.image.toUri())
                setArtworkData(station.image.toUri().toFile().readBytes(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            } else {
                //setArtworkUri(Uri.parse(Keys.LOCATION_RESOURCES + R.raw.ic_default_station_image))
                setArtworkData(ImageHelper.getStationImageAsByteArray(context), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            setIsBrowsable(false)
            setIsPlayable(true)
        }.build()
        // build MediaItem and return it
        return MediaItem.Builder().apply {
            setMediaId(station.uuid)
            setRequestMetadata(requestMetadata)
            setMediaMetadata(mediaMetadata)
            //setMimeType(station.getMediaType())
            setUri(station.getStreamUri().toUri())
        }.build()
    }


    /* Creates a fallback station - stupid hack for Android Auto compatibility :-/ */
    fun createFallbackStation(): Station {
        return Station(
            name = "KCSB",
            streamUris = mutableListOf("http://live.kcsb.org:80/KCSB_128"),
            streamContent = Keys.MIME_TYPE_MPEG
        )
    }


    /* Sorts radio stations */
    fun sortCollection(collection: Collection): Collection {
        val favoriteStations = collection.stations.filter { it.starred }
        val otherStations = collection.stations.filter { !it.starred }
        collection.stations = (favoriteStations + otherStations) as MutableList<Station>
        return collection
    }


    /* Get favicon address */
    private fun getFaviconAddress(urlString: String): String {
        var faviconAddress = String()
        try {
            var host: String = URL(urlString).host
            if (!host.startsWith("www")) {
                val index = host.indexOf(".")
                host = "www" + host.substring(index)
            }
            faviconAddress = "http://$host/favicon.ico"
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get base URL from $urlString.\n$e ")
        }
        return faviconAddress
    }


    /* Converts search result JSON string */
    fun createRadioBrowserResult(result: String): Array<RadioBrowserResult> {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setDateFormat("M/d/yy hh:mm a")
        val gson = gsonBuilder.create()
        return gson.fromJson(result, Array<RadioBrowserResult>::class.java)
    }

}
