/*
 * M3uHelper.kt
 * Reads the station details an extended M3U playlist carries per entry
 *
 * Station lists - many of them from the IPTV world - describe each entry like this:
 *
 *   #EXTINF:-1 tvg-name="Radio XY" tvg-logo="https://example.com/xy.png" group-title="Pop, Rock",Radio XY
 *   https://stream.example.com/live.mp3
 *
 * Some radio playlists give the logo in a line of its own instead:
 *
 *   #EXTIMG:https://example.com/xy.png
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.helpers

import java.net.URL


object M3uHelper {

    /* What an #EXTINF line says about an entry - either value may be empty */
    data class ExtInf(val name: String, val logo: String)


    private val ATTRIBUTE = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")


    /* Reads name and logo from an #EXTINF line */
    fun parseExtInf(line: String): ExtInf {
        val content: String = line.substringAfter("#EXTINF:", line)
        // The title follows the first comma that is not inside quotes - an attribute like
        // group-title="Pop, Rock" must not cut the line short.
        var inQuotes = false
        var titleStart: Int = -1
        for ((index, char) in content.withIndex()) {
            if (char == '"') inQuotes = !inQuotes
            else if (char == ',' && !inQuotes) {
                titleStart = index + 1
                break
            }
        }
        val head: String = if (titleStart >= 0) content.substring(0, titleStart - 1) else content
        val title: String = if (titleStart >= 0) content.substring(titleStart).trim() else String()
        val attributes: Map<String, String> = ATTRIBUTE.findAll(head)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
        return ExtInf(
            name = title.ifEmpty { attributes["tvg-name"].orEmpty() },
            logo = attributes["tvg-logo"]?.takeIf { it.isNotEmpty() } ?: attributes["logo"].orEmpty(),
        )
    }


    /* Reads the logo address from an #EXTIMG line */
    fun parseExtImg(line: String): String {
        val value: String = line.substringAfter("#EXTIMG:", String()).trim()
        // some write a description first: "#EXTIMG:front cover,https://..."
        return if (value.contains("://")) value.substring(value.lastIndexOf(',', value.indexOf("://")) + 1).trim()
        else value
    }


    /*
     * Turns an address from a playlist into an absolute http(s) address, resolving it against
     * the playlist's own address when it is relative. Returns an empty string for anything
     * that is not usable, such as a relative address in a playlist read from a local file.
     */
    fun resolveAddress(address: String, playlistAddress: String?): String {
        if (address.isBlank()) return String()
        val resolved: String = try {
            if (playlistAddress.isNullOrBlank()) URL(address).toString()
            else URL(URL(playlistAddress), address).toString()
        } catch (_: Exception) {
            return String()
        }
        return if (resolved.startsWith("http://", true) || resolved.startsWith("https://", true)) resolved else String()
    }


    /* An #EXTINF line for the M3U export, with the logo when the station has one */
    fun createExtInf(name: String, logo: String): String {
        val cleanName: String = name.replace("\n", " ").replace("\r", " ")
        return if (logo.startsWith("http://", true) || logo.startsWith("https://", true)) {
            // a quote would end the attribute early
            "#EXTINF:-1 tvg-logo=\"${logo.replace("\"", "%22")}\",$cleanName"
        } else {
            "#EXTINF:-1,$cleanName"
        }
    }
}
