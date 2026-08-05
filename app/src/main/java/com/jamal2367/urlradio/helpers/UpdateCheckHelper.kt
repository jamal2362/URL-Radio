/*
 * UpdateCheckHelper.kt
 * Asks GitHub whether a newer release exists
 *
 * Carried over from the check that used to live in PlayerFragment. Two things changed:
 * it runs on OkHttp instead of Volley (the last Volley user in the app), and it is a
 * suspending function, so it is bound to the caller's coroutine scope and cannot outlive
 * the screen the way the old delayed Handler post did.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.helpers

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object UpdateCheckHelper {

    private val tag: String = UpdateCheckHelper::class.java.simpleName

    /**
     * Returns the tag name of the latest release when it differs from [currentVersionName],
     * or null when the app is current or the check could not be completed.
     */
    suspend fun findNewerRelease(releasesApiUrl: String, currentVersionName: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(releasesApiUrl).build()
                OkHttpClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body.string()
                    if (body.isEmpty()) return@withContext null
                    val latest = Gson().fromJson(body, JsonObject::class.java)
                        ?.get("tag_name")?.asString ?: return@withContext null
                    if (latest != currentVersionName) latest else null
                }
            } catch (e: Exception) {
                // A failed update check must never be visible to the user.
                Log.w(tag, "Update check failed", e)
                null
            }
        }
}
