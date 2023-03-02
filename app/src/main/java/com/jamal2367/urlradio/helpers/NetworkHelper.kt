/*
 * NetworkHelper.kt
 * Implements the NetworkHelper object
 * A NetworkHelper provides helper methods for network related operations
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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.*
import android.os.Build
import android.util.Log
import com.jamal2367.urlradio.Keys
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/*
 * NetworkHelper object
 */
object NetworkHelper {

    /* Define log tag */
    private val TAG: String = NetworkHelper::class.java.simpleName

    /* Data class: holder for content type information */
    data class ContentType(var type: String = String(), var charset: String = String())


    /* Checks if the active network connection is over Wifi */
    fun isConnectedToWifi(context: Context): Boolean {
        var result = false
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connMgr.activeNetwork
        } else {
            return true
        }
        if (activeNetwork != null) {
            val capabilities: NetworkCapabilities? = connMgr.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                // check if a Wifi connection is active
                result = capabilities.hasTransport(TRANSPORT_WIFI)
            }
        }
        return result
    }


    /* Checks if the active network connection is over Cellular */
    fun isConnectedToCellular(context: Context): Boolean {
        var result = false
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connMgr.activeNetwork
        } else {
            return true
        }
        if (activeNetwork != null) {
            val capabilities: NetworkCapabilities? = connMgr.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                // check if a cellular connection is active
                result = capabilities.hasTransport(TRANSPORT_CELLULAR)
            }
        }
        return result
    }


    /* Checks if the active network connection is over VPN */
    fun isConnectedToVpn(context: Context): Boolean {
        var result = false
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connMgr.activeNetwork
        } else {
            return true
        }
        if (activeNetwork != null) {
            val capabilities: NetworkCapabilities? = connMgr.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                // check if a VPN connection is active
                result = capabilities.hasTransport(TRANSPORT_VPN)
            }
        }
        return result
    }


    /* Checks if the active network connection is connected to any network */
    fun isConnectedToNetwork(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connMgr.activeNetwork
        } else {
            return true
        }
        return activeNetwork != null
    }


    /* Detects content type (mime type) from given URL string - async using coroutine - use only on separate threat */
    fun detectContentType(urlString: String): ContentType {
        Log.v(TAG, "Determining content type - Thread: ${Thread.currentThread().name}")
        val contentType = ContentType(Keys.MIME_TYPE_UNSUPPORTED, Keys.CHARSET_UNDEFINDED)
        val connection: HttpURLConnection? = createConnection(urlString)
        if (connection != null) {
            val contentTypeHeader: String = connection.contentType ?: String()
            Log.v(TAG, "Raw content type header: $contentTypeHeader")
            val contentTypeHeaderParts: List<String> = contentTypeHeader.split(";")
            contentTypeHeaderParts.forEachIndexed { index, part ->
                if (index == 0 && part.isNotEmpty()) {
                    contentType.type = part.trim()
                } else if (part.contains("charset=")) {
                    contentType.charset = part.substringAfter("charset=").trim()
                }
            }

            // special treatment for octet-stream - try to get content type from file extension
            if (contentType.type.contains(Keys.MIME_TYPE_OCTET_STREAM)) {
                Log.w(TAG, "Special case \"application/octet-stream\"")
                val headerFieldContentDisposition: String? =
                    connection.getHeaderField("Content-Disposition")
                if (headerFieldContentDisposition != null) {
                    val fileName: String = headerFieldContentDisposition.split("=")[1].replace(
                        "\"",
                        ""
                    ) //getting value after '=' & stripping any "s
                    contentType.type = FileHelper.getContentTypeFromExtension(fileName)
                } else {
                    Log.i(TAG, "Unable to get file name from \"Content-Disposition\" header field.")
                }
            }

            connection.disconnect()
        }
        Log.i(TAG, "content type: ${contentType.type} | character set: ${contentType.charset}")
        return contentType
    }


    /* Suspend function: Detects content type (mime type) from given URL string - async using coroutine */
    suspend fun detectContentTypeSuspended(urlString: String): ContentType {
        return suspendCoroutine { cont ->
            cont.resume(detectContentType(urlString))
        }
    }


    /* Suspend function: Gets a random radio-browser.info api address - async using coroutine */
    suspend fun getRadioBrowserServerSuspended(): String {
        return suspendCoroutine { cont ->
            val serverAddress: String = try {
                // get all available radio browser servers
                val serverAddressList: Array<InetAddress> =
                    InetAddress.getAllByName(Keys.RADIO_BROWSER_API_BASE)
                // select a random address
                serverAddressList[Random().nextInt(serverAddressList.size)].canonicalHostName
            } catch (e: UnknownHostException) {
                Keys.RADIO_BROWSER_API_DEFAULT
            }
            PreferencesHelper.saveRadioBrowserApiAddress(serverAddress)
            cont.resume(serverAddress)
        }
    }


    /* Creates a http connection from given url string */
    private fun createConnection(urlString: String, redirectCount: Int = 0): HttpURLConnection? {
        var connection: HttpURLConnection? = null

        try {
            // try to open connection and get status
            Log.i(TAG, "Opening http connection.")
            connection = URL(urlString).openConnection() as HttpURLConnection
            val status = connection.responseCode

            // CHECK for non-HTTP_OK status
            if (status != HttpURLConnection.HTTP_OK) {
                // CHECK for redirect status
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
                    val redirectUrl: String = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (redirectCount < 5) {
                        Log.i(TAG, "Following redirect to $redirectUrl")
                        connection = createConnection(redirectUrl, redirectCount + 1)
                    } else {
                        connection = null
                        Log.e(TAG, "Too many redirects.")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unable to open http connection.")
            e.printStackTrace()
        }

        return connection
    }


}
