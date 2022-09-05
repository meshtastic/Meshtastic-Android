package com.geeksville.mesh.util

import com.geeksville.mesh.BuildConfig

/// A toString that makes sure all newlines are removed (for nice logging).
fun Any.toOneLineString() = this.toString().replace('\n', ' ')

/// Return a one line string version of an object (but if a release build, just say 'might be PII)
fun Any.toPIIString() =
    if (!BuildConfig.DEBUG)
        "<PII?>"
    else
        this.toOneLineString()

fun formatAgo(lastSeenUnix: Int): String {
    val currentTime = (System.currentTimeMillis() / 1000).toInt()
    val diffMin = (currentTime - lastSeenUnix) / 60;
    if (diffMin < 1)
        return "now";
    if (diffMin < 100)
        return diffMin.toString() + "m"
    if (diffMin < 6000)
        return (diffMin / 60).toString() + "h"
    if (diffMin < 144000)
        return (diffMin / (60 * 24)).toString() + "d";
    return "?";
}
