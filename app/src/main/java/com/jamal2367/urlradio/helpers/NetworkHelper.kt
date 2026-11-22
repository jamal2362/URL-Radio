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
import android.util.Log
import com.jamal2367.urlradio.Keys
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.Random
import kotlin.coroutines.resume


/*
 * NetworkHelper object
 */
object NetworkHelper {

    /* Define log tag */
    private val tag: String = NetworkHelper::class.java.simpleName

    /* Data class: holder for content type information */
    /* error stays empty unless the address could not be reached - then it says why */
    /* error stays empty unless the address could not be reached - then it says why.
       icyName and icyBitrate (kbit/s) are what an Icecast / Shoutcast server says about itself. */
    data class ContentType(
        var type: String = String(),
        var charset: String = String(),
        var error: String = String(),
        var icyName: String = String(),
        var icyBitrate: Int = 0,
    )


    /* Checks if the active network connection is connected to any network */
    fun isConnectedToNetwork(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        return activeNetwork != null
    }


    /* Detects content type (mime type) from given URL string - async using coroutine - use only on separate threat */
    fun detectContentType(urlString: String): ContentType {
        Log.v(tag, "Determining content type - Thread: ${Thread.currentThread().name}")
        val contentType = ContentType(Keys.MIME_TYPE_UNSUPPORTED, Keys.CHARSET_UNDEFINDED)
        val connection: HttpURLConnection? = try {
            openConnection(urlString)
        } catch (e: Exception) {
            Log.w(tag, "Unable to open http connection to $urlString: $e")
            contentType.error = e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            null
        }
        if (connection != null) {
            // the server's own name for its stream and its bitrate, if it sends them
            contentType.icyName = connection.getHeaderField("icy-name")?.trim().orEmpty()
            contentType.icyBitrate = connection.getHeaderField("icy-br")
                ?.substringBefore(",")?.trim()?.toIntOrNull() ?: 0
            val contentTypeHeader: String = connection.contentType ?: String()
            Log.v(tag, "Raw content type header: $contentTypeHeader")
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
                Log.w(tag, "Special case \"application/octet-stream\"")
                val headerFieldContentDisposition: String? =
                    connection.getHeaderField("Content-Disposition")
                if (headerFieldContentDisposition != null) {
                    val fileName: String = headerFieldContentDisposition.split("=")[1].replace(
                        "\"",
                        ""
                    ) //getting value after '=' & stripping any "s
                    contentType.type = FileHelper.getContentTypeFromExtension(fileName)
                } else {
                    Log.i(tag, "Unable to get file name from \"Content-Disposition\" header field.")
                    // the address itself may end in .m3u or .pls
                    contentType.type = FileHelper.getContentTypeFromExtension(connection.url.path)
                }
                // Nothing more specific to go by: keep octet-stream. Many radio servers send it
                // for the stream itself, and the player sorts out the actual format.
                if (contentType.type == Keys.MIME_TYPE_UNSUPPORTED) {
                    contentType.type = Keys.MIME_TYPE_OCTET_STREAM
                }
            }

            connection.disconnect()
        }
        Log.i(tag, "content type: ${contentType.type} | character set: ${contentType.charset}")
        return contentType
    }


    /* Download playlist - up to 100 lines, with max. 200 characters */
    fun downloadPlaylist(playlistUrlString: String): List<String> {
        val lines = mutableListOf<String>()
        // same redirect handling and timeouts as the content type check
        val connection: HttpURLConnection = openConnection(playlistUrlString)
        try {
            connection.inputStream.bufferedReader().useLines { sequence ->
                sequence.take(100).forEach { line ->
                    val trimmedLine = line.take(2000)
                    lines.add(trimmedLine)
                }
            }
        } finally {
            connection.disconnect()
        }
        return lines
    }


    /* Suspend function: Detects content type (mime type) from given URL string - async using coroutine */
    suspend fun detectContentTypeSuspended(urlString: String): ContentType {
        return suspendCancellableCoroutine { cont ->
            cont.resume(detectContentType(urlString))
        }
    }


    /* Suspend function: Gets a random radio-browser.info api address - async using coroutine */
    suspend fun getRadioBrowserServerSuspended(): String {
        return suspendCancellableCoroutine { cont ->
            val serverAddress: String = try {
                // get all available radio browser servers
                val serverAddressList: Array<InetAddress> =
                    InetAddress.getAllByName(Keys.RADIO_BROWSER_API_BASE)
                // select a random address
                serverAddressList[Random().nextInt(serverAddressList.size)].canonicalHostName
            } catch (_: UnknownHostException) {
                Keys.RADIO_BROWSER_API_DEFAULT
            }
            PreferencesHelper.saveRadioBrowserApiAddress(serverAddress)
            cont.resume(serverAddress)
        }
    }


    /*
     * Opens an http connection to the given address and follows redirects - also the ones
     * between http and https, which HttpURLConnection will not follow on its own.
     * Throws when the address cannot be reached or does not answer with 200 OK, with a
     * message that says why.
     */
    private fun openConnection(urlString: String, redirectCount: Int = 0): HttpURLConnection {
        Log.i(tag, "Opening http connection.")
        val connection: HttpURLConnection = URL(urlString).openConnection() as HttpURLConnection
        // without these a server that never answers kept the check running forever
        connection.connectTimeout = CONNECTION_TIMEOUT_MS
        connection.readTimeout = CONNECTION_TIMEOUT_MS
        val status: Int = try {
            connection.responseCode
        } catch (e: Exception) {
            connection.disconnect()
            throw e
        }
        if (status == HttpURLConnection.HTTP_OK) return connection

        val location: String? = connection.getHeaderField("Location")
        val message: String = connection.responseMessage.orEmpty()
        connection.disconnect()
        if (status in REDIRECT_CODES && location != null) {
            if (redirectCount >= MAX_REDIRECTS) throw IOException("Too many redirects")
            // the location may be relative to the address that sent it
            val redirectUrl: String = URL(URL(urlString), location).toString()
            Log.i(tag, "Following redirect to $redirectUrl")
            return openConnection(redirectUrl, redirectCount + 1)
        }
        throw IOException("HTTP $status $message".trim())
    }


    private const val CONNECTION_TIMEOUT_MS = 15_000
    private const val MAX_REDIRECTS = 5
    // 301, 302, 303 and the method-keeping 307 and 308
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)


}
