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
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.R
import com.jamal2367.urlradio.core.Collection
import com.jamal2367.urlradio.core.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale


/*
 * CollectionHelper object
 */
object CollectionHelper {

    /* Define log tag */
    private val tag: String = CollectionHelper::class.java.simpleName

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
            FileHelper.readStationPlaylist(context.contentResolver.openInputStream(localFileUri), remoteFileLocation)
        if (station.name.isEmpty()) {
            // construct name from file name - strips file extension
            station.name = FileHelper.getFileName(context, localFileUri).substringBeforeLast(".")
        }
        station.remoteStationLocation = remoteFileLocation
        // the logo the playlist names, otherwise the favicon of the server it came from
        if (station.remoteImageLocation.isEmpty()) {
            station.remoteImageLocation = getFaviconAddress(remoteFileLocation)
        }
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
                    DownloadHelper.updateStationImage(context, it)
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
                    // a playlist without a logo keeps the one the station has
                    if (station.remoteImageLocation.isNotEmpty()) {
                        it.remoteImageLocation = station.remoteImageLocation
                    }
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
            UserMessages.notify(context.getString(R.string.snackbar_station_not_valid))
            return collection
        }
        // duplicate check
        else if (!isNewStation(collection, newStation)) {
            // update station
            UserMessages.notify(context.getString(R.string.snackbar_station_duplicate))
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
                station.modificationDate = GregorianCalendar.getInstance().time
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


    /* Gets MediaItem for next station within collection */
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


    /* Gets MediaItem for previous station within collection */
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


    /* Hashtags in a station name: "WFPK #local #indie" -> [#local, #indie] */
    fun getTags(name: String): List<String> = tagRegex.findAll(name).map { it.value.lowercase() }.toList()
    /* The name without its #tags - as the notification, the widget and the tile show it */
    fun nameWithoutTags(name: String): String = name.replace(tagRegex, "").trim().ifEmpty { name }
    private val tagRegex = Regex("#\\p{L}[\\p{L}\\d_-]*")

    /* Levenshtein edit distance between two words, for misheard station names */
    fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val cur = IntArray(b.length + 1).also { it[0] = i + 1 }
            for (j in b.indices) cur[j + 1] = minOf(prev[j + 1] + 1, cur[j] + 1, prev[j] + if (a[i] == b[j]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    /* Stations carrying the tag, or all of them for no tag */
    fun getStations(collection: Collection, tag: String?): List<Station> =
        if (tag == null) collection.stations else collection.stations.filter { tag in getTags(it.name) }

    /* The car shows All plus the three most used tags as tabs; the rest work by voice */
    private fun getTopTags(collection: Collection): List<String> = collection.stations
        .flatMap { getTags(it.name) }.groupingBy { it }.eachCount().entries
        .sortedByDescending { it.value }.take(3).map { it.key }

    /* Root > tabs (All, #tag...) > stations, or root > stations when nothing is tagged.
     * Stations under a tab get the id "#tag/uuid" so playing one remembers the tab. */
    fun getChildren(context: Context, collection: Collection, parentId: String): List<MediaItem> {
        val tabs: List<String> = getTopTags(collection)
        return if (parentId == "[rootID]" && tabs.isNotEmpty()) {
            (listOf("[all]") + tabs).map { id ->
                val title: String = if (id == "[all]") "All" else id.drop(1).replaceFirstChar(Char::uppercase)
                MediaItem.Builder().setMediaId(id).setMediaMetadata(MediaMetadata.Builder().setTitle(title)
                    .setIsPlayable(false).setIsBrowsable(true).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS).build()).build()
            }
        } else {
            val tag: String? = parentId.takeIf { it.startsWith("#") }
            getStations(collection, tag).map { station ->
                buildMediaItem(context, station).let { if (tag == null) it else it.buildUpon().setMediaId("$tag/${station.uuid}").build() }
            }
        }
    }


    /* Returns media item for given station id ("uuid" or "#tag/uuid") */
    fun getItem(context: Context, collection: Collection, stationUuid: String): MediaItem {
        return buildMediaItem(context, getStation(collection, stationUuid.substringAfter("/")))
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
        // The caller here is the player service, which keeps its own copy of the collection
        // and only refreshes it when the collection-changed broadcast reaches it. Writing an
        // out-of-date copy back would undo whatever changed the collection in the meantime --
        // removing every station, say, where the pause that goes with it would otherwise put
        // them all straight back. The service reloads from the broadcast either way, so
        // skipping to write here loses nothing but the playback flag.
        if (PreferencesHelper.loadCollectionModificationDate().after(collection.modificationDate)) {
            Log.v(tag, "Not saving playback state. Reason: collection on storage is newer.")
            return collection
        }
        // save collection and store modification date
        collection.modificationDate = saveCollection(context, collection)
        return collection
    }


    /* Saves collection of radio stations */
    /**
     * @param allowEmpty lets a deliberately emptied collection through the guard in
     *   [FileHelper.saveCollection], which otherwise refuses to replace a stored collection
     *   with an empty one.
     */
    fun saveCollection(
        context: Context,
        collection: Collection,
        async: Boolean = true,
        allowEmpty: Boolean = false,
    ): Date {
        Log.v(
            tag,
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
                    FileHelper.saveCollectionSuspended(context, collection, date, allowEmpty)
                    // broadcast collection update
                    sendCollectionBroadcast(date)
                }
            }
            false -> {
                // save collection
                FileHelper.saveCollection(context, collection, date, allowEmpty)
                // broadcast collection update
                sendCollectionBroadcast(date)
            }
        }
        // return modification date
        return date
    }


    /* Why an address the user entered did not yield any station */
    enum class UrlProblem {
        /** The address could not be reached, or did not answer with 200 OK. */
        UNREACHABLE,
        /** The address answered, but with something that is neither a stream nor a playlist. */
        NOT_AUDIO,
        /** A playlist without a single playable entry. */
        EMPTY_PLAYLIST,
    }


    /* The outcome of checking an address: stations, or the problem and its technical detail */
    data class UrlCheck(
        val stations: List<Station>,
        val problem: UrlProblem? = null,
        val detail: String = String(),
    )


    /* Creates station from playlist URLs and stream address URLs */
    suspend fun createStationsFromUrl(query: String): List<Station> = checkStationsFromUrl(query).stations


    /*
     * Checks a playlist or stream address. Unlike createStationsFromUrl it also tells why an
     * address yielded nothing, so the search dialog can show it.
     */
    suspend fun checkStationsFromUrl(query: String): UrlCheck {
        val detected: NetworkHelper.ContentType = NetworkHelper.detectContentType(query)
        if (detected.error.isNotEmpty()) {
            return UrlCheck(emptyList(), UrlProblem.UNREACHABLE, detected.error)
        }
        val contentType: String = detected.type.lowercase(Locale.getDefault())

        // CASE: M3U or PLS playlist - or an M3U8 address, which can be either an HLS stream
        // or a plain station list (often with logos), and only its content tells which
        val isHls: Boolean = Keys.MIME_TYPES_HLS.contains(contentType)
        if (isHls || Keys.MIME_TYPES_M3U.contains(contentType) || Keys.MIME_TYPES_PLS.contains(contentType)) {
            val lines: List<String> = try {
                NetworkHelper.downloadPlaylist(query)
            } catch (e: Exception) {
                // an HLS stream that cannot be read as text is still worth a try in the player
                if (isHls) return UrlCheck(listOf(createStationFromStreamUrl(query, contentType)))
                return UrlCheck(emptyList(), UrlProblem.UNREACHABLE, e.localizedMessage ?: e.javaClass.simpleName)
            }
            // HLS playlists are often sent as plain M3U - they are a stream, not a station list
            if (lines.any { it.startsWith("#EXT-X-") }) {
                return UrlCheck(listOf(createStationFromStreamUrl(query, Keys.MIME_TYPES_HLS.first())))
            }
            val stations: List<Station> = if (isHls || Keys.MIME_TYPES_M3U.contains(contentType)) {
                readM3uPlaylistContent(lines, query)
            } else {
                readPlsPlaylistContent(lines)
            }
            return if (stations.isNotEmpty()) UrlCheck(stations)
            else UrlCheck(emptyList(), UrlProblem.EMPTY_PLAYLIST)
        }

        // CASE: stream address
        if (isStreamContentType(contentType)) {
            // named and rated the way the server announces itself, rather than by its address
            val station: Station = createStationFromStreamUrl(query, contentType)
            if (detected.icyName.isNotEmpty()) station.name = detected.icyName
            if (detected.icyBitrate > 0) station.bitrate = detected.icyBitrate
            return UrlCheck(listOf(station))
        }
        return UrlCheck(emptyList(), UrlProblem.NOT_AUDIO, detected.type)
    }


    /*
     * Whether a content type stands for something the player can stream. Beyond the types
     * the app has always known this takes any audio type (FLAC, MP4 audio, x-aac ...), MPEG
     * transport streams and octet-stream, which many radio servers send for their stream -
     * these used to be turned away even though they play fine.
     */
    private fun isStreamContentType(contentType: String): Boolean =
        Keys.MIME_TYPES_MPEG.contains(contentType) ||
            Keys.MIME_TYPES_OGG.contains(contentType) ||
            Keys.MIME_TYPES_AAC.contains(contentType) ||
            Keys.MIME_TYPES_HLS.contains(contentType) ||
            contentType.startsWith("audio/") ||
            contentType == "video/mp2t" ||
            contentType == Keys.MIME_TYPE_OCTET_STREAM


    private fun createStationFromStreamUrl(streamUri: String, contentType: String): Station =
        Station(
            name = streamUri,
            streamUris = mutableListOf(streamUri),
            streamContent = contentType,
            modificationDate = GregorianCalendar.getInstance().time
        )


    /*
     * Creates stations from a URI pointing to a local .m3u or .pls file.
     *
     * This is what both the "open playlist" intent and the playlist import in the settings
     * end up calling.
     */
    suspend fun createStationListFromContentUri(context: Context, contentUri: Uri): List<Station> {
        val fileType: String = FileHelper.getContentType(context, contentUri)
        val playlist: List<String> = FileHelper.readTextFileFromContentUri(context, contentUri)
        return when {
            // CASE: M3U playlist detected
            Keys.MIME_TYPES_M3U.contains(fileType) -> readM3uPlaylistContent(playlist)
            // CASE: PLS playlist detected
            Keys.MIME_TYPES_PLS.contains(fileType) -> readPlsPlaylistContent(playlist)
            else -> emptyList()
        }
    }


    /* A playlist entry - name, stream address and logo - before its type has been checked */
    private data class PlaylistEntry(val name: String, val streamUri: String, val logo: String = String())


    /*
     * Reads a m3u playlist and returns a list of stations. With the playlist's own address,
     * relative stream and logo addresses in it are resolved against that address.
     */
    private suspend fun readM3uPlaylistContent(playlist: List<String>, playlistAddress: String? = null): List<Station> {
        val entries: MutableList<PlaylistEntry> = mutableListOf()
        var name = String()
        var logo = String()

        playlist.forEach { rawLine ->
            val line: String = rawLine.trim()
            when {
                // name and logo of the next station
                line.startsWith("#EXTINF:") -> {
                    val extInf: M3uHelper.ExtInf = M3uHelper.parseExtInf(line)
                    name = extInf.name
                    if (extInf.logo.isNotEmpty()) logo = extInf.logo
                }
                // logo of the next station, in a line of its own
                line.startsWith("#EXTIMG:") -> logo = M3uHelper.parseExtImg(line)
                // stream address
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val streamUri: String = M3uHelper.resolveAddress(line, playlistAddress).ifEmpty { line }
                    // use the stream address as the name if no name is specified
                    entries.add(PlaylistEntry(name.ifEmpty { streamUri }, streamUri, M3uHelper.resolveAddress(logo, playlistAddress)))
                    // reset for the next station - useful if the playlist does not name every one
                    name = String()
                    logo = String()
                }
            }
        }
        return createStationsFromEntries(entries)
    }


    /* Reads a pls playlist and returns a list of stations */
    private suspend fun readPlsPlaylistContent(playlist: List<String>): List<Station> {
        val entries: MutableList<PlaylistEntry> = mutableListOf()

        playlist.forEachIndexed { index, line ->
            // get stream uri
            if (line.startsWith("File") && line.contains("=")) {
                var name = String()
                val streamUri: String = line.substringAfter("=").trim()
                // look for the matching station name
                val number: String = line.substring(4 /* File */, line.indexOf("="))
                val lineBeforeIndex: Int = index - 1
                val lineAfterIndex: Int = index + 1
                // first: check the line before
                if (lineBeforeIndex >= 0) {
                    val lineBefore: String = playlist[lineBeforeIndex]
                    if (lineBefore.startsWith("Title$number")) {
                        name = lineBefore.substringAfter("=").trim()
                    }
                }
                // then: check the line after
                if (name.isEmpty() && lineAfterIndex < playlist.size) {
                    val lineAfter: String = playlist[lineAfterIndex]
                    if (lineAfter.startsWith("Title$number")) {
                        name = lineAfter.substringAfter("=").trim()
                    }
                }
                // fallback: use stream uri as name
                entries.add(PlaylistEntry(name.ifEmpty { streamUri }, streamUri))
            }
        }
        return createStationsFromEntries(entries)
    }


    /*
     * Turns playlist entries into stations, dropping everything that does not serve a
     * supported stream.
     *
     * Every entry needs a request of its own to find out what it serves. Those requests run
     * side by side rather than one after the other -- a playlist with fifty entries used to
     * take fifty round trips before the app showed anything. The permit count keeps that from
     * turning into fifty simultaneous connections.
     */
    private suspend fun createStationsFromEntries(entries: List<PlaylistEntry>): List<Station> =
        coroutineScope {
            val permits = Semaphore(MAX_PARALLEL_CONTENT_TYPE_CHECKS)
            entries.map { entry ->
                async(IO) {
                    permits.withPermit {
                        val contentType: String = NetworkHelper.detectContentType(entry.streamUri)
                            .type.lowercase(Locale.getDefault())
                        if (contentType == Keys.MIME_TYPE_UNSUPPORTED) {
                            null
                        } else {
                            Station(
                                name = entry.name,
                                streamUris = mutableListOf(entry.streamUri),
                                streamContent = contentType,
                                remoteImageLocation = entry.logo,
                                modificationDate = GregorianCalendar.getInstance().time
                            )
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }


    /* How many playlist entries are checked at the same time */
    private const val MAX_PARALLEL_CONTENT_TYPE_CHECKS = 8


    /* Export collection of stations as M3U */
    fun exportCollectionM3u(context: Context, collection: Collection) {
        Log.v(tag, "Exporting collection of stations as M3U")
        // export collection as M3U - launch = fire & forget (no return value from save collection)
        if (collection.stations.isNotEmpty()) {
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
        #EXTINF:-1 tvg-logo="https://www.site.com/logo.png",My Cool Stream
        http://www.site.com:8000/listen.pls
         */

        // add opening tag
        m3uString.append("#EXTM3U")
        m3uString.append("\n")

        // add name and stream address
        collection.stations.forEach { station ->
            m3uString.append("\n")
            // the logo goes along as tvg-logo, so a re-import brings it back
            m3uString.append(M3uHelper.createExtInf(station.name, station.remoteImageLocation))
            m3uString.append("\n")
            m3uString.append(station.getStreamUri())
            m3uString.append("\n")
        }

        return m3uString.toString()
    }


    /* Export collection of stations as PLS */
    fun exportCollectionPls(context: Context, collection: Collection) {
        Log.v(tag, "Exporting collection of stations as PLS")
        // export collection as PLS - launch = fire & forget (no return value from save collection)
        if (collection.stations.isNotEmpty()) {
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


    /* Announces that the collection on storage has changed */
    fun sendCollectionBroadcast(modificationDate: Date) {
        Log.v(tag, "Broadcasting that collection has changed.")
        CollectionChanges.notifyChanged(modificationDate)
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
            // name without its hashtags; the tags show underneath
            setArtist(station.name.replace(tagRegex, "").trim())
            setAlbumTitle(getTags(station.name).joinToString("  ").ifEmpty { null })
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
            Log.e(tag, "Unable to get base URL from $urlString.\n$e ")
        }
        return faviconAddress
    }

}
