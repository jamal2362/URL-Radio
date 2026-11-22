/*
 * RecordingStorage.kt
 * Moves finished recordings to where the user (and other music apps) can find them
 *
 * Android 10 and newer: the shared "Music/URL Radio" folder via MediaStore, which needs no
 * storage permission. Older versions: the app's own music folder on external storage
 * (Android/data/<package>/files/Music), which needs no permission either.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.recording

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File


object RecordingStorage {

    private val tag: String = RecordingStorage::class.java.simpleName

    private const val FOLDER_NAME = "URL Radio"
    private const val MIME_TYPE = "audio/mp4"


    /* Copies a finished recording into the recordings folder. Returns whether that worked. */
    fun save(context: Context, source: File, displayName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(context, source, displayName)
            } else {
                saveToAppFolder(context, source, displayName)
            }
        } catch (e: Exception) {
            Log.e(tag, "Unable to save recording $displayName.", e)
            false
        }
    }


    /* Human readable location of the recordings folder, for messages. */
    fun folderDescription(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${Environment.DIRECTORY_MUSIC}/$FOLDER_NAME"
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.absolutePath ?: FOLDER_NAME
        }
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(context: Context, source: File, displayName: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER_NAME")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection: Uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri: Uri = resolver.insert(collection, values) ?: return false
        return try {
            val output = resolver.openOutputStream(uri) ?: throw IllegalStateException("No output stream for $uri")
            output.use { out -> source.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }


    private fun saveToAppFolder(context: Context, source: File, displayName: String): Boolean {
        val folder: File = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return false
        folder.mkdirs()
        var target = File(folder, displayName)
        var counter = 1
        while (target.exists()) {
            target = File(folder, "${displayName.substringBeforeLast('.')} ($counter).${displayName.substringAfterLast('.')}")
            counter++
        }
        source.copyTo(target)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(MIME_TYPE), null)
        return true
    }
}
