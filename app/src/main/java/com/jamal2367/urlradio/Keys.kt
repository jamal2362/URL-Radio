/*
 * Keys.kt
 * Implements the keys used throughout the app
 * This object hosts all keys used to control URLRadio state
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio

import java.util.*


/*
 * Keys object
 */
object Keys {

    // version numbers
    const val CURRENT_COLLECTION_CLASS_VERSION_NUMBER: Int = 0

    // time values
    const val SLEEP_TIMER_DURATION = "SLEEP_TIMER_DURATION"
    const val RECONNECTION_WAIT_INTERVAL: Long = 500L       // 5 seconds in milliseconds

    // intent actions
    const val ACTION_SHOW_PLAYER: String = "com.jamal2367.urlradio.action.SHOW_PLAYER"
    const val ACTION_COLLECTION_CHANGED: String = "com.jamal2367.urlradio.action.COLLECTION_CHANGED"
    const val ACTION_START: String = "com.jamal2367.urlradio.action.START"

    // intent extras
    const val EXTRA_COLLECTION_MODIFICATION_DATE: String = "COLLECTION_MODIFICATION_DATE"
    const val EXTRA_STATION_UUID: String = "STATION_UUID"
    const val EXTRA_STREAM_URI: String = "STREAM_URI"
    const val EXTRA_START_LAST_PLAYED_STATION: String = "START_LAST_PLAYED_STATION"
    const val EXTRA_SLEEP_TIMER_REMAINING: String = "SLEEP_TIMER_REMAINING"
    const val EXTRA_METADATA_HISTORY: String = "METADATA_HISTORY"

    // arguments
    const val ARG_UPDATE_COLLECTION: String = "ArgUpdateCollection"
    const val ARG_UPDATE_IMAGES: String = "ArgUpdateImages"
    const val ARG_RESTORE_COLLECTION: String = "ArgRestoreCollection"

    // keys
    const val KEY_SAVE_INSTANCE_STATE_STATION_LIST: String = "SAVE_INSTANCE_STATE_STATION_LIST"
    const val KEY_STREAM_URI: String = "STREAM_URI"

    // custom MediaController commands
    const val CMD_START_SLEEP_TIMER: String = "START_SLEEP_TIMER"
    const val CMD_CANCEL_SLEEP_TIMER: String = "CANCEL_SLEEP_TIMER"
    const val CMD_PLAY_STREAM: String = "PLAY_STREAM"
    const val CMD_REQUEST_SLEEP_TIMER_REMAINING: String = "REQUEST_SLEEP_TIMER_REMAINING"
    const val CMD_REQUEST_METADATA_HISTORY: String = "REQUEST_METADATA_HISTORY"

    // preferences
    const val PREF_RADIO_BROWSER_API: String = "RADIO_BROWSER_API"
    const val PREF_ONE_TIME_HOUSEKEEPING_NECESSARY: String = "ONE_TIME_HOUSEKEEPING_NECESSARY_VERSIONCODE_95" // increment to current app version code to trigger housekeeping that runs only once
    const val PREF_THEME_SELECTION: String = "THEME_SELECTION"
    const val PREF_LAST_UPDATE_COLLECTION: String = "LAST_UPDATE_COLLECTION"
    const val PREF_COLLECTION_SIZE: String = "COLLECTION_SIZE"
    const val PREF_COLLECTION_MODIFICATION_DATE: String = "COLLECTION_MODIFICATION_DATE"
    const val PREF_ACTIVE_DOWNLOADS: String = "ACTIVE_DOWNLOADS"
    const val PREF_DOWNLOAD_OVER_MOBILE: String = "DOWNLOAD_OVER_MOBILE"
    const val PREF_STATION_LIST_EXPANDED_UUID = "STATION_LIST_EXPANDED_UUID"
    const val PREF_PLAYER_STATE_STATION_UUID: String = "PLAYER_STATE_STATION_UUID"
    const val PREF_PLAYER_STATE_IS_PLAYING: String = "PLAYER_STATE_IS_PLAYING"
    const val PREF_PLAYER_METADATA_HISTORY: String = "PLAYER_METADATA_HISTORY"
    const val PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING: String = "PLAYER_STATE_SLEEP_TIMER_RUNNING"
    const val PREF_LARGE_BUFFER_SIZE: String = "LARGE_BUFFER_SIZE"
    const val PREF_EDIT_STATIONS: String = "EDIT_STATIONS"
    const val PREF_EDIT_STREAMS_URIS: String = "EDIT_STREAMS_URIS"

    // default const values
    const val DEFAULT_SIZE_OF_METADATA_HISTORY: Int = 25
    const val DEFAULT_MAX_LENGTH_OF_METADATA_ENTRY: Int = 127
    const val DEFAULT_DOWNLOAD_OVER_MOBILE: Boolean = false
    const val ACTIVE_DOWNLOADS_EMPTY: String = "zero"
    const val DEFAULT_MAX_RECONNECTION_COUNT: Int = 30
    const val LARGE_BUFFER_SIZE_MULTIPLIER: Int = 8

    // view types
    const val VIEW_TYPE_ADD_NEW: Int = 1
    const val VIEW_TYPE_STATION: Int = 2

    // view holder update types
    const val HOLDER_UPDATE_COVER: Int = 0
    const val HOLDER_UPDATE_NAME: Int = 1
    const val HOLDER_UPDATE_PLAYBACK_STATE: Int = 2
    const val HOLDER_UPDATE_DOWNLOAD_STATE: Int = 3
    const val HOLDER_UPDATE_PLAYBACK_PROGRESS: Int = 4

    // dialog types
    const val DIALOG_UPDATE_COLLECTION: Int = 1
    const val DIALOG_REMOVE_STATION: Int = 2
    const val DIALOG_UPDATE_STATION_IMAGES: Int = 4
    const val DIALOG_RESTORE_COLLECTION: Int = 5

    // dialog results
    const val DIALOG_EMPTY_PAYLOAD_STRING: String = ""
    const val DIALOG_EMPTY_PAYLOAD_INT: Int = -1

    // search types
    const val SEARCH_TYPE_BY_KEYWORD = 0
    const val SEARCH_TYPE_BY_UUID = 1

    // file types
    const val FILE_TYPE_PLAYLIST: Int = 10
    const val FILE_TYPE_AUDIO: Int = 20
    const val FILE_TYPE_IMAGE: Int = 3

    // mime types and charsets and file extensions
    const val CHARSET_UNDEFINDED = "undefined"
    const val MIME_TYPE_JPG = "image/jpeg"
    const val MIME_TYPE_PNG = "image/png"
    const val MIME_TYPE_M3U = "audio/x-mpegurl"
    const val MIME_TYPE_PLS = "audio/x-scpls"
    const val MIME_TYPE_ZIP = "application/zip"
    const val MIME_TYPE_OCTET_STREAM = "application/octet-stream"
    const val MIME_TYPE_UNSUPPORTED = "unsupported"
    val MIME_TYPES_M3U = arrayOf("application/mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl")
    val MIME_TYPES_PLS = arrayOf("audio/x-scpls", "application/pls+xml")
    val MIME_TYPES_HLS = arrayOf("application/vnd.apple.mpegurl", "application/vnd.apple.mpegurl.audio")
    val MIME_TYPES_MPEG = arrayOf("audio/mpeg")
    val MIME_TYPES_OGG = arrayOf("audio/ogg", "application/ogg", "audio/opus")
    val MIME_TYPES_AAC = arrayOf("audio/aac", "audio/aacp")
    val MIME_TYPES_IMAGE = arrayOf("image/png", "image/jpeg")
    val MIME_TYPES_FAVICON = arrayOf("image/x-icon", "image/vnd.microsoft.icon")
    val MIME_TYPES_ZIP = arrayOf("application/zip", "application/x-zip-compressed", "multipart/x-zip")

    // folder names
    const val FOLDER_COLLECTION: String = "collection"
    const val FOLDER_AUDIO: String = "audio"
    const val FOLDER_IMAGES: String = "images"
    const val FOLDER_TEMP: String = "temp"
    const val URLRADIO_LEGACY_FOLDER_COLLECTION: String = "Collection"

    // file names and extensions
    const val COLLECTION_FILE: String = "collection.json"
    const val COLLECTION_M3U_FILE: String = "collection.m3u"
    const val COLLECTION_PLS_FILE: String = "collection.pls"
    const val STATION_IMAGE_FILE: String = "station-image.jpg"

    // server addresses
    const val RADIO_BROWSER_API_BASE: String = "all.api.radio-browser.info"
    const val RADIO_BROWSER_API_DEFAULT: String = "de1.api.radio-browser.info"

    // locations
    const val LOCATION_DEFAULT_STATION_IMAGE: String = "android.resource://com.jamal2367.urlradio/drawable/ic_default_station_image_24dp"

    // sizes (in dp)
    const val SIZE_STATION_IMAGE_CARD: Int = 72
    const val SIZE_STATION_IMAGE_MAXIMUM: Int = 640
    const val BOTTOM_SHEET_PEEK_HEIGHT: Int = 72

    // default values
    val DEFAULT_DATE: Date = Date(0L)
    const val EMPTY_STRING_RESOURCE: Int = 0

    // theme states
    const val STATE_THEME_FOLLOW_SYSTEM: String = "stateFollowSystem"
    const val STATE_THEME_LIGHT_MODE: String = "stateLightMode"
    const val STATE_THEME_DARK_MODE: String = "stateDarkMode"

}
