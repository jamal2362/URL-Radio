/*
 * LogHelper.kt
 * Implements the LogHelper object
 * A LogHelper wraps the logging calls to be able to strip them out of release versions
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
import android.util.Log
import com.jamal2367.urlradio.BuildConfig
import java.util.*


/*
 * LogHelper object
 */
object LogHelper {

    private const val TESTING: Boolean = false // set to "false" for release builds
    private const val LOG_PREFIX: String = "URLRadio_"
    private const val MAX_LOG_tag_LENGTH: Int = 64
    private const val LOG_PREFIX_LENGTH: Int = LOG_PREFIX.length

    fun makeLogTag(str: String): String {
        return if (str.length > MAX_LOG_tag_LENGTH - LOG_PREFIX_LENGTH) {
            LOG_PREFIX + str.substring(0, MAX_LOG_tag_LENGTH - LOG_PREFIX_LENGTH - 1)
        } else LOG_PREFIX + str
    }

    fun makeLogTag(cls: Class<*>): String {
        // don't use this when obfuscating class names
        return makeLogTag(cls.simpleName)
    }

    fun v(vararg messages: Any) {
        // Only log VERBOSE if build type is DEBUG or if TESTING is true
        if (BuildConfig.DEBUG || TESTING) {
            log(Log.VERBOSE, null, *messages)
        }
    }

    fun d(vararg messages: Any) {
        // Only log DEBUG if build type is DEBUG or if TESTING is true
        if (BuildConfig.DEBUG || TESTING) {
            log(Log.DEBUG, null, *messages)
        }
    }

    fun i(vararg messages: Any) {
        log(Log.INFO, null, *messages)
    }

    fun w(vararg messages: Any) {
        log(Log.WARN, null, *messages)
    }

    fun w(t: Throwable, vararg messages: Any) {
        log(Log.WARN, t, *messages)
    }

    fun e(vararg messages: Any) {
        log(Log.ERROR, null, *messages)
    }

    fun e(t: Throwable, vararg messages: Any) {
        log(Log.ERROR, t, *messages)
    }

    fun save(context: Context, vararg messages: Any) {
        save(context, aTAG, null, *messages)
    }

    fun save(context: Context, tag: String, t: Throwable?, vararg messages: Any) {
        if (PreferencesHelper.loadKeepDebugLog()) {
            val sb = StringBuilder()
            sb.append(DateTimeHelper.convertToRfc2822(Calendar.getInstance().time))
            sb.append(" | ")
            sb.append(tag)
            sb.append(" | ")
            for (m in messages) {
                sb.append(m)
            }
            if (t != null) {
                sb.append("\n")
                sb.append(Log.getStackTraceString(t))
            }
            sb.append("\n")
            val message = sb.toString()
            FileHelper.saveLog(context, message)
        }
    }

    private fun log(level: Int, t: Throwable?, vararg messages: Any) {
        val message: String = if (t == null && messages.size == 1) {
            // handle this common case without the extra cost of creating a stringbuffer:
            messages[0].toString()
        } else {
            val sb = StringBuilder()
            for (m in messages) {
                sb.append(m)
            }
            if (t != null) {
                sb.append("\n").append(Log.getStackTraceString(t))
            }
            sb.toString()
        }
        Log.println(level, aTAG, message)

//        if (Log.isLoggable(aTAG, level)) {
//            val message: String
//            if (t == null && messages != null && messages.size == 1) {
//                // handle this common case without the extra cost of creating a stringbuffer:
//                message = messages[0].toString()
//            } else {
//                val sb = StringBuilder()
//                if (messages != null)
//                    for (m in messages) {
//                        sb.append(m)
//                    }
//                if (t != null) {
//                    sb.append("\n").append(Log.getStackTraceString(t))
//                }
//                message = sb.toString()
//            }
//            Log.println(level, aTAG, message)
//        }
    }
}
