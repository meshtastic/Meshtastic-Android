package com.geeksville.mesh.util

import android.widget.EditText
import com.geeksville.mesh.BuildConfig

/**
 * When printing strings to logs sometimes we want to print useful debugging information about users
 * or positions.  But we don't want to leak things like usernames or locations.  So this function
 * if given a string, will return a string which is a maximum of three characters long, taken from the tail
 * of the string.  Which should effectively hide real usernames and locations,
 * but still let us see if values were zero, empty or different.
 */
val Any?.anonymize: String
    get() = this.anonymize()

/**
 * A version of anonymize that allows passing in a custom minimum length
 */
fun Any?.anonymize(maxLen: Int = 3) =
    if (this != null) ("..." + this.toString().takeLast(maxLen)) else "null"

/// A toString that makes sure all newlines are removed (for nice logging).
fun Any.toOneLineString() = this.toString().replace('\n', ' ')

/// Return a one line string version of an object (but if a release build, just say 'might be PII)
fun Any.toPIIString() =
    if (!BuildConfig.DEBUG)
        "<PII?>"
    else
        this.toOneLineString()

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

fun formatAgo(lastSeenUnix: Int): String {
    val currentTime = (System.currentTimeMillis() / 1000).toInt()
    val diffMin = (currentTime - lastSeenUnix) / 60
    if (diffMin < 1)
        return "now"
    if (diffMin < 60)
        return diffMin.toString() + " min"
    if (diffMin < 2880)
        return (diffMin / 60).toString() + " h"
    if (diffMin < 1440000)
        return (diffMin / (60 * 24)).toString() + " d"
    return "?"
}

/// Allows usage like email.onEditorAction(EditorInfo.IME_ACTION_NEXT, { confirm() })
fun EditText.onEditorAction(actionId: Int, func: () -> Unit) {
    setOnEditorActionListener { _, receivedActionId, _ ->

        if (actionId == receivedActionId) {
            func()
        }
        true
    }
}
