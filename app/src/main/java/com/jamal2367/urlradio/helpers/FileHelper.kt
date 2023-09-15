/*
 * FileHelper.kt
 * Implements the FileHelper object
 * A FileHelper provides helper methods for reading and writing files from and to device storage
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.helpers

import android.app.Activity
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.core.Collection
import com.jamal2367.urlradio.core.Station
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import java.io.*
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/*
 * FileHelper object
 */
object FileHelper {


    /* Define log tag */
    private val TAG: String = FileHelper::class.java.simpleName


    /* Get file size for given Uri */
    fun getFileSize(context: Context, uri: Uri): Long {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        return if (cursor != null) {
            val sizeIndex: Int = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            val size: Long = cursor.getLong(sizeIndex)
            cursor.close()
            size
        } else {
            0L
        }
    }


    /* Get file name for given Uri */
    fun getFileName(context: Context, uri: Uri): String {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        return if (cursor != null) {
            val nameIndex: Int = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            val name: String = cursor.getString(nameIndex)
            cursor.close()
            name
        } else {
            String()
        }
    }


    /* Get content type for given file */
    fun getContentType(context: Context, uri: Uri): String {
        // get file type from content resolver
        var contentType: String = context.contentResolver.getType(uri) ?: Keys.MIME_TYPE_UNSUPPORTED
        contentType = contentType.lowercase(Locale.getDefault())
        return if (contentType != Keys.MIME_TYPE_UNSUPPORTED && !contentType.contains(Keys.MIME_TYPE_OCTET_STREAM)) {
            // return the found content type
            contentType
        } else {
            // fallback: try to determine file type based on file extension
            getContentTypeFromExtension(getFileName(context, uri))
        }
    }


    /* Determine content type based on file extension */
    fun getContentTypeFromExtension(fileName: String): String {
        Log.i(TAG, "Deducing content type from file name: $fileName")
        if (fileName.endsWith("m3u", true)) return Keys.MIME_TYPE_M3U
        if (fileName.endsWith("pls", true)) return Keys.MIME_TYPE_PLS
        if (fileName.endsWith("png", true)) return Keys.MIME_TYPE_PNG
        if (fileName.endsWith("jpg", true)) return Keys.MIME_TYPE_JPG
        if (fileName.endsWith("jpeg", true)) return Keys.MIME_TYPE_JPG
        // default return
        return Keys.MIME_TYPE_UNSUPPORTED
    }


    /* Determines a destination folder */
    fun determineDestinationFolderPath(type: Int, stationUuid: String): String {
        val folderPath: String = when (type) {
            Keys.FILE_TYPE_PLAYLIST -> Keys.FOLDER_TEMP
            Keys.FILE_TYPE_AUDIO -> Keys.FOLDER_AUDIO + "/" + stationUuid
            Keys.FILE_TYPE_IMAGE -> Keys.FOLDER_IMAGES + "/" + stationUuid
            else -> "/"
        }
        return folderPath
    }


    /* Clears given folder - keeps given number of files */
    fun clearFolder(folder: File?, keep: Int, deleteFolder: Boolean = false) {
        if (folder != null && folder.exists()) {
            val files = folder.listFiles()!!
            val fileCount: Int = files.size
            files.sortBy { it.lastModified() }
            for (fileNumber in files.indices) {
                if (fileNumber < fileCount - keep) {
                    files[fileNumber].delete()
                }
            }
            if (deleteFolder && keep == 0) {
                folder.delete()
            }
        }
    }


    /* Creates and save a scaled version of the station image */
    fun saveStationImage(
        context: Context,
        stationUuid: String,
        sourceImageUri: Uri,
        size: Int,
        fileName: String
    ): Uri {
        val coverBitmap: Bitmap = ImageHelper.getScaledStationImage(context, sourceImageUri, size)
        val file = File(
            context.getExternalFilesDir(
                determineDestinationFolderPath(
                    Keys.FILE_TYPE_IMAGE,
                    stationUuid
                )
            ), fileName
        )
        writeImageFile(coverBitmap, file)
        return file.toUri()
    }


    /* Saves collection of radio stations as JSON text file */
    fun saveCollection(context: Context, collection: Collection, lastSave: Date) {
        Log.v(TAG, "Saving collection - Thread: ${Thread.currentThread().name}")
        val collectionSize: Int = collection.stations.size
        // do not override an existing collection with an empty one - except when last station is deleted
        if (collectionSize > 0 || PreferencesHelper.loadCollectionSize() == 1) {
            // convert to JSON
            val gson: Gson = getCustomGson()
            var json = String()
            try {
                json = gson.toJson(collection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (json.isNotBlank()) {
                // write text file
                writeTextFile(context, json, Keys.FOLDER_COLLECTION, Keys.COLLECTION_FILE)
                // save modification date and collection size
                PreferencesHelper.saveCollectionModificationDate(lastSave)
                PreferencesHelper.saveCollectionSize(collectionSize)
            } else {
                Log.w(TAG, "Not writing collection file. Reason: JSON string was completely empty.")
            }
        } else {
            Log.w(
                TAG,
                "Not saving collection. Reason: Trying to override an collection with more than one station"
            )
        }
    }


    /* Reads m3u or pls playlists */
    fun readStationPlaylist(playlistInputStream: InputStream?): Station {
        val station = Station()
        if (playlistInputStream != null) {
            val reader = BufferedReader(InputStreamReader(playlistInputStream))
            // until last line reached: read station name and stream address(es)
            reader.forEachLine { line ->
                when {
                    // M3U: found station name
                    line.contains("#EXTINF:-1,") -> station.name = line.substring(11).trim()
                    line.contains("#EXTINF:0,") -> station.name = line.substring(10).trim()
                    // M3U: found stream URL
                    line.startsWith("http") -> station.streamUris.add(0, line.trim())
                    // PLS: found station name
                    line.matches(Regex("^Title[0-9]+=.*")) -> station.name =
                        line.substring(line.indexOf("=") + 1).trim()
                    // PLS: found stream URL
                    line.matches(Regex("^File[0-9]+=http.*")) -> station.streamUris.add(
                        line.substring(
                            line.indexOf("=") + 1
                        ).trim()
                    )
                }

            }
            playlistInputStream.close()
        }
        return station
    }


    /* Reads collection of radio stations from storage using GSON */
    fun readCollection(context: Context): Collection {
        Log.v(TAG, "Reading collection - Thread: ${Thread.currentThread().name}")
        // get JSON from text file
        val json: String = readTextFileFromFile(context)
        var collection = Collection()
        if (json.isNotBlank()) {
            // convert JSON and return as collection
            try {
                collection = getCustomGson().fromJson(json, collection::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Error Reading collection.\nContent: $json")
                e.printStackTrace()
            }
        }
        return collection
    }


    /* Get content Uri for M3U file */
    fun getM3ulUri(activity: Activity): Uri? {
        var m3ulUri: Uri? = null
        // try to get an existing M3U File
        var m3uFile =
            File(activity.getExternalFilesDir(Keys.FOLDER_COLLECTION), Keys.COLLECTION_M3U_FILE)
        if (!m3uFile.exists()) {
            m3uFile = File(
                activity.getExternalFilesDir(Keys.URLRADIO_LEGACY_FOLDER_COLLECTION),
                Keys.COLLECTION_M3U_FILE
            )
        }
        // get Uri for existing M3U File
        if (m3uFile.exists()) {
            m3ulUri = FileProvider.getUriForFile(
                activity,
                "${activity.applicationContext.packageName}.provider",
                m3uFile
            )
        }
        return m3ulUri
    }


    /* Get content Uri for PLS file */
    fun getPlslUri(activity: Activity): Uri? {
        var plslUri: Uri? = null
        // try to get an existing PLS File
        var plsFile =
            File(activity.getExternalFilesDir(Keys.FOLDER_COLLECTION), Keys.COLLECTION_PLS_FILE)
        if (!plsFile.exists()) {
            plsFile = File(
                activity.getExternalFilesDir(Keys.URLRADIO_LEGACY_FOLDER_COLLECTION),
                Keys.COLLECTION_PLS_FILE
            )
        }
        // get Uri for existing M3U File
        if (plsFile.exists()) {
            plslUri = FileProvider.getUriForFile(
                activity,
                "${activity.applicationContext.packageName}.provider",
                plsFile
            )
        }
        return plslUri
    }


    /* Suspend function: Wrapper for saveCollection */
    suspend fun saveCollectionSuspended(
        context: Context,
        collection: Collection,
        lastUpdate: Date
    ) {
        return suspendCoroutine { cont ->
            cont.resume(saveCollection(context, collection, lastUpdate))
        }
    }


    /* Suspend function: Wrapper for readCollection */
    suspend fun readCollectionSuspended(context: Context): Collection =
        withContext(IO) {
            readCollection(context)
        }


    /* Suspend function: Wrapper for copyFile */
    suspend fun saveCopyOfFileSuspended(
        context: Context,
        originalFileUri: Uri,
        targetFileUri: Uri
    ): Boolean {
        return suspendCoroutine { cont ->
            cont.resume(copyFile(context, originalFileUri, targetFileUri))
        }
    }


    /* Suspend function: Exports collection of stations as M3U file - local backup copy */
    suspend fun backupCollectionAsM3uSuspended(context: Context, collection: Collection) {
        return suspendCoroutine { cont ->
            Log.v(TAG, "Backing up collection as M3U - Thread: ${Thread.currentThread().name}")
            // create M3U string
            val m3uString: String = CollectionHelper.createM3uString(collection)
            // save M3U as text file
            cont.resume(
                writeTextFile(
                    context,
                    m3uString,
                    Keys.FOLDER_COLLECTION,
                    Keys.COLLECTION_M3U_FILE
                )
            )
        }
    }


    /* Suspend function: Exports collection of stations as PLS file - local backup copy */
    suspend fun backupCollectionAsPlsSuspended(context: Context, collection: Collection) {
        return suspendCoroutine { cont ->
            Log.v(TAG, "Backing up collection as PLS - Thread: ${Thread.currentThread().name}")
            // create PLS string
            val plsString: String = CollectionHelper.createPlsString(collection)
            // save PLS as text file
            cont.resume(
                writeTextFile(
                    context,
                    plsString,
                    Keys.FOLDER_COLLECTION,
                    Keys.COLLECTION_PLS_FILE
                )
            )
        }
    }


    /* Copies file to specified target */
    private fun copyFile(
        context: Context,
        originalFileUri: Uri,
        targetFileUri: Uri,
    ): Boolean {
        var success = true
        var inputStream: InputStream? = null
        val outputStream: OutputStream?
        try {
            inputStream = context.contentResolver.openInputStream(originalFileUri)
            outputStream = context.contentResolver.openOutputStream(targetFileUri)
            if (outputStream != null && inputStream != null) {
                inputStream.copyTo(outputStream)
                outputStream.close() // Close the output stream after copying
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to copy file.")
            success = false
            exception.printStackTrace()
        } finally {
            inputStream?.close() // Close the input stream in the finally block
        }
        if (success) {
            try {
                // use contentResolver to handle files of type content://
                context.contentResolver.delete(originalFileUri, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to delete the original file. Stack trace: $e")
            }
        }
        return success
    }


    /*  Creates a Gson object */
    private fun getCustomGson(): Gson {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setDateFormat("M/d/yy hh:mm a")
        gsonBuilder.excludeFieldsWithoutExposeAnnotation()
        return gsonBuilder.create()
    }


    /* Create nomedia file in given folder to prevent media scanning */
    fun createNomediaFile(folder: File?) {
        if (folder != null && folder.exists() && folder.isDirectory) {
            val nomediaFile: File = getNoMediaFile(folder)
            if (!nomediaFile.exists()) {
                val noMediaOutStream = FileOutputStream(getNoMediaFile(folder))
                noMediaOutStream.write(0)
            } else {
                Log.v(TAG, ".nomedia file exists already in given folder.")
            }
        } else {
            Log.w(TAG, "Unable to create .nomedia file. Given folder is not valid.")
        }
    }


    /* Reads InputStream from file uri and returns it as String */
    private fun readTextFileFromFile(context: Context): String {
        // todo read https://commonsware.com/blog/2016/03/15/how-consume-content-uri.html
        // https://developer.android.com/training/secure-file-sharing/retrieve-info

        // check if file exists
        val file = File(context.getExternalFilesDir(Keys.FOLDER_COLLECTION), Keys.COLLECTION_FILE)
        if (!file.exists() || !file.canRead()) {
            return String()
        }
        // read until last line reached
        val stream: InputStream = file.inputStream()
        val reader = BufferedReader(InputStreamReader(stream))
        val builder: StringBuilder = StringBuilder()
        reader.forEachLine {
            builder.append(it)
            builder.append("\n")
        }
        stream.close()
        return builder.toString()
    }


    /* Reads InputStream from content uri and returns it as List of String */
    fun readTextFileFromContentUri(context: Context, contentUri: Uri): List<String> {
        val lines: MutableList<String> = mutableListOf()
        try {
            // open input stream from content URI
            val inputStream: InputStream? = context.contentResolver.openInputStream(contentUri)
            if (inputStream != null) {
                val reader: InputStreamReader = inputStream.reader()
                var index = 0
                reader.forEachLine {
                    index += 1
                    if (index < 256)
                        lines.add(it)
                }
                inputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return lines
    }


    /* Writes given text to file on storage */
    @Suppress("SameParameterValue")
    private fun writeTextFile(context: Context, text: String, folder: String, fileName: String) {
        if (text.isNotBlank()) {
            File(context.getExternalFilesDir(folder), fileName).writeText(text)
        } else {
            Log.w(TAG, "Writing text file $fileName failed. Empty text string text was provided.")
        }
    }


    /* Writes given bitmap as image file to storage */
    private fun writeImageFile(
        bitmap: Bitmap,
        file: File
    ) {
        if (file.exists()) file.delete()
        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /* Returns a nomedia file object */
    private fun getNoMediaFile(folder: File): File {
        return File(folder, ".nomedia")
    }

}
